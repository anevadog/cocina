package com.killa.cocina.modules.quality.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Consumo de un lote por una preparación concreta (CAL-01).
 *
 * <p>Se construye a partir de un movimiento {@code PRODUCTION_OUT} del kardex; la preparación se
 * resuelve a través de sus puertos publicados, sin tocar entidades JPA de producción (ADR-040).</p>
 */
public record ProductionConsumptionTrace(
        long batchId,
        long planId,
        LocalDate serviceDate,
        String serviceType,
        Long recipeId,
        String recipeName,
        int recipeVersionNumber,
        BigDecimal quantityBase,
        Instant occurredAt) {}
