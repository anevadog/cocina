package com.killa.cocina.modules.recipes.domain;

import java.util.List;

/** Puerto de persistencia de los ingredientes de una versión. */
public interface RecipeItemRepository {

    List<RecipeItem> findByVersion(long versionId);

    /** Sustituye el conjunto completo de ingredientes de una versión. */
    void replaceForVersion(long versionId, List<RecipeItem> items);

    void deleteByVersion(long versionId);
}
