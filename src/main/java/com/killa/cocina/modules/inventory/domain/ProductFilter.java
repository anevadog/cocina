package com.killa.cocina.modules.inventory.domain;

/**
 * Filtros del listado de productos.
 *
 * @param active si se filtra por estado; {@code null} devuelve activos e inactivos
 * @param categoryId si se filtra por categoría; {@code null} devuelve todas
 */
public record ProductFilter(Boolean active, Long categoryId) {

    public static ProductFilter none() {
        return new ProductFilter(null, null);
    }
}
