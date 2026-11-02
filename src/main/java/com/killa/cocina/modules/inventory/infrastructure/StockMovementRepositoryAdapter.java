package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.MovementType;
import com.killa.cocina.modules.inventory.domain.StockMovement;
import com.killa.cocina.modules.inventory.domain.StockMovementRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link StockMovementRepository}. */
@Repository
class StockMovementRepositoryAdapter implements StockMovementRepository {

    private final StockMovementJpaRepository movements;

    private final Clock clock;

    StockMovementRepositoryAdapter(StockMovementJpaRepository movements, Clock clock) {
        this.movements = movements;
        this.clock = clock;
    }

    @Override
    public StockMovement save(StockMovement movement) {
        return movements
                .save(StockMovementEntity.from(movement, Instant.now(clock)))
                .toDomain();
    }

    @Override
    public List<StockMovement> findByProduct(long establishmentId, long productId, int page, int size) {
        return movements
                .findByEstablishmentIdAndProductIdOrderByOccurredAtDescIdDesc(
                        establishmentId, productId, PageRequest.of(page, size))
                .stream()
                .map(StockMovementEntity::toDomain)
                .toList();
    }

    @Override
    public long countByProduct(long establishmentId, long productId) {
        return movements.countByEstablishmentIdAndProductId(establishmentId, productId);
    }

    @Override
    public Map<Long, BigDecimal> productionOutByBatches(long establishmentId, Collection<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return Map.of();
        }
        return toMap(movements.sumProductionOutByBatches(establishmentId, MovementType.PRODUCTION_OUT, batchIds));
    }

    @Override
    public Map<Long, BigDecimal> netAdjustmentOutflow(
            long establishmentId, Instant fromInclusive, Instant toExclusive) {
        return toMap(movements.sumNetAdjustments(
                establishmentId,
                MovementType.ADJUSTMENT_OUT,
                List.of(MovementType.ADJUSTMENT_IN, MovementType.ADJUSTMENT_OUT),
                fromInclusive,
                toExclusive));
    }

    @Override
    public List<StockMovement> findByLot(long establishmentId, long lotId, MovementType type) {
        return movements.findByLot(establishmentId, lotId, type).stream()
                .map(StockMovementEntity::toDomain)
                .toList();
    }

    private static Map<Long, BigDecimal> toMap(List<Object[]> rows) {
        Map<Long, BigDecimal> values = new LinkedHashMap<>();
        for (Object[] row : rows) {
            values.put((Long) row[0], (BigDecimal) row[1]);
        }
        return values;
    }
}
