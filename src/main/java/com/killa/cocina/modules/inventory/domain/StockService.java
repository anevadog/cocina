package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.pagination.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Casos de uso de stock: compras, ajustes, kardex, lotes y alertas (INV-04..09, ECO-06).
 *
 * <p>La implementación transaccional vive en {@code infrastructure} (ADR-020).</p>
 */
public interface StockService {

    /** Entrada de compra: crea lote, recalcula PMP y escribe el movimiento y la auditoría. */
    PurchaseResult purchase(long establishmentId, Long actorId, long productId, PurchaseRegistration registration);

    /** Ajuste manual con motivo; puede crear lote (entrada) o consumir lotes FIFO (salida). */
    StockChangeResult adjust(long establishmentId, Long actorId, long productId, AdjustmentRegistration registration);

    /**
     * Descuento atómico por producción (PRD-03, REC-06, CAL-04).
     *
     * <p>Bloquea los productos implicados en orden ascendente de {@code productId}, consume lotes
     * vigentes FIFO y escribe un movimiento {@code PRODUCTION_OUT} por lote. Los lotes caducados no
     * se consumen; si no hay stock vigente suficiente y el producto no admite negativo, responde 409
     * sin dejar ningún cambio. Una sola llamada agrupa todos los productos del batch para que el
     * fallo de uno revierta también los demás (NFR-INT-01).</p>
     *
     * @param requirements cantidad necesaria por producto, en unidad base
     */
    void consumeForProduction(
            long establishmentId, Long actorId, long batchId, Map<Long, BigDecimal> requirements, Instant occurredAt);

    /**
     * Descuento atómico por merma (CAL-03, CAL-04).
     *
     * <p>Consume el lote indicado o, si no se indica, los lotes con saldo en orden FIFO. A
     * diferencia de producción, <strong>sí puede retirar lotes caducados</strong>: la merma es la vía
     * para sacarlos del inventario. La cabecera se valora al PMP vigente; cada movimiento
     * {@code WASTE_OUT} conserva el coste de su lote.</p>
     *
     * @param quantityBase cantidad en unidad base del producto
     * @param wasteRecordId cabecera de merma a la que referencian los movimientos
     */
    WasteConsumption consumeForWaste(
            long establishmentId,
            Long actorId,
            long productId,
            Long lotId,
            BigDecimal quantityBase,
            long wasteRecordId,
            String reason,
            Instant occurredAt);

    /**
     * Consumo real de producción por producto para unos batches (ECO-03).
     *
     * <p>Suma los movimientos {@code PRODUCTION_OUT} referenciados por los batches; el resultado está
     * en unidad base y no incluye ajustes manuales.</p>
     */
    Map<Long, BigDecimal> productionConsumption(long establishmentId, Collection<Long> batchIds);

    /**
     * Ajustes netos de salida por producto en {@code [fromInclusive, toExclusive)} (ECO-03).
     *
     * <p>Las salidas suman y las entradas restan: un valor positivo indica consumo neto por ajuste.
     * Solo considera {@code ADJUSTMENT_IN} y {@code ADJUSTMENT_OUT}, no compras ni producción.</p>
     */
    Map<Long, BigDecimal> netAdjustmentOutflow(long establishmentId, Instant fromInclusive, Instant toExclusive);

    PageResult<StockMovement> movements(long establishmentId, long productId, int page, int size);

    PageResult<StockLot> lots(long establishmentId, long productId, int page, int size);

    InventoryAlerts alerts(long establishmentId);
}
