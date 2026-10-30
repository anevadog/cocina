package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.pagination.PageResult;

/** Casos de uso de categorías de producto (INV-01). */
public interface CategoryService {

    CategoryView create(long establishmentId, Long actorId, String name);

    CategoryView rename(long establishmentId, Long actorId, long categoryId, String name);

    CategoryView find(long establishmentId, long categoryId);

    PageResult<CategoryView> list(long establishmentId, int page, int size);
}
