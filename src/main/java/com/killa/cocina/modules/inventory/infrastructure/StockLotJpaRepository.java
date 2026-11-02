package com.killa.cocina.modules.inventory.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de lotes. */
interface StockLotJpaRepository extends JpaRepository<StockLotEntity, Long> {

    Optional<StockLotEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    /**
     * Lotes con saldo en orden FIFO: primero la caducidad más próxima y, entre los que no caducan,
     * los más antiguos. Los lotes sin fecha van al final.
     */
    @Query("""
            select l from StockLotEntity l
            where l.establishmentId = :establishmentId and l.productId = :productId
              and l.remainingQty > 0
            order by case when l.expiresAt is null then 1 else 0 end,
                     l.expiresAt asc, l.receivedAt asc, l.id asc
            """)
    List<StockLotEntity> findConsumable(
            @Param("establishmentId") long establishmentId, @Param("productId") long productId);

    /**
     * Igual que {@link #findConsumable}, pero excluye los lotes caducados en {@code now}. Es el
     * consumo de producción: un lote caducado no se consume (CAL-04, ADR-030).
     */
    @Query("""
            select l from StockLotEntity l
            where l.establishmentId = :establishmentId and l.productId = :productId
              and l.remainingQty > 0
              and (l.expiresAt is null or l.expiresAt > :now)
            order by case when l.expiresAt is null then 1 else 0 end,
                     l.expiresAt asc, l.receivedAt asc, l.id asc
            """)
    List<StockLotEntity> findUsable(
            @Param("establishmentId") long establishmentId,
            @Param("productId") long productId,
            @Param("now") java.time.Instant now);

    List<StockLotEntity> findByEstablishmentIdAndProductIdOrderByReceivedAtDescIdDesc(
            long establishmentId, long productId, Pageable pageable);

    long countByEstablishmentIdAndProductId(long establishmentId, long productId);

    @Query("""
            select l.id, l.productId, p.name, l.expiresAt, l.remainingQty, l.unitCost
            from StockLotEntity l, ProductEntity p
            where l.productId = p.id
              and l.establishmentId = :establishmentId
              and l.remainingQty > 0
              and l.expiresAt is not null
              and l.expiresAt <= :until
            order by l.expiresAt asc, l.id asc
            """)
    List<Object[]> findExpiring(
            @Param("establishmentId") long establishmentId, @Param("until") java.time.Instant until, Pageable pageable);

    @Query("""
            select count(l) from StockLotEntity l
            where l.establishmentId = :establishmentId
              and l.remainingQty > 0
              and l.expiresAt is not null
              and l.expiresAt <= :until
            """)
    long countExpiring(@Param("establishmentId") long establishmentId, @Param("until") java.time.Instant until);
}
