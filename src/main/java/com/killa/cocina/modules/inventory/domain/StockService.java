package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.pagination.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
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

    PageResult<StockMovement> movements(long establishmentId, long productId, int page, int size);

    PageResult<StockLot> lots(long establishmentId, long productId, int page, int size);

    InventoryAlerts alerts(long establishmentId);
}
