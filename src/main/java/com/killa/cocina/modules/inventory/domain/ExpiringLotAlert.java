package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lote próximo a caducar con su valor en riesgo (INV-07, ECO-06).
 *
 * @param daysRemaining días completos hasta la caducidad; negativo si ya caducó
 * @param value valor del saldo restante al coste del lote
 */
public record ExpiringLotAlert(
        Long lotId,
        Long productId,
        String productName,
        Instant expiresAt,
        long daysRemaining,
        BigDecimal remainingQty,
        BigDecimal unitCost,
        BigDecimal value) {}
