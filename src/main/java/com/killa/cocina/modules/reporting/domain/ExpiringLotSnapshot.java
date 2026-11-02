package com.killa.cocina.modules.reporting.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Lote con saldo que caduca dentro del horizonte consultado (ECO-06). */
public record ExpiringLotSnapshot(
        long lotId,
        long productId,
        String productName,
        Instant expiresAt,
        BigDecimal remainingQty,
        BigDecimal unitCost) {}
