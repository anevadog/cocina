package com.killa.cocina.modules.recipes.infrastructure;

import com.killa.cocina.modules.recipes.domain.RecipeVersion;
import com.killa.cocina.modules.recipes.domain.RecipeVersionRepository;
import com.killa.cocina.modules.recipes.domain.RecipeVersionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link RecipeVersionRepository}. */
@Repository
class RecipeVersionRepositoryAdapter implements RecipeVersionRepository {

    private final RecipeVersionJpaRepository versions;

    RecipeVersionRepositoryAdapter(RecipeVersionJpaRepository versions) {
        this.versions = versions;
    }

    @Override
    public Optional<RecipeVersion> findById(long establishmentId, long recipeId, long versionId) {
        return versions.findByIdAndEstablishmentIdAndRecipeId(versionId, establishmentId, recipeId)
                .map(RecipeVersionEntity::toDomain);
    }

    @Override
    public Optional<RecipeVersion> findById(long establishmentId, long versionId) {
        return versions.findByIdAndEstablishmentId(versionId, establishmentId).map(RecipeVersionEntity::toDomain);
    }

    @Override
    public List<RecipeVersion> findByRecipe(long establishmentId, long recipeId) {
        return versions.findByEstablishmentIdAndRecipeIdOrderByVersionNumberDesc(establishmentId, recipeId).stream()
                .map(RecipeVersionEntity::toDomain)
                .toList();
    }

    @Override
    public List<RecipeVersion> findPublished(long establishmentId) {
        return versions
                .findByEstablishmentIdAndStatusOrderByRecipeIdAscVersionNumberAsc(
                        establishmentId, RecipeVersionStatus.PUBLISHED)
                .stream()
                .map(RecipeVersionEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<RecipeVersion> latest(long establishmentId, long recipeId) {
        return versions.findFirstByEstablishmentIdAndRecipeIdOrderByVersionNumberDesc(establishmentId, recipeId)
                .map(RecipeVersionEntity::toDomain);
    }

    @Override
    public Optional<RecipeVersion> findDraft(long establishmentId, long recipeId) {
        return versions.findByEstablishmentIdAndRecipeIdAndStatus(establishmentId, recipeId, RecipeVersionStatus.DRAFT)
                .map(RecipeVersionEntity::toDomain);
    }

    @Override
    public long countByRecipe(long establishmentId, long recipeId) {
        return versions.countByEstablishmentIdAndRecipeId(establishmentId, recipeId);
    }

    @Override
    public RecipeVersion save(RecipeVersion version) {
        RecipeVersionEntity entity = version.id() == null
                ? RecipeVersionEntity.from(version)
                : versions.findById(version.id()).orElseGet(() -> RecipeVersionEntity.from(version));
        if (version.id() != null) {
            entity.apply(version);
        }
        return versions.save(entity).toDomain();
    }

    @Override
    public void delete(long versionId) {
        versions.deleteById(versionId);
    }
}
