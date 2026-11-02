package com.killa.cocina.modules.production.domain;

import java.util.List;

/** Necesidades agregadas del plan, en unidad base y por producto (PRD-02). */
public record ProductionRequirements(long planId, List<RequirementLine> lines) {

    public ProductionRequirements {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
