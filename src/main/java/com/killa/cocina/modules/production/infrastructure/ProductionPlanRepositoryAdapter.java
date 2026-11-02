package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.production.domain.ProductionPlan;
import com.killa.cocina.modules.production.domain.ProductionPlanRepository;
import com.killa.cocina.modules.production.domain.ServiceType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link ProductionPlanRepository}. */
@Repository
class ProductionPlanRepositoryAdapter implements ProductionPlanRepository {

    private final ProductionPlanJpaRepository plans;

    ProductionPlanRepositoryAdapter(ProductionPlanJpaRepository plans) {
        this.plans = plans;
    }

    @Override
    public Optional<ProductionPlan> findById(long establishmentId, long planId) {
        return plans.findByIdAndEstablishmentId(planId, establishmentId).map(ProductionPlanEntity::toDomain);
    }

    @Override
    public Optional<ProductionPlan> findForUpdate(long establishmentId, long planId) {
        return plans.findForUpdate(planId, establishmentId).map(ProductionPlanEntity::toDomain);
    }

    @Override
    public Optional<ProductionPlan> findByService(
            long establishmentId, LocalDate serviceDate, ServiceType serviceType) {
        return plans.findByEstablishmentIdAndServiceDateAndServiceType(establishmentId, serviceDate, serviceType)
                .map(ProductionPlanEntity::toDomain);
    }

    @Override
    public List<ProductionPlan> findByDate(long establishmentId, LocalDate serviceDate, int page, int size) {
        return plans
                .findByEstablishmentIdAndServiceDateOrderByServiceTypeAsc(
                        establishmentId, serviceDate, PageRequest.of(page, size))
                .stream()
                .map(ProductionPlanEntity::toDomain)
                .toList();
    }

    @Override
    public long countByDate(long establishmentId, LocalDate serviceDate) {
        return plans.countByEstablishmentIdAndServiceDate(establishmentId, serviceDate);
    }

    @Override
    public ProductionPlan save(ProductionPlan plan) {
        ProductionPlanEntity entity = plan.id() == null
                ? ProductionPlanEntity.from(plan)
                : plans.findById(plan.id()).orElseGet(() -> ProductionPlanEntity.from(plan));
        if (plan.id() != null) {
            entity.apply(plan);
        }
        return plans.save(entity).toDomain();
    }

    @Override
    public void delete(long planId) {
        plans.deleteById(planId);
    }
}
