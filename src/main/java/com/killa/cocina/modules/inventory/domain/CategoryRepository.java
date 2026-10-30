package com.killa.cocina.modules.inventory.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de categorías de producto. */
public interface CategoryRepository {

    Optional<Category> findById(long establishmentId, long categoryId);

    boolean existsByName(long establishmentId, String name);

    /** Comprueba el nombre excluyendo una categoría, para renombrar sin chocar consigo misma. */
    boolean existsByNameExcluding(long establishmentId, String name, long excludedCategoryId);

    Category save(Category category);

    List<Category> findAll(long establishmentId, int page, int size);

    long count(long establishmentId);
}
