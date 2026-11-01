package com.killa.cocina.modules.recipes.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de ingredientes de receta. */
interface RecipeItemJpaRepository extends JpaRepository<RecipeItemEntity, Long> {

    List<RecipeItemEntity> findByVersionIdOrderBySortOrderAsc(long versionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecipeItemEntity i where i.versionId = :versionId")
    void deleteByVersionId(@Param("versionId") long versionId);
}
