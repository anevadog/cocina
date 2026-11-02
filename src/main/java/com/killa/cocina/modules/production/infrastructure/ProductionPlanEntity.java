package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.production.domain.ProductionPlan;
import com.killa.cocina.modules.production.domain.ProductionStatus;
import com.killa.cocina.modules.production.domain.ServiceType;
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
import java.time.LocalDate;

/** Mapeo JPA de {@code production_plans}. */
@Entity
@Table(name = "production_plans")
public class ProductionPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "production_plans_seq")
    @SequenceGenerator(name = "production_plans_seq", sequenceName = "production_plans_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 10)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductionStatus status;

    @Column(name = "created_by", nullable = false)
    private long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductionPlanEntity() {}

    private ProductionPlanEntity(ProductionPlan plan) {
        apply(plan);
        this.createdAt = plan.createdAt();
    }

    static ProductionPlanEntity from(ProductionPlan plan) {
        return new ProductionPlanEntity(plan);
    }

    /** Copia el estado del dominio; no toca {@code createdAt}. */
    void apply(ProductionPlan plan) {
        this.id = plan.id();
        this.establishmentId = plan.establishmentId();
        this.serviceDate = plan.serviceDate();
        this.serviceType = plan.serviceType();
        this.status = plan.status();
        this.createdBy = plan.createdBy();
        this.updatedAt = plan.updatedAt();
    }

    ProductionPlan toDomain() {
        return new ProductionPlan(
                id, establishmentId, serviceDate, serviceType, status, createdBy, createdAt, updatedAt);
    }

    Long getId() {
        return id;
    }
}
