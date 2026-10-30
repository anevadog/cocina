package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Resultado de una entrada de compra: lote creado, movimiento y nuevo estado del producto. */
public record PurchaseResult(Long lotId, Long movementId, long productId, BigDecimal quantityBase,
        BigDecimal unitCost, BigDecimal currentStock, BigDecimal currentAvgCost, Instant occurredAt) {

    public static PurchaseResult from(StockLot lot, StockMovement movement, Product product) {
        return new PurchaseResult(lot.id(), movement.id(), product.id(), movement.quantityBase(),
                movement.unitCost(), product.currentStock(), product.currentAvgCost(),
                movement.occurredAt());
    }
}
