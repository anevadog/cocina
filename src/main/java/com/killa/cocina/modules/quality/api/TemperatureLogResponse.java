package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.TemperatureKind;
import com.killa.cocina.modules.quality.domain.TemperatureLogView;
import java.math.BigDecimal;
import java.time.Instant;

/** Lectura de temperatura expuesta por la API (CAL-02). */
public record TemperatureLogResponse(
        Long id,
        TemperatureKind kind,
        String equipment,
        BigDecimal valueC,
        BigDecimal minC,
        BigDecimal maxC,
        boolean withinRange,
        long actorId,
        Instant recordedAt) {

    public static TemperatureLogResponse from(TemperatureLogView view) {
        return new TemperatureLogResponse(
                view.id(),
                view.kind(),
                view.equipment(),
                view.valueC(),
                view.minC(),
                view.maxC(),
                view.withinRange(),
                view.actorId(),
                view.recordedAt());
    }
}
