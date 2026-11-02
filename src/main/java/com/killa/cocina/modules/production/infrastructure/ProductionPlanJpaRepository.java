package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.production.domain.ServiceType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de planes de producción. */
interface ProductionPlanJpaRepository extends JpaRepository<ProductionPlanEntity, Long> {

    Optional<ProductionPlanEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    Optional<ProductionPlanEntity> findByEstablishmentIdAndServiceDateAndServiceType(
            long establishmentId, LocalDate serviceDate, ServiceType serviceType);

    List<ProductionPlanEntity> findByEstablishmentIdAndServiceDateOrderByServiceTypeAsc(
            long establishmentId, LocalDate serviceDate, Pageable pageable);

    long countByEstablishmentIdAndServiceDate(long establishmentId, LocalDate serviceDate);
}
