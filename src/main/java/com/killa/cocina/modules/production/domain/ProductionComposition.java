package com.killa.cocina.modules.production.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Composición de una versión publicada, tal y como la necesita producción.
 *
 * <p>Se define en el dominio de producción para no depender de las entidades de recetas: el adaptador
 * traduce {@code RecipeVersion} y sus ítems a este contrato (ADR-037).</p>
 */
public record ProductionComposition(
        long recipeId,
        String recipeName,
        boolean recipeActive,
        long versionId,
        int versionNumber,
        BigDecimal costPerRation,
        List<ProductionCompositionItem> items) {

    public ProductionComposition {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
