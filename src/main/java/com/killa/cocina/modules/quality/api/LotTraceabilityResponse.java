package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.LotTraceabilityView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Trazabilidad de un lote y de las preparaciones que lo consumieron (CAL-01). */
public record LotTraceabilityResponse(
        long lotId,
        long productId,
        String productName,
        String unit,
        Instant receivedAt,
        Instant expiresAt,
        BigDecimal initialQty,
        BigDecimal remainingQty,
        List<ProductionConsumptionTraceResponse> consumptions) {

    public static LotTraceabilityResponse from(LotTraceabilityView view) {
        return new LotTraceabilityResponse(
                view.lotId(),
                view.productId(),
                view.productName(),
                view.unit(),
                view.receivedAt(),
                view.expiresAt(),
                view.initialQty(),
                view.remainingQty(),
                view.consumptions().stream()
                        .map(ProductionConsumptionTraceResponse::from)
                        .toList());
    }
}
