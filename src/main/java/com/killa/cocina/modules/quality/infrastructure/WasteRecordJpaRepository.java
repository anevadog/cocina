package com.killa.cocina.modules.quality.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de mermas. */
interface WasteRecordJpaRepository extends JpaRepository<WasteRecordEntity, Long> {

    Optional<WasteRecordEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    List<WasteRecordEntity> findByEstablishmentIdOrderByOccurredAtDescIdDesc(long establishmentId, Pageable pageable);

    long countByEstablishmentId(long establishmentId);
}
