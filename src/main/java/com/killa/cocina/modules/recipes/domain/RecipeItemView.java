package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;

/**
 * Ingrediente de una versión para la API (REC-02).
 *
 * @param unit etiqueta de la unidad base del producto ({@code g}, {@code ml}, {@code ud})
 * @param quantityBase cantidad por ración en esa unidad, sin merma
 * @param wastePct merma aplicada al consumo y al coste
 */
public record RecipeItemView(
        Long id,
        long productId,
        String productName,
        String unit,
        BigDecimal quantityBase,
        BigDecimal wastePct,
        int sortOrder) {}
