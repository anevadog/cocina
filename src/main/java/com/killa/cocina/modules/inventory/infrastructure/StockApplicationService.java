package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.config.InventoryProperties;
import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.inventory.domain.AdjustmentRegistration;
import com.killa.cocina.modules.inventory.domain.ExpiringLotAlert;
import com.killa.cocina.modules.inventory.domain.InventoryAlerts;
import com.killa.cocina.modules.inventory.domain.LowStockAlert;
import com.killa.cocina.modules.inventory.domain.MovementType;
import com.killa.cocina.modules.inventory.domain.Product;
import com.killa.cocina.modules.inventory.domain.ProductRepository;
import com.killa.cocina.modules.inventory.domain.PurchaseRegistration;
import com.killa.cocina.modules.inventory.domain.PurchaseResult;
import com.killa.cocina.modules.inventory.domain.Quantities;
import com.killa.cocina.modules.inventory.domain.StockChangeResult;
import com.killa.cocina.modules.inventory.domain.StockLot;
import com.killa.cocina.modules.inventory.domain.StockLotRepository;
import com.killa.cocina.modules.inventory.domain.StockMovement;
import com.killa.cocina.modules.inventory.domain.StockMovementRepository;
import com.killa.cocina.modules.inventory.domain.StockService;
import com.killa.cocina.modules.inventory.domain.UnitConversion;
import com.killa.cocina.modules.inventory.domain.Valuation;
import com.killa.cocina.modules.suppliers.domain.Supplier;
import com.killa.cocina.modules.suppliers.domain.SupplierRepository;
import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.pagination.PageResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Casos de uso de stock (INV-04..09, CAL-01, ECO-06).
 *
 * <p>Toda operación bloquea la fila del producto, calcula el saldo y escribe movimiento, lote y
 * auditoría en la misma transacción (ADR-022). El PMP solo cambia en las compras (ADR-027/028).</p>
 */
@Service
@Transactional
public class StockApplicationService implements StockService {

    private static final Logger log = LoggerFactory.getLogger(StockApplicationService.class);

    private static final int MAX_PAGE_SIZE = 100;

    /** Límite de cada lista de alertas: la respuesta queda acotada y el total dice cuántos hay. */
    private static final int ALERT_LIMIT = 100;

    /** Referencia del kardex hacia el batch de producción que originó el consumo. */
    private static final String PRODUCTION_REFERENCE = "production_batch";

    private final ProductRepository products;

    private final StockLotRepository lots;

    private final StockMovementRepository movements;

    private final SupplierRepository suppliers;

    private final AuditRecorder audit;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    private final InventoryProperties inventory;

    public StockApplicationService(
            ProductRepository products,
            StockLotRepository lots,
            StockMovementRepository movements,
            SupplierRepository suppliers,
            AuditRecorder audit,
            ObjectMapper objectMapper,
            Clock clock,
            InventoryProperties inventory) {
        this.products = products;
        this.lots = lots;
        this.movements = movements;
        this.suppliers = suppliers;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.inventory = inventory;
    }

    @Override
    public PurchaseResult purchase(
            long establishmentId, Long actorId, long productId, PurchaseRegistration registration) {
        long actor = requireActor(actorId);
        Instant now = Instant.now(clock);
        Product product = requireActiveForUpdate(establishmentId, productId);
        requireActiveSupplier(establishmentId, registration.supplierId());
        PurchaseRegistration.Schedule schedule = registration.schedule(now);

        List<UnitConversion> conversions = products.conversions(productId);
        BigDecimal factor = Quantities.factorToBase(product, conversions, registration.unit());
        BigDecimal quantityBase = Quantities.toBase(product, conversions, registration.quantity(), registration.unit());
        BigDecimal unitCost = Quantities.unitCost(registration.unitPrice(), factor);

        StockLot lot = lots.save(StockLot.create(
                establishmentId,
                productId,
                registration.supplierId(),
                schedule.receivedAt(),
                schedule.expiresAt(),
                unitCost,
                quantityBase,
                now));
        BigDecimal newStock = Quantities.stock(product.currentStock().add(quantityBase));
        BigDecimal newAvgCost = Valuation.averageCost(product, quantityBase, unitCost);
        Product updated = products.save(product.withStock(newStock, newAvgCost, now));
        StockMovement movement = movements.save(new StockMovement(
                null,
                establishmentId,
                productId,
                lot.id(),
                MovementType.PURCHASE_IN,
                quantityBase,
                unitCost,
                newStock,
                null,
                null,
                null,
                actor,
                schedule.receivedAt()));

        audit.record(
                establishmentId,
                actor,
                AuditAction.STOCK_PURCHASE_RECORDED,
                "stock_movement",
                movement.id(),
                stockSnapshot(product),
                stockSnapshot(updated));
        if (product.currentAvgCost().compareTo(updated.currentAvgCost()) != 0) {
            audit.record(
                    establishmentId,
                    actor,
                    AuditAction.STOCK_COST_CHANGED,
                    "product",
                    productId,
                    costSnapshot(product),
                    costSnapshot(updated));
        }
        return PurchaseResult.from(lot, movement, updated);
    }

    @Override
    public StockChangeResult adjust(
            long establishmentId, Long actorId, long productId, AdjustmentRegistration registration) {
        long actor = requireActor(actorId);
        Instant now = Instant.now(clock);
        Product product = requireActiveForUpdate(establishmentId, productId);
        BigDecimal quantityBase = Quantities.toBase(
                product, products.conversions(productId), registration.quantity(), registration.unit());
        BigDecimal unitCost = product.currentAvgCost();

        BigDecimal newStock;
        Long lotId = null;
        if (registration.type().isInbound()) {
            newStock = Quantities.stock(product.currentStock().add(quantityBase));
            StockLot lot = lots.save(
                    StockLot.create(establishmentId, productId, null, now, null, unitCost, quantityBase, now));
            lotId = lot.id();
        } else {
            newStock = Quantities.stock(product.currentStock().subtract(quantityBase));
            if (newStock.signum() < 0 && !product.allowNegative()) {
                throw new ApiException(
                        ErrorCode.INSUFFICIENT_STOCK,
                        "El ajuste dejaría un saldo negativo en '" + product.name()
                                + "'. El producto no admite stock negativo.");
            }
            consumeLotsFifo(establishmentId, productId, quantityBase);
        }

        Product updated = products.save(product.withStock(newStock, unitCost, now));
        StockMovement movement = movements.save(new StockMovement(
                null,
                establishmentId,
                productId,
                lotId,
                registration.type(),
                quantityBase,
                unitCost,
                newStock,
                registration.reason(),
                null,
                null,
                actor,
                now));

        audit.record(
                establishmentId,
                actor,
                AuditAction.STOCK_ADJUSTMENT_RECORDED,
                "stock_movement",
                movement.id(),
                stockSnapshot(product),
                stockSnapshot(updated));
        return StockChangeResult.from(movement, updated);
    }

    @Override
    public void consumeForProduction(
            long establishmentId, Long actorId, long batchId, Map<Long, BigDecimal> requirements, Instant occurredAt) {
        long actor = requireActor(actorId);
        if (occurredAt == null) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "La petición contiene datos no válidos.",
                    List.of(new ApiError.FieldViolation("occurredAt", "es obligatorio")));
        }
        if (requirements == null || requirements.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> ordered = new TreeMap<>();
        for (Map.Entry<Long, BigDecimal> line : requirements.entrySet()) {
            if (line.getKey() == null
                    || line.getValue() == null
                    || line.getValue().signum() < 0) {
                throw new ApiException(
                        ErrorCode.VALIDATION_ERROR,
                        "La petición contiene datos no válidos.",
                        List.of(new ApiError.FieldViolation("requirements", "las cantidades deben ser positivas")));
            }
            BigDecimal quantity = Quantities.stock(line.getValue());
            if (quantity.signum() > 0) {
                ordered.put(line.getKey(), quantity);
            }
        }
        if (ordered.isEmpty()) {
            return;
        }

        // Primero se bloquean todos los productos en orden ascendente; después se consume. Así dos
        // producciones con ingredientes cruzados no pueden interbloquearse (ADR-022, ADR-035).
        Map<Long, Product> locked = new LinkedHashMap<>();
        for (Long productId : ordered.keySet()) {
            locked.put(productId, requireActiveForUpdate(establishmentId, productId));
        }
        for (Map.Entry<Long, Product> entry : locked.entrySet()) {
            consumeProductForProduction(
                    establishmentId, batchId, actor, entry.getValue(), ordered.get(entry.getKey()), occurredAt);
        }
    }

    /**
     * Consume un producto por lotes vigentes FIFO y escribe un movimiento por lote.
     *
     * <p>El saldo del producto se recalcula con cada lote para que la cadena de {@code balance_after}
     * sea reconstruible. Si el producto admite negativo y faltan lotes, el resto va en un movimiento
     * sin lote, igual que un ajuste con datos cargados fuera del API.</p>
     */
    private void consumeProductForProduction(
            long establishmentId, long batchId, long actor, Product product, BigDecimal quantity, Instant occurredAt) {
        List<StockLot> usable = lots.findUsable(establishmentId, product.id(), occurredAt);
        BigDecimal available = usable.stream().map(StockLot::remainingQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(quantity) < 0 && !product.allowNegative()) {
            throw new ApiException(
                    ErrorCode.INSUFFICIENT_STOCK,
                    "No hay stock vigente suficiente de '" + product.name() + "' para la producción: se necesitan "
                            + quantity.toPlainString() + " "
                            + product.baseUnit().label() + " y hay "
                            + available.toPlainString()
                            + ". Los lotes caducados no se consumen.");
        }

        BigDecimal remaining = quantity;
        BigDecimal balance = product.currentStock();
        for (StockLot lot : usable) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal taken = remaining.min(lot.remainingQty());
            lots.save(lot.consume(taken));
            balance = Quantities.stock(balance.subtract(taken));
            movements.save(productionMovement(
                    establishmentId,
                    product.id(),
                    lot.id(),
                    taken,
                    lot.unitCost(),
                    balance,
                    batchId,
                    actor,
                    occurredAt));
            remaining = remaining.subtract(taken);
        }
        if (remaining.signum() > 0) {
            log.warn(
                    "Producción sin lotes vigentes suficientes [establishmentId={}, productId={}, "
                            + "batchId={}, cantidad sin lote={}]",
                    establishmentId,
                    product.id(),
                    batchId,
                    remaining.toPlainString());
            balance = Quantities.stock(balance.subtract(remaining));
            movements.save(productionMovement(
                    establishmentId,
                    product.id(),
                    null,
                    remaining,
                    product.currentAvgCost(),
                    balance,
                    batchId,
                    actor,
                    occurredAt));
        }
        products.save(product.withStock(balance, product.currentAvgCost(), Instant.now(clock)));
    }

    private static StockMovement productionMovement(
            long establishmentId,
            long productId,
            Long lotId,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal balanceAfter,
            long batchId,
            long actor,
            Instant occurredAt) {
        return new StockMovement(
                null,
                establishmentId,
                productId,
                lotId,
                MovementType.PRODUCTION_OUT,
                quantity,
                unitCost,
                balanceAfter,
                null,
                PRODUCTION_REFERENCE,
                batchId,
                actor,
                occurredAt);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockMovement> movements(long establishmentId, long productId, int page, int size) {
        requireProduct(establishmentId, productId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return new PageResult<>(
                movements.findByProduct(establishmentId, productId, safePage, safeSize),
                movements.countByProduct(establishmentId, productId),
                safePage,
                safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockLot> lots(long establishmentId, long productId, int page, int size) {
        requireProduct(establishmentId, productId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return new PageResult<>(
                lots.findByProduct(establishmentId, productId, safePage, safeSize),
                lots.countByProduct(establishmentId, productId),
                safePage,
                safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryAlerts alerts(long establishmentId) {
        Instant now = Instant.now(clock);
        Instant until = now.plus(Duration.ofDays(inventory.expiryWarningDays()));

        List<LowStockAlert> lowStock = products.lowStock(establishmentId, ALERT_LIMIT).stream()
                .map(product ->
                        new LowStockAlert(product.id(), product.name(), product.currentStock(), product.minStock()))
                .toList();
        List<ExpiringLotAlert> expiring = lots.findExpiring(establishmentId, until, ALERT_LIMIT).stream()
                .map(row -> new ExpiringLotAlert(
                        row.lotId(),
                        row.productId(),
                        row.productName(),
                        row.expiresAt(),
                        ChronoUnit.DAYS.between(now, row.expiresAt()),
                        row.remainingQty(),
                        row.unitCost(),
                        Quantities.cost(row.remainingQty().multiply(row.unitCost()))))
                .toList();
        return new InventoryAlerts(
                lowStock,
                products.countLowStock(establishmentId),
                expiring,
                lots.countExpiring(establishmentId, until));
    }

    /**
     * Consume lotes por caducidad y antigüedad (D15). Un ajuste es una corrección de inventario, no
     * un consumo de producción: puede tocar lotes caducados.
     */
    private void consumeLotsFifo(long establishmentId, long productId, BigDecimal quantity) {
        BigDecimal remaining = quantity;
        for (StockLot lot : lots.findConsumable(establishmentId, productId)) {
            if (remaining.signum() <= 0) {
                return;
            }
            BigDecimal taken = remaining.min(lot.remainingQty());
            lots.save(lot.consume(taken));
            remaining = remaining.subtract(taken);
        }
        if (remaining.signum() > 0) {
            // Solo puede ocurrir con datos cargados fuera del API: current_stock manda sobre el
            // desglose de lotes. Se deja rastro para poder corregir el inventario físico.
            log.warn(
                    "Ajuste de salida sin lotes suficientes [establishmentId={}, productId={}, "
                            + "cantidad sin lote={}]",
                    establishmentId,
                    productId,
                    remaining.toPlainString());
        }
    }

    private Product requireActiveForUpdate(long establishmentId, long productId) {
        Product product = products.findForUpdate(establishmentId, productId).orElseThrow(() -> notFound(productId));
        if (!product.active()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "La petición contiene datos no válidos.",
                    List.of(new ApiError.FieldViolation(
                            "productId", "el producto está archivado y no admite movimientos")));
        }
        return product;
    }

    private Product requireProduct(long establishmentId, long productId) {
        return products.findById(establishmentId, productId).orElseThrow(() -> notFound(productId));
    }

    private void requireActiveSupplier(long establishmentId, long supplierId) {
        Supplier supplier = suppliers
                .findById(establishmentId, supplierId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.VALIDATION_ERROR,
                        "La petición contiene datos no válidos.",
                        List.of(new ApiError.FieldViolation(
                                "supplierId", "el proveedor " + supplierId + " no existe"))));
        if (!supplier.active()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "La petición contiene datos no válidos.",
                    List.of(new ApiError.FieldViolation(
                            "supplierId", "el proveedor '" + supplier.name() + "' está desactivado")));
        }
    }

    private static long requireActor(Long actorId) {
        if (actorId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Las operaciones de stock exigen un usuario autenticado.");
        }
        return actorId;
    }

    private static ApiException notFound(long productId) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "El producto " + productId + " no existe.");
    }

    private String stockSnapshot(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", product.name());
        values.put("currentStock", product.currentStock().toPlainString());
        values.put("currentAvgCost", product.currentAvgCost().toPlainString());
        values.put("minStock", product.minStock().toPlainString());
        values.put("allowNegative", product.allowNegative());
        return objectMapper.writeValueAsString(values);
    }

    private String costSnapshot(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", product.name());
        values.put("currentAvgCost", product.currentAvgCost().toPlainString());
        return objectMapper.writeValueAsString(values);
    }
}
