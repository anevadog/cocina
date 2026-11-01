package com.killa.cocina.modules.recipes.infrastructure;

import com.killa.cocina.modules.recipes.domain.Recipe;
import com.killa.cocina.modules.recipes.domain.RecipeFilter;
import com.killa.cocina.modules.recipes.domain.RecipeRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link RecipeRepository}. */
@Repository
class RecipeRepositoryAdapter implements RecipeRepository {

    private final RecipeJpaRepository recipes;

    RecipeRepositoryAdapter(RecipeJpaRepository recipes) {
        this.recipes = recipes;
    }

    @Override
    public Optional<Recipe> findById(long establishmentId, long recipeId) {
        return recipes.findByIdAndEstablishmentId(recipeId, establishmentId).map(RecipeEntity::toDomain);
    }

    @Override
    public boolean existsByName(long establishmentId, String name) {
        return recipes.existsByEstablishmentIdAndName(establishmentId, name);
    }

    @Override
    public boolean existsByNameExcluding(long establishmentId, String name, long excludedRecipeId) {
        return recipes.existsByEstablishmentIdAndNameAndIdNot(establishmentId, name, excludedRecipeId);
    }

    @Override
    public Recipe save(Recipe recipe) {
        RecipeEntity entity = recipe.id() == null
                ? RecipeEntity.from(recipe)
                : recipes.findById(recipe.id()).orElseGet(() -> RecipeEntity.from(recipe));
        if (recipe.id() != null) {
            entity.apply(recipe);
        }
        return recipes.save(entity).toDomain();
    }

    @Override
    public List<Recipe> search(long establishmentId, RecipeFilter filter, int page, int size) {
        return recipes
                .findAll(
                        specification(establishmentId, filter),
                        PageRequest.of(page, size, Sort.by("name").ascending()))
                .stream()
                .map(RecipeEntity::toDomain)
                .toList();
    }

    @Override
    public long count(long establishmentId, RecipeFilter filter) {
        return recipes.count(specification(establishmentId, filter));
    }

    private static Specification<RecipeEntity> specification(long establishmentId, RecipeFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("establishmentId"), establishmentId));
            if (filter != null && filter.active() != null) {
                predicates.add(builder.equal(root.get("active"), filter.active()));
            }
            if (filter != null && filter.hasName()) {
                String needle =
                        "%" + filter.name().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.like(builder.lower(root.get("name")), needle));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
