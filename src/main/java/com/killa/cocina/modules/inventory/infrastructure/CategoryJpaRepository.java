package com.killa.cocina.modules.inventory.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de categorías. */
interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {

    Optional<CategoryEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    boolean existsByEstablishmentIdAndName(long establishmentId, String name);

    boolean existsByEstablishmentIdAndNameAndIdNot(long establishmentId, String name, long excludedId);

    List<CategoryEntity> findByEstablishmentIdOrderByNameAsc(long establishmentId, Pageable pageable);

    long countByEstablishmentId(long establishmentId);
}
