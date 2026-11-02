package com.killa.cocina.modules.production.domain;

import java.math.BigDecimal;

/** Ingrediente de una versión publicada: cantidad por ración en unidad base y merma (REC-02). */
public record ProductionCompositionItem(long productId, BigDecimal quantityBase, BigDecimal wastePct) {}
