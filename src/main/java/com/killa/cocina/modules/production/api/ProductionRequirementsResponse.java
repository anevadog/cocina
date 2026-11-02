package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionRequirements;
import java.util.List;

/** Necesidades agregadas del plan por producto (PRD-02). */
public record ProductionRequirementsResponse(long planId, List<RequirementLineResponse> lines) {

    public static ProductionRequirementsResponse from(ProductionRequirements requirements) {
        return new ProductionRequirementsResponse(
                requirements.planId(),
                requirements.lines().stream().map(RequirementLineResponse::from).toList());
    }
}
