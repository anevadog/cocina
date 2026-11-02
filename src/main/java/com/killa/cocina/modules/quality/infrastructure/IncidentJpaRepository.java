package com.killa.cocina.modules.quality.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de incidencias. */
interface IncidentJpaRepository extends JpaRepository<IncidentEntity, Long> {

    Optional<IncidentEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IncidentEntity i where i.id = :id and i.establishmentId = :establishmentId")
    Optional<IncidentEntity> findByIdAndEstablishmentIdForUpdate(
            @Param("id") long id, @Param("establishmentId") long establishmentId);

    List<IncidentEntity> findByEstablishmentIdOrderByOpenedAtDescIdDesc(long establishmentId, Pageable pageable);

    long countByEstablishmentId(long establishmentId);
}
