package com.killa.cocina.modules.reporting.api;

import com.killa.cocina.modules.reporting.domain.ExpiryRiskLine;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lote próximo a caducar con su valor en riesgo (ECO-06).
 *
 * @param daysRemaining días completos hasta la caducidad; negativo si ya caducó
 * @param value valor del saldo restante al coste del lote
 */
public record ExpiryRiskLineResponse(
        Long lotId,
        Long productId,
        String productName,
        Instant expiresAt,
        long daysRemaining,
        BigDecimal remainingQty,
        BigDecimal unitCost,
        BigDecimal value) {

    public static ExpiryRiskLineResponse from(ExpiryRiskLine line) {
        return new ExpiryRiskLineResponse(
                line.lotId(),
                line.productId(),
                line.productName(),
                line.expiresAt(),
                line.daysRemaining(),
                line.remainingQty(),
                line.unitCost(),
                line.value());
    }
}
