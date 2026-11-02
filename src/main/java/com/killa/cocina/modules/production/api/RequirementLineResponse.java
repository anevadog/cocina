package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.RequirementLine;
import java.math.BigDecimal;

/** Necesidad de un producto para todo el plan, en su unidad base (PRD-02). */
public record RequirementLineResponse(long productId, String productName, String unit, BigDecimal quantity) {

    public static RequirementLineResponse from(RequirementLine line) {
        return new RequirementLineResponse(line.productId(), line.productName(), line.unit(), line.quantity());
    }
}
