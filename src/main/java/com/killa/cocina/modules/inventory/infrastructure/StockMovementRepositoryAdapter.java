package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.StockMovement;
import com.killa.cocina.modules.inventory.domain.StockMovementRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
}
