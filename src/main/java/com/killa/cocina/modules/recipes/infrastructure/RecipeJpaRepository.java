package com.killa.cocina.modules.recipes.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repositorio Spring Data de recetas. */
interface RecipeJpaRepository extends JpaRepository<RecipeEntity, Long>, JpaSpecificationExecutor<RecipeEntity> {

    Optional<RecipeEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    boolean existsByEstablishmentIdAndName(long establishmentId, String name);

    boolean existsByEstablishmentIdAndNameAndIdNot(long establishmentId, String name, long excludedId);
}
