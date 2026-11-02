package com.killa.cocina.modules.production.domain;

/**
 * Ítem del plan con la información de la receta y de su batch, si ya empezó.
 *
 * @param status estado del batch; {@code PENDING} mientras no se haya empezado
 * @param batch ejecución real; {@code null} si aún no se ha empezado
 */
public record ProductionItemView(
        long id,
        Long recipeId,
        String recipeName,
        long versionId,
        int versionNumber,
        int plannedRations,
        ProductionStatus status,
        ProductionBatchView batch) {}
