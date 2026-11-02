package com.killa.cocina.modules.reporting.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Coste congelado de una versión publicada (ECO-01).
 *
 * <p>El catálogo solo devuelve versiones publicadas: un borrador aún no tiene coste y no forma parte
 * del histórico.</p>
 */
public record RecipeCostSnapshot(
        long recipeId,
        String recipeName,
        long versionId,
        int versionNumber,
        int baseRations,
        BigDecimal totalCost,
        BigDecimal costPerRation,
        Instant publishedAt) {}
