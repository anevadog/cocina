package com.killa.cocina.modules.recipes.infrastructure;

import com.killa.cocina.modules.recipes.domain.Recipe;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** Mapeo JPA de {@code recipes}. */
@Entity
@Table(name = "recipes")
public class RecipeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "recipes_seq")
    @SequenceGenerator(name = "recipes_seq", sequenceName = "recipes_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 80)
    private String category;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecipeEntity() {}

    private RecipeEntity(Recipe recipe) {
        apply(recipe);
        this.createdAt = recipe.createdAt();
    }

    static RecipeEntity from(Recipe recipe) {
        return new RecipeEntity(recipe);
    }

    /** Copia el estado del dominio; no toca {@code createdAt}. */
    void apply(Recipe recipe) {
        this.id = recipe.id();
        this.establishmentId = recipe.establishmentId();
        this.name = recipe.name();
        this.category = recipe.category();
        this.active = recipe.active();
        this.currentVersionId = recipe.currentVersionId();
        this.updatedAt = recipe.updatedAt();
    }

    Recipe toDomain() {
        return new Recipe(id, establishmentId, name, category, active, currentVersionId, createdAt, updatedAt);
    }

    Long getId() {
        return id;
    }
}
