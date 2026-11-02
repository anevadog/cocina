package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.production.domain.ProductionBatch;
import com.killa.cocina.modules.production.domain.ProductionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** Mapeo JPA de {@code production_batches}. */
@Entity
@Table(name = "production_batches")
public class ProductionBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "production_batches_seq")
    @SequenceGenerator(name = "production_batches_seq", sequenceName = "production_batches_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "plan_item_id", nullable = false)
    private long planItemId;

    @Column(name = "recipe_version_id", nullable = false)
    private long recipeVersionId;

    @Column(name = "planned_rations", nullable = false)
    private int plannedRations;

    @Column(nullable = false)
    private int rations;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "started_by", nullable = false)
    private long startedBy;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "finished_by")
    private Long finishedBy;

    @Column(name = "deviation_reason", length = 200)
    private String deviationReason;

    protected ProductionBatchEntity() {}

    private ProductionBatchEntity(ProductionBatch batch) {
        apply(batch);
    }

    static ProductionBatchEntity from(ProductionBatch batch) {
        return new ProductionBatchEntity(batch);
    }

    void apply(ProductionBatch batch) {
        this.id = batch.id();
        this.establishmentId = batch.establishmentId();
        this.planItemId = batch.planItemId();
        this.recipeVersionId = batch.recipeVersionId();
        this.plannedRations = batch.plannedRations();
        this.rations = batch.rations();
        this.status = batch.status();
        this.startedAt = batch.startedAt();
        this.startedBy = batch.startedBy();
        this.finishedAt = batch.finishedAt();
        this.finishedBy = batch.finishedBy();
        this.deviationReason = batch.deviationReason();
    }

    ProductionBatch toDomain() {
        return new ProductionBatch(
                id,
                establishmentId,
                planItemId,
                recipeVersionId,
                plannedRations,
                rations,
                status,
                startedAt,
                startedBy,
                finishedAt,
                finishedBy,
                deviationReason);
    }

    Long getId() {
        return id;
    }
}
