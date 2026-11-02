package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.MovementType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data del kardex (solo inserción y lectura). */
interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, Long> {

    List<StockMovementEntity> findByEstablishmentIdAndProductIdOrderByOccurredAtDescIdDesc(
            long establishmentId, long productId, Pageable pageable);

    long countByEstablishmentIdAndProductId(long establishmentId, long productId);

    /** Consumo de producción por producto para unos batches (ECO-03). */
    @Query("""
            select m.productId, sum(m.quantityBase)
            from StockMovementEntity m
            where m.establishmentId = :establishmentId
              and m.type = :type
              and m.referenceId in :batchIds
            group by m.productId
            """)
    List<Object[]> sumProductionOutByBatches(
            @Param("establishmentId") long establishmentId,
            @Param("type") MovementType type,
            @Param("batchIds") Collection<Long> batchIds);

    /**
     * Ajustes netos por producto: las salidas suman y las entradas restan. La consulta se hace en
     * SQL agregado para no cargar el kardex completo (ECO-03, arquitectura §8).
     */
    @Query("""
            select m.productId,
                   sum(case when m.type = :outType then m.quantityBase else -m.quantityBase end)
            from StockMovementEntity m
            where m.establishmentId = :establishmentId
              and m.type in :types
              and m.occurredAt >= :fromInclusive
              and m.occurredAt < :toExclusive
            group by m.productId
            """)
    List<Object[]> sumNetAdjustments(
            @Param("establishmentId") long establishmentId,
            @Param("outType") MovementType outType,
            @Param("types") Collection<MovementType> types,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);
}
