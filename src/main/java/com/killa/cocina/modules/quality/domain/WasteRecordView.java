package com.killa.cocina.modules.quality.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Merma expuesta por la API (CAL-03). */
public record WasteRecordView(
        Long id,
        long productId,
        Long lotId,
        BigDecimal quantityBase,
        BigDecimal unitCost,
        BigDecimal cost,
        String reason,
        long actorId,
        Instant occurredAt) {

    public static WasteRecordView from(WasteRecord record) {
        return new WasteRecordView(
                record.id(),
                record.productId(),
                record.lotId(),
                record.quantityBase(),
                record.unitCost(),
                record.cost(),
                record.reason(),
                record.actorId(),
                record.occurredAt());
    }
}
