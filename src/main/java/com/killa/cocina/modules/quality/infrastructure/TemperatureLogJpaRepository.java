package com.killa.cocina.modules.quality.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de lecturas de temperatura. */
interface TemperatureLogJpaRepository extends JpaRepository<TemperatureLogEntity, Long> {

    Optional<TemperatureLogEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    List<TemperatureLogEntity> findByEstablishmentIdOrderByRecordedAtDescIdDesc(
            long establishmentId, Pageable pageable);

    long countByEstablishmentId(long establishmentId);

    List<TemperatureLogEntity> findByEstablishmentIdAndWithinRangeFalseOrderByRecordedAtDescIdDesc(
            long establishmentId, Pageable pageable);
}
