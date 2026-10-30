package com.killa.cocina.modules.inventory.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de productos. */
interface ProductJpaRepository
        extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

    Optional<ProductEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    boolean existsByEstablishmentIdAndName(long establishmentId, String name);

    boolean existsByEstablishmentIdAndNameAndIdNot(long establishmentId, String name, long excludedId);

    List<ProductEntity> findByEstablishmentIdOrderByNameAsc(long establishmentId, Pageable pageable);

    /** Bloqueo pesimista del producto: serializa los movimientos de stock (ADR-022). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.id = :id and p.establishmentId = :establishmentId")
    Optional<ProductEntity> findForUpdate(@Param("id") long id,
            @Param("establishmentId") long establishmentId);

    /** INV-07: productos activos por debajo de su mínimo, ordenados por nombre. */
    @Query("""
            select p from ProductEntity p
            where p.establishmentId = :establishmentId and p.active = true
              and p.currentStock < p.minStock
            order by p.name asc
            """)
    List<ProductEntity> findLowStock(@Param("establishmentId") long establishmentId,
            Pageable pageable);

    @Query("""
            select count(p) from ProductEntity p
            where p.establishmentId = :establishmentId and p.active = true
              and p.currentStock < p.minStock
            """)
    long countLowStock(@Param("establishmentId") long establishmentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProductUnitConversionEntity c where c.productId = :productId")
    void deleteConversions(@Param("productId") long productId);
}
