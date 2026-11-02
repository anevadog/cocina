package com.killa.cocina.modules.quality.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Trazabilidad de un lote: sus datos y las preparaciones que lo consumieron (CAL-01).
 *
 * <p>El lote se identifica aunque ya no tenga saldo; el histórico del kardex explica qué se hizo con
 * él. Un lote sin consumos devuelve la lista vacía, no un error.</p>
 */
public record LotTraceabilityView(
        long lotId,
        long productId,
        String productName,
        String unit,
        Instant receivedAt,
        Instant expiresAt,
        BigDecimal initialQty,
        BigDecimal remainingQty,
        List<ProductionConsumptionTrace> consumptions) {

    public LotTraceabilityView {
        consumptions = consumptions == null ? List.of() : List.copyOf(consumptions);
    }
}
