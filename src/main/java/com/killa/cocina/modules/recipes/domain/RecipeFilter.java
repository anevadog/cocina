package com.killa.cocina.modules.recipes.domain;

/**
 * Filtros del listado de recetas.
 *
 * @param active si se filtra por estado; {@code null} devuelve activas y archivadas
 * @param name si se filtra por nombre; {@code null} o vacío devuelve todas
 */
public record RecipeFilter(Boolean active, String name) {

    public static RecipeFilter none() {
        return new RecipeFilter(null, null);
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }
}
