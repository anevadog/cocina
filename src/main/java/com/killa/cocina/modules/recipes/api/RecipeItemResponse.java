package com.killa.cocina.modules.recipes.api;

import com.killa.cocina.modules.recipes.domain.RecipeItemView;
import java.math.BigDecimal;

/**
 * Ingrediente expuesto por la API (REC-02).
 *
 * @param quantityBase cantidad por ración en la unidad base, sin merma
 * @param wastePct merma que se suma a la cantidad y al coste
 */
public record RecipeItemResponse(
        Long id,
        long productId,
        String productName,
        String unit,
        BigDecimal quantityBase,
        BigDecimal wastePct,
        int sortOrder) {

    public static RecipeItemResponse from(RecipeItemView view) {
        return new RecipeItemResponse(
                view.id(),
                view.productId(),
                view.productName(),
                view.unit(),
                view.quantityBase(),
                view.wastePct(),
                view.sortOrder());
    }
}
