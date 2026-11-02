package com.killa.cocina.modules.production.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de batches de producción. */
interface ProductionBatchJpaRepository extends JpaRepository<ProductionBatchEntity, Long> {

    Optional<ProductionBatchEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    List<ProductionBatchEntity> findByPlanItemIdInOrderByIdAsc(Collection<Long> planItemIds);

    Optional<ProductionBatchEntity> findByPlanItemId(long planItemId);

    boolean existsByPlanItemId(long planItemId);
}
