package com.killa.cocina.shared.pagination;

import java.util.List;

/**
 * Página independiente de Spring (PLA-03).
 *
 * <p>El dominio y los servicios de aplicación no conocen {@code Pageable} ni {@code Page}; la capa
 * web decide cómo exponerla.</p>
 *
 * @param items elementos de la página
 * @param total número total de elementos
 * @param page número de página, empezando en 0
 * @param size tamaño de la página
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public PageResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
