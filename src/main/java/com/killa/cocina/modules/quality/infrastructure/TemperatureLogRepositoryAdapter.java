package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.quality.domain.TemperatureLog;
import com.killa.cocina.modules.quality.domain.TemperatureLogRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link TemperatureLogRepository}. */
@Repository
class TemperatureLogRepositoryAdapter implements TemperatureLogRepository {

    private final TemperatureLogJpaRepository logs;

    TemperatureLogRepositoryAdapter(TemperatureLogJpaRepository logs) {
        this.logs = logs;
    }

    @Override
    public TemperatureLog save(TemperatureLog log) {
        TemperatureLogEntity entity = log.id() == null
                ? TemperatureLogEntity.from(log)
                : logs.findById(log.id()).orElseGet(() -> TemperatureLogEntity.from(log));
        if (log.id() != null) {
            entity.apply(log);
        }
        return logs.save(entity).toDomain();
    }

    @Override
    public Optional<TemperatureLog> findById(long establishmentId, long temperatureLogId) {
        return logs.findByIdAndEstablishmentId(temperatureLogId, establishmentId)
                .map(TemperatureLogEntity::toDomain);
    }

    @Override
    public List<TemperatureLog> findPage(long establishmentId, int page, int size) {
        return logs
                .findByEstablishmentIdOrderByRecordedAtDescIdDesc(establishmentId, PageRequest.of(page, size))
                .stream()
                .map(TemperatureLogEntity::toDomain)
                .toList();
    }

    @Override
    public long count(long establishmentId) {
        return logs.countByEstablishmentId(establishmentId);
    }

    @Override
    public List<TemperatureLog> findAlerts(long establishmentId, int limit) {
        return logs
                .findByEstablishmentIdAndWithinRangeFalseOrderByRecordedAtDescIdDesc(
                        establishmentId, PageRequest.of(0, limit))
                .stream()
                .map(TemperatureLogEntity::toDomain)
                .toList();
    }
}
