package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.production.domain.ProductionPlanItem;
import com.killa.cocina.modules.production.domain.ProductionPlanItemRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link ProductionPlanItemRepository}. */
@Repository
class ProductionPlanItemRepositoryAdapter implements ProductionPlanItemRepository {

    private final ProductionPlanItemJpaRepository items;

    ProductionPlanItemRepositoryAdapter(ProductionPlanItemJpaRepository items) {
        this.items = items;
    }

    @Override
    public List<ProductionPlanItem> findByPlan(long planId) {
        return items.findByPlanIdOrderByIdAsc(planId).stream()
                .map(ProductionPlanItemEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<ProductionPlanItem> findById(long establishmentId, long planId, long itemId) {
        return items.findByIdAndEstablishmentIdAndPlanId(itemId, establishmentId, planId)
                .map(ProductionPlanItemEntity::toDomain);
    }

    @Override
    public void replaceForPlan(long planId, List<ProductionPlanItem> replacements) {
        items.deleteByPlanId(planId);
        items.saveAll(replacements.stream().map(ProductionPlanItemEntity::from).toList());
    }

    @Override
    public void delete(long itemId) {
        items.deleteById(itemId);
    }
}
