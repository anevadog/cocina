package com.killa.cocina.modules.production.domain;

/** Ítem tal y como llega en la petición de un plan (PRD-01). */
public record ProductionItemRegistration(long recipeVersionId, int plannedRations) {

    public ProductionItemRegistration {
        if (recipeVersionId <= 0) {
            throw ProductionLimits.invalid("recipeVersionId", "es obligatorio");
        }
        plannedRations = ProductionLimits.rations(plannedRations, "plannedRations");
    }
}
