package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.WasteRecordView;
import java.math.BigDecimal;
import java.time.Instant;

/** Merma expuesta por la API (CAL-03). */
public record WasteRecordResponse(
        Long id,
        long productId,
        Long lotId,
        BigDecimal quantityBase,
        BigDecimal unitCost,
        BigDecimal cost,
        String reason,
        long actorId,
        Instant occurredAt) {

    public static WasteRecordResponse from(WasteRecordView view) {
        return new WasteRecordResponse(
                view.id(),
                view.productId(),
                view.lotId(),
                view.quantityBase(),
                view.unitCost(),
                view.cost(),
                view.reason(),
                view.actorId(),
                view.occurredAt());
    }
}
