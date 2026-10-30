package com.killa.cocina.shared.web;

import com.killa.cocina.shared.pagination.PageResult;
import java.util.List;
import java.util.function.Function;

/**
 * Página del contrato de la API (PLA-03), compartida por todos los módulos.
 *
 * <p>El nombre {@code content} es el fijado en {@code docs/arquitectura.md} §7 y coincide con el de
 * Spring Data, para que el cliente no tenga que conocer dos vocabularios.</p>
 *
 * @param page número de página; empieza en 0 (primera página = 0)
 */
public record PageResponse<T>(List<T> content, long total, int page, int size) {

    public static <D, R> PageResponse<R> from(PageResult<D> result, Function<D, R> mapper) {
        return new PageResponse<>(result.items().stream().map(mapper).toList(), result.total(),
                result.page(), result.size());
    }
}
