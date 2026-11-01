package com.killa.cocina.modules.recipes.api;

import com.killa.cocina.modules.recipes.domain.ScaledIngredient;
import java.math.BigDecimal;

/**
 * Ingrediente expandido para un número de raciones (REC-03).
 *
 * @param quantityBase cantidad ya expandida con merma, en unidad base
 * @param unitCost coste vigente por unidad base; {@code null} si no se conoce
 */
public record ScaledIngredientResponse(
        long productId,
        String productName,
        String unit,
        BigDecimal quantityBase,
        BigDecimal wastePct,
        BigDecimal unitCost,
        BigDecimal cost) {

    public static ScaledIngredientResponse from(ScaledIngredient ingredient) {
        return new ScaledIngredientResponse(
                ingredient.productId(),
                ingredient.productName(),
                ingredient.unit(),
                ingredient.quantityBase(),
                ingredient.wastePct(),
                ingredient.unitCost(),
                ingredient.cost());
    }
}
