package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.production.domain.ProductionComposition;
import com.killa.cocina.modules.production.domain.ProductionCompositionItem;
import com.killa.cocina.modules.production.domain.RecipeCompositionCatalog;
import com.killa.cocina.modules.recipes.domain.Recipe;
import com.killa.cocina.modules.recipes.domain.RecipeItemRepository;
import com.killa.cocina.modules.recipes.domain.RecipeRepository;
import com.killa.cocina.modules.recipes.domain.RecipeVersion;
import com.killa.cocina.modules.recipes.domain.RecipeVersionRepository;
import com.killa.cocina.modules.recipes.domain.RecipeVersionStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Adaptador del puerto {@link RecipeCompositionCatalog} sobre los puertos publicados de recetas.
 *
 * <p>Producción nunca toca las entidades JPA de recetas: usa sus repositorios de dominio, igual que
 * recetas usa los de inventario (ADR-037).</p>
 */
@Component
class RecipeCompositionAdapter implements RecipeCompositionCatalog {

    private final RecipeVersionRepository versions;

    private final RecipeRepository recipes;

    private final RecipeItemRepository items;

    RecipeCompositionAdapter(RecipeVersionRepository versions, RecipeRepository recipes, RecipeItemRepository items) {
        this.versions = versions;
        this.recipes = recipes;
        this.items = items;
    }

    @Override
    public Optional<ProductionComposition> findPublished(long establishmentId, long versionId) {
        return versions.findById(establishmentId, versionId)
                .filter(version -> version.status() == RecipeVersionStatus.PUBLISHED)
                .flatMap(version -> composition(establishmentId, version));
    }

    private Optional<ProductionComposition> composition(long establishmentId, RecipeVersion version) {
        return recipes.findById(establishmentId, version.recipeId()).map(recipe -> toComposition(recipe, version));
    }

    private ProductionComposition toComposition(Recipe recipe, RecipeVersion version) {
        return new ProductionComposition(
                recipe.id(),
                recipe.name(),
                recipe.active(),
                version.id(),
                version.versionNumber(),
                version.costPerRation(),
                items.findByVersion(version.id()).stream()
                        .map(item ->
                                new ProductionCompositionItem(item.productId(), item.quantityBase(), item.wastePct()))
                        .toList());
    }
}
