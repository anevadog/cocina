package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;

/**
 * Ingrediente de una versión de receta (REC-02).
 *
 * <p>{@code quantityBase} es la cantidad <strong>por ración</strong> en la unidad base del producto,
 * con la escala de stock. {@code wastePct} se suma a esa cantidad para obtener el consumo real
 * (ADR-033).</p>
 */
public record RecipeItem(
        Long id,
        long establishmentId,
        long versionId,
        long productId,
        BigDecimal quantityBase,
        BigDecimal wastePct,
        int sortOrder) {

    public static RecipeItem create(
            long establishmentId,
            long versionId,
            long productId,
            BigDecimal quantityBase,
            BigDecimal wastePct,
            int sortOrder) {
        return new RecipeItem(null, establishmentId, versionId, productId, quantityBase, wastePct, sortOrder);
    }

    /** Copia el ingrediente a una versión nueva, conservando orden y merma. */
    public RecipeItem copyTo(long newVersionId) {
        return new RecipeItem(null, establishmentId, newVersionId, productId, quantityBase, wastePct, sortOrder);
    }
}
