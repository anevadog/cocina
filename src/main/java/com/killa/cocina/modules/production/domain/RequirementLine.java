package com.killa.cocina.modules.production.domain;

import java.math.BigDecimal;

/** Cantidad necesaria de un producto para todo el plan (PRD-02). */
public record RequirementLine(long productId, String productName, String unit, BigDecimal quantity) {}
