package com.killa.cocina.modules.recipes.infrastructure;

import com.killa.cocina.modules.recipes.domain.RecipeVersion;
import com.killa.cocina.modules.recipes.domain.RecipeVersionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Mapeo JPA de {@code recipe_versions}. */
@Entity
@Table(name = "recipe_versions")
public class RecipeVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "recipe_versions_seq")
    @SequenceGenerator(name = "recipe_versions_seq", sequenceName = "recipe_versions_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "recipe_id", nullable = false)
    private long recipeId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipeVersionStatus status;

    @Column(name = "base_rations", nullable = false)
    private int baseRations;

    @Column(name = "yield_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal yieldPct;

    @Column(name = "prep_time_minutes")
    private Integer prepTimeMinutes;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(name = "total_cost", precision = 12, scale = 4)
    private BigDecimal totalCost;

    @Column(name = "cost_per_ration", precision = 12, scale = 4)
    private BigDecimal costPerRation;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecipeVersionEntity() {}

    private RecipeVersionEntity(RecipeVersion version) {
        apply(version);
        this.createdAt = version.createdAt();
    }

    static RecipeVersionEntity from(RecipeVersion version) {
        return new RecipeVersionEntity(version);
    }

    /** Copia el estado del dominio; no toca {@code createdAt}. */
    void apply(RecipeVersion version) {
        this.id = version.id();
        this.establishmentId = version.establishmentId();
        this.recipeId = version.recipeId();
        this.versionNumber = version.versionNumber();
        this.status = version.status();
        this.baseRations = version.baseRations();
        this.yieldPct = version.yieldPct();
        this.prepTimeMinutes = version.prepTimeMinutes();
        this.instructions = version.instructions();
        this.totalCost = version.totalCost();
        this.costPerRation = version.costPerRation();
        this.publishedAt = version.publishedAt();
        this.publishedBy = version.publishedBy();
        this.createdBy = version.createdBy();
        this.updatedAt = version.updatedAt();
    }

    RecipeVersion toDomain() {
        return new RecipeVersion(
                id,
                establishmentId,
                recipeId,
                versionNumber,
                status,
                baseRations,
                yieldPct,
                prepTimeMinutes,
                instructions,
                totalCost,
                costPerRation,
                publishedAt,
                publishedBy,
                createdBy,
                createdAt,
                updatedAt);
    }

    Long getId() {
        return id;
    }
}
