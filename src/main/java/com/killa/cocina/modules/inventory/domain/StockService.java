package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.pagination.PageResult;

/**
 * Casos de uso de stock: compras, ajustes, kardex, lotes y alertas (INV-04..09, ECO-06).
 *
 * <p>La implementación transaccional vive en {@code infrastructure} (ADR-020).</p>
 */
public interface StockService {

    /** Entrada de compra: crea lote, recalcula PMP y escribe el movimiento y la auditoría. */
    PurchaseResult purchase(long establishmentId, Long actorId, long productId,
            PurchaseRegistration registration);

    /** Ajuste manual con motivo; puede crear lote (entrada) o consumir lotes FIFO (salida). */
    StockChangeResult adjust(long establishmentId, Long actorId, long productId,
            AdjustmentRegistration registration);

    PageResult<StockMovement> movements(long establishmentId, long productId, int page, int size);

    PageResult<StockLot> lots(long establishmentId, long productId, int page, int size);

    InventoryAlerts alerts(long establishmentId);
}
