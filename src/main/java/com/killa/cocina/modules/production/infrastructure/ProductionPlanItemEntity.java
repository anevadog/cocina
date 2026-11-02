package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.production.domain.ProductionPlanItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** Mapeo JPA de {@code production_plan_items}. */
@Entity
@Table(name = "production_plan_items")
public class ProductionPlanItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "production_plan_items_seq")
    @SequenceGenerator(
            name = "production_plan_items_seq",
            sequenceName = "production_plan_items_seq",
            allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "plan_id", nullable = false)
    private long planId;

    @Column(name = "recipe_version_id", nullable = false)
    private long recipeVersionId;

    @Column(name = "planned_rations", nullable = false)
    private int plannedRations;

    protected ProductionPlanItemEntity() {}

    private ProductionPlanItemEntity(ProductionPlanItem item) {
        apply(item);
    }

    static ProductionPlanItemEntity from(ProductionPlanItem item) {
        return new ProductionPlanItemEntity(item);
    }

    void apply(ProductionPlanItem item) {
        this.id = item.id();
        this.establishmentId = item.establishmentId();
        this.planId = item.planId();
        this.recipeVersionId = item.recipeVersionId();
        this.plannedRations = item.plannedRations();
    }

    ProductionPlanItem toDomain() {
        return new ProductionPlanItem(id, establishmentId, planId, recipeVersionId, plannedRations);
    }

    Long getId() {
        return id;
    }
}
