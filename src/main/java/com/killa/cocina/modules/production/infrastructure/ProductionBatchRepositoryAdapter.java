package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.production.domain.ProductionBatch;
import com.killa.cocina.modules.production.domain.ProductionBatchRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link ProductionBatchRepository}. */
@Repository
class ProductionBatchRepositoryAdapter implements ProductionBatchRepository {

    private final ProductionBatchJpaRepository batches;

    ProductionBatchRepositoryAdapter(ProductionBatchJpaRepository batches) {
        this.batches = batches;
    }

    @Override
    public Optional<ProductionBatch> findById(long establishmentId, long batchId) {
        return batches.findByIdAndEstablishmentId(batchId, establishmentId).map(ProductionBatchEntity::toDomain);
    }

    @Override
    public List<ProductionBatch> findByPlanItems(Collection<Long> planItemIds) {
        if (planItemIds == null || planItemIds.isEmpty()) {
            return List.of();
        }
        return batches.findByPlanItemIdInOrderByIdAsc(planItemIds).stream()
                .map(ProductionBatchEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<ProductionBatch> findByPlanItem(long planItemId) {
        return batches.findByPlanItemId(planItemId).map(ProductionBatchEntity::toDomain);
    }

    @Override
    public boolean existsByPlanItem(long planItemId) {
        return batches.existsByPlanItemId(planItemId);
    }

    @Override
    public ProductionBatch save(ProductionBatch batch) {
        ProductionBatchEntity entity = batch.id() == null
                ? ProductionBatchEntity.from(batch)
                : batches.findById(batch.id()).orElseGet(() -> ProductionBatchEntity.from(batch));
        if (batch.id() != null) {
            entity.apply(batch);
        }
        return batches.save(entity).toDomain();
    }
}
