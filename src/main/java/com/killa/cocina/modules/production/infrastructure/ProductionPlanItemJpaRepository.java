package com.killa.cocina.modules.production.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de ítems de plan. */
interface ProductionPlanItemJpaRepository extends JpaRepository<ProductionPlanItemEntity, Long> {

    List<ProductionPlanItemEntity> findByPlanIdOrderByIdAsc(long planId);

    Optional<ProductionPlanItemEntity> findByIdAndEstablishmentIdAndPlanId(long id, long establishmentId, long planId);

    Optional<ProductionPlanItemEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    /**
     * Borra los ítems de un plan antes de insertar los nuevos. Debe ser un DELETE inmediato: si
     * Hibernate dejara los borrados para el final, el INSERT de la misma versión chocaría con la
     * clave única {@code (plan_id, recipe_version_id)}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProductionPlanItemEntity item where item.planId = :planId")
    void deleteByPlanId(@Param("planId") long planId);
}
