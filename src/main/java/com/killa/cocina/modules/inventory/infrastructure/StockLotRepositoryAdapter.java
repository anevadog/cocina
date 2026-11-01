package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.ExpiringLotRow;
import com.killa.cocina.modules.inventory.domain.StockLot;
import com.killa.cocina.modules.inventory.domain.StockLotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link StockLotRepository}. */
@Repository
class StockLotRepositoryAdapter implements StockLotRepository {

    private final StockLotJpaRepository lots;

    StockLotRepositoryAdapter(StockLotJpaRepository lots) {
        this.lots = lots;
    }

    @Override
    public StockLot save(StockLot lot) {
        StockLotEntity entity = lot.id() == null
                ? StockLotEntity.from(lot)
                : lots.findById(lot.id()).orElseGet(() -> StockLotEntity.from(lot));
        if (lot.id() != null) {
            entity.apply(lot);
        }
        return lots.save(entity).toDomain();
    }

    @Override
    public Optional<StockLot> findById(long establishmentId, long lotId) {
        return lots.findByIdAndEstablishmentId(lotId, establishmentId).map(StockLotEntity::toDomain);
    }

    @Override
    public List<StockLot> findConsumable(long establishmentId, long productId) {
        return lots.findConsumable(establishmentId, productId).stream()
                .map(StockLotEntity::toDomain)
                .toList();
    }

    @Override
    public List<StockLot> findByProduct(long establishmentId, long productId, int page, int size) {
        return lots
                .findByEstablishmentIdAndProductIdOrderByReceivedAtDescIdDesc(
                        establishmentId, productId, PageRequest.of(page, size))
                .stream()
                .map(StockLotEntity::toDomain)
                .toList();
    }

    @Override
    public long countByProduct(long establishmentId, long productId) {
        return lots.countByEstablishmentIdAndProductId(establishmentId, productId);
    }

    @Override
    public List<ExpiringLotRow> findExpiring(long establishmentId, Instant until, int limit) {
        return lots.findExpiring(establishmentId, until, PageRequest.of(0, limit)).stream()
                .map(StockLotRepositoryAdapter::toRow)
                .toList();
    }

    @Override
    public long countExpiring(long establishmentId, Instant until) {
        return lots.countExpiring(establishmentId, until);
    }

    private static ExpiringLotRow toRow(Object[] row) {
        return new ExpiringLotRow(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                (String) row[2],
                (Instant) row[3],
                (BigDecimal) row[4],
                (BigDecimal) row[5]);
    }
}
