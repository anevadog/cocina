package com.killa.cocina.modules.reporting.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lote en riesgo de caducidad dentro del informe (ECO-06).
 *
 * @param daysRemaining días completos hasta la caducidad; negativo si ya caducó
 * @param value valor del saldo restante al coste del lote
 */
public record ExpiryRiskLine(
        long lotId,
        long productId,
        String productName,
        Instant expiresAt,
        long daysRemaining,
        BigDecimal remainingQty,
        BigDecimal unitCost,
        BigDecimal value) {}
