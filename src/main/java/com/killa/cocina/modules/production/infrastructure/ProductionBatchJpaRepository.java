package com.killa.cocina.modules.production.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de batches de producción. */
interface ProductionBatchJpaRepository extends JpaRepository<ProductionBatchEntity, Long> {

    Optional<ProductionBatchEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ProductionBatchEntity b where b.id = :id and b.establishmentId = :establishmentId")
    Optional<ProductionBatchEntity> findForUpdate(@Param("id") long id, @Param("establishmentId") long establishmentId);

    List<ProductionBatchEntity> findByPlanItemIdInOrderByIdAsc(Collection<Long> planItemIds);

    Optional<ProductionBatchEntity> findByPlanItemId(long planItemId);

    boolean existsByPlanItemId(long planItemId);
}
