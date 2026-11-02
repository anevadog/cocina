package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.production.domain.ServiceType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de planes de producción. */
interface ProductionPlanJpaRepository extends JpaRepository<ProductionPlanEntity, Long> {

    Optional<ProductionPlanEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductionPlanEntity p where p.id = :id and p.establishmentId = :establishmentId")
    Optional<ProductionPlanEntity> findForUpdate(@Param("id") long id, @Param("establishmentId") long establishmentId);

    Optional<ProductionPlanEntity> findByEstablishmentIdAndServiceDateAndServiceType(
            long establishmentId, LocalDate serviceDate, ServiceType serviceType);

    List<ProductionPlanEntity> findByEstablishmentIdAndServiceDateOrderByServiceTypeAsc(
            long establishmentId, LocalDate serviceDate, Pageable pageable);

    long countByEstablishmentIdAndServiceDate(long establishmentId, LocalDate serviceDate);
}
