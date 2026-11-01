package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Proyección de un lote con caducidad próxima junto al nombre de su producto. */
public record ExpiringLotRow(
        Long lotId,
        Long productId,
        String productName,
        Instant expiresAt,
        BigDecimal remainingQty,
        BigDecimal unitCost) {}
