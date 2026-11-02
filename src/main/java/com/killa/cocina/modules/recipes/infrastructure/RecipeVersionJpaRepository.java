package com.killa.cocina.modules.recipes.infrastructure;

import com.killa.cocina.modules.recipes.domain.RecipeVersionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de versiones de receta. */
interface RecipeVersionJpaRepository extends JpaRepository<RecipeVersionEntity, Long> {

    Optional<RecipeVersionEntity> findByIdAndEstablishmentIdAndRecipeId(long id, long establishmentId, long recipeId);

    Optional<RecipeVersionEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    List<RecipeVersionEntity> findByEstablishmentIdAndRecipeIdOrderByVersionNumberDesc(
            long establishmentId, long recipeId);

    Optional<RecipeVersionEntity> findFirstByEstablishmentIdAndRecipeIdOrderByVersionNumberDesc(
            long establishmentId, long recipeId);

    Optional<RecipeVersionEntity> findByEstablishmentIdAndRecipeIdAndStatus(
            long establishmentId, long recipeId, RecipeVersionStatus status);

    long countByEstablishmentIdAndRecipeId(long establishmentId, long recipeId);
}
