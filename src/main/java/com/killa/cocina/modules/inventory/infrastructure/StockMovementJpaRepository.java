package com.killa.cocina.modules.inventory.infrastructure;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data del kardex (solo inserción y lectura). */
interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, Long> {

    List<StockMovementEntity> findByEstablishmentIdAndProductIdOrderByOccurredAtDescIdDesc(
            long establishmentId, long productId, Pageable pageable);

    long countByEstablishmentIdAndProductId(long establishmentId, long productId);
}
