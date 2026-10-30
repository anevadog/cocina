package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Resultado de un ajuste: movimiento registrado y nuevo estado del producto. */
public record StockChangeResult(Long movementId, long productId, MovementType type,
        BigDecimal quantityBase, BigDecimal unitCost, BigDecimal currentStock,
        BigDecimal currentAvgCost, String reason, Instant occurredAt) {

    public static StockChangeResult from(StockMovement movement, Product product) {
        return new StockChangeResult(movement.id(), product.id(), movement.type(),
                movement.quantityBase(), movement.unitCost(), product.currentStock(),
                product.currentAvgCost(), movement.reason(), movement.occurredAt());
    }
}
