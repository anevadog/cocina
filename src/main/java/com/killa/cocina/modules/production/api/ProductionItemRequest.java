package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionItemRegistration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Línea del plan: versión publicada de la receta y raciones previstas (PRD-01). */
public record ProductionItemRequest(
        @Min(value = 1, message = "debe ser al menos 1") long recipeVersionId,

        @Min(value = 1, message = "debe ser al menos 1") @Max(value = 10000, message = "no puede superar 10000")
        int plannedRations) {

    public ProductionItemRegistration toDomain() {
        return new ProductionItemRegistration(recipeVersionId, plannedRations);
    }
}
