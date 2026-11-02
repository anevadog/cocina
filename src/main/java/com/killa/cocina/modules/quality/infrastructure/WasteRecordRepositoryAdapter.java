package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.quality.domain.WasteRecord;
import com.killa.cocina.modules.quality.domain.WasteRecordRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link WasteRecordRepository}. */
@Repository
class WasteRecordRepositoryAdapter implements WasteRecordRepository {

    private final WasteRecordJpaRepository records;

    WasteRecordRepositoryAdapter(WasteRecordJpaRepository records) {
        this.records = records;
    }

    @Override
    public WasteRecord save(WasteRecord record) {
        WasteRecordEntity entity = record.id() == null
                ? WasteRecordEntity.from(record)
                : records.findById(record.id()).orElseGet(() -> WasteRecordEntity.from(record));
        if (record.id() != null) {
            entity.apply(record);
        }
        return records.save(entity).toDomain();
    }

    @Override
    public Optional<WasteRecord> findById(long establishmentId, long wasteRecordId) {
        return records.findByIdAndEstablishmentId(wasteRecordId, establishmentId)
                .map(WasteRecordEntity::toDomain);
    }

    @Override
    public List<WasteRecord> findPage(long establishmentId, int page, int size) {
        return records
                .findByEstablishmentIdOrderByOccurredAtDescIdDesc(establishmentId, PageRequest.of(page, size))
                .stream()
                .map(WasteRecordEntity::toDomain)
                .toList();
    }

    @Override
    public long count(long establishmentId) {
        return records.countByEstablishmentId(establishmentId);
    }
}
