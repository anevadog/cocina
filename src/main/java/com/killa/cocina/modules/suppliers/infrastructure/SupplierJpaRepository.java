package com.killa.cocina.modules.suppliers.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de proveedores. */
interface SupplierJpaRepository extends JpaRepository<SupplierEntity, Long> {

    Optional<SupplierEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    boolean existsByEstablishmentIdAndName(long establishmentId, String name);

    boolean existsByEstablishmentIdAndNameAndIdNot(long establishmentId, String name, long excludedId);

    List<SupplierEntity> findByEstablishmentIdOrderByNameAsc(long establishmentId, Pageable pageable);

    long countByEstablishmentId(long establishmentId);
}
