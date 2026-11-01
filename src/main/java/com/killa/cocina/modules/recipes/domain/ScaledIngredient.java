package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;

/**
 * Ingrediente expandido para un número de raciones (REC-03).
 *
 * @param quantityBase cantidad ya expandida con merma, en unidad base
 * @param unitCost coste vigente por unidad base; {@code null} si el producto no tiene coste
 * @param cost coste de la línea para esas raciones; {@code null} si falta {@code unitCost}
 */
public record ScaledIngredient(
        long productId,
        String productName,
        String unit,
        BigDecimal quantityBase,
        BigDecimal wastePct,
        BigDecimal unitCost,
        BigDecimal cost) {}
