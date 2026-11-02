package com.killa.cocina.modules.quality.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de lecturas de temperatura. */
public interface TemperatureLogRepository {

    TemperatureLog save(TemperatureLog log);

    Optional<TemperatureLog> findById(long establishmentId, long temperatureLogId);

    /** Más recientes primero. */
    List<TemperatureLog> findPage(long establishmentId, int page, int size);

    long count(long establishmentId);

    /** Lecturas fuera de rango más recientes, limitadas por el llamador. */
    List<TemperatureLog> findAlerts(long establishmentId, int limit);
}
