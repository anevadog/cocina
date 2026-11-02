package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionItemView;
import com.killa.cocina.modules.production.domain.ProductionStatus;

/**
 * Ítem de un plan con la receta y su ejecución (PRD-01..05).
 *
 * @param status estado del batch; {@code PENDING} si aún no empezó
 * @param batch ejecución real; {@code null} si aún no empezó
 */
public record ProductionItemResponse(
        long id,
        Long recipeId,
        String recipeName,
        long versionId,
        int versionNumber,
        int plannedRations,
        ProductionStatus status,
        ProductionBatchResponse batch) {

    public static ProductionItemResponse from(ProductionItemView view) {
        return new ProductionItemResponse(
                view.id(),
                view.recipeId(),
                view.recipeName(),
                view.versionId(),
                view.versionNumber(),
                view.plannedRations(),
                view.status(),
                view.batch() == null ? null : ProductionBatchResponse.from(view.batch()));
    }
}
