package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.PurchaseResult;
import java.math.BigDecimal;
import java.time.Instant;

/** Resultado de una compra: lote, movimiento y estado resultante del producto. */
public record PurchaseResponse(
        Long lotId,
        Long movementId,
        long productId,
        BigDecimal quantityBase,
        BigDecimal unitCost,
        BigDecimal currentStock,
        BigDecimal currentAvgCost,
        Instant occurredAt) {

    public static PurchaseResponse from(PurchaseResult result) {
        return new PurchaseResponse(
                result.lotId(),
                result.movementId(),
                result.productId(),
                result.quantityBase(),
                result.unitCost(),
                result.currentStock(),
                result.currentAvgCost(),
                result.occurredAt());
    }
}
