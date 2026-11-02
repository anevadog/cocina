package com.killa.cocina.modules.reporting.infrastructure;

import com.killa.cocina.modules.recipes.domain.Recipe;
import com.killa.cocina.modules.recipes.domain.RecipeRepository;
import com.killa.cocina.modules.recipes.domain.RecipeVersion;
import com.killa.cocina.modules.recipes.domain.RecipeVersionRepository;
import com.killa.cocina.modules.reporting.domain.RecipeCostCatalog;
import com.killa.cocina.modules.reporting.domain.RecipeCostSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Adaptador del puerto {@link RecipeCostCatalog} sobre los puertos publicados de recetas.
 *
 * <p>Informes no toca las entidades JPA de recetas: usa sus repositorios de dominio, igual que
 * producción (ADR-037) y deja el cálculo del coste congelado donde se hizo, al publicar.</p>
 */
@Component
class RecipeCostCatalogAdapter implements RecipeCostCatalog {

    private final RecipeVersionRepository versions;

    private final RecipeRepository recipes;

    RecipeCostCatalogAdapter(RecipeVersionRepository versions, RecipeRepository recipes) {
        this.versions = versions;
        this.recipes = recipes;
    }

    @Override
    public List<RecipeCostSnapshot> publishedVersions(long establishmentId) {
        List<RecipeVersion> published = versions.findPublished(establishmentId);
        Map<Long, String> names = new HashMap<>();
        for (RecipeVersion version : published) {
            names.computeIfAbsent(
                    version.recipeId(),
                    recipeId -> recipes.findById(establishmentId, recipeId)
                            .map(Recipe::name)
                            .orElse("Receta " + recipeId));
        }
        return published.stream()
                .map(version -> new RecipeCostSnapshot(
                        version.recipeId(),
                        names.get(version.recipeId()),
                        version.id(),
                        version.versionNumber(),
                        version.baseRations(),
                        version.totalCost(),
                        version.costPerRation(),
                        version.publishedAt()))
                .toList();
    }
}
