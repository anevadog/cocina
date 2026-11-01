package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.ExpiringLotAlert;
import java.math.BigDecimal;
import java.time.Instant;

/** Lote próximo a caducar con el valor en riesgo (INV-07, ECO-06). */
public record ExpiringLotAlertResponse(
        Long lotId,
        Long productId,
        String productName,
        Instant expiresAt,
        long daysRemaining,
        BigDecimal remainingQty,
        BigDecimal unitCost,
        BigDecimal value) {

    public static ExpiringLotAlertResponse from(ExpiringLotAlert alert) {
        return new ExpiringLotAlertResponse(
                alert.lotId(),
                alert.productId(),
                alert.productName(),
                alert.expiresAt(),
                alert.daysRemaining(),
                alert.remainingQty(),
                alert.unitCost(),
                alert.value());
    }
}
