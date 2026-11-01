package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.MovementType;
import com.killa.cocina.modules.inventory.domain.StockMovement;
import java.math.BigDecimal;
import java.time.Instant;

/** Fila del kardex expuesta por la API (INV-06). */
public record StockMovementResponse(
        Long id,
        MovementType type,
        BigDecimal quantityBase,
        BigDecimal unitCost,
        BigDecimal balanceAfter,
        String reason,
        Long lotId,
        long actorId,
        Instant occurredAt) {

    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(
                movement.id(),
                movement.type(),
                movement.quantityBase(),
                movement.unitCost(),
                movement.balanceAfter(),
                movement.reason(),
                movement.lotId(),
                movement.actorId(),
                movement.occurredAt());
    }
}
