package com.killa.cocina.modules.quality.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Lectura de temperatura expuesta por la API (CAL-02). */
public record TemperatureLogView(
        Long id,
        TemperatureKind kind,
        String equipment,
        BigDecimal valueC,
        BigDecimal minC,
        BigDecimal maxC,
        boolean withinRange,
        long actorId,
        Instant recordedAt) {

    public static TemperatureLogView from(TemperatureLog log) {
        return new TemperatureLogView(
                log.id(),
                log.kind(),
                log.equipment(),
                log.valueC(),
                log.minC(),
                log.maxC(),
                log.withinRange(),
                log.actorId(),
                log.recordedAt());
    }
}
