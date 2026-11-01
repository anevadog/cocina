package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.MovementType;
import com.killa.cocina.modules.inventory.domain.StockChangeResult;
import java.math.BigDecimal;
import java.time.Instant;

/** Resultado de un ajuste de stock. */
public record AdjustmentResponse(
        Long movementId,
        long productId,
        MovementType type,
        BigDecimal quantityBase,
        BigDecimal unitCost,
        BigDecimal currentStock,
        BigDecimal currentAvgCost,
        String reason,
        Instant occurredAt) {

    public static AdjustmentResponse from(StockChangeResult result) {
        return new AdjustmentResponse(
                result.movementId(),
                result.productId(),
                result.type(),
                result.quantityBase(),
                result.unitCost(),
                result.currentStock(),
                result.currentAvgCost(),
                result.reason(),
                result.occurredAt());
    }
}
