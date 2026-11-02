package com.killa.cocina.modules.quality.domain;

import com.killa.cocina.shared.pagination.PageResult;
import java.util.List;

/**
 * Casos de uso de temperaturas (CAL-02).
 *
 * <p>La implementación transaccional vive en {@code infrastructure} (ADR-020).</p>
 */
public interface TemperatureService {

    /** Registra una lectura; una lectura fuera de rango también se guarda y genera alerta. */
    TemperatureLogView register(long establishmentId, Long actorId, TemperatureLogRegistration registration);

    TemperatureLogView find(long establishmentId, long temperatureLogId);

    PageResult<TemperatureLogView> list(long establishmentId, int page, int size);

    /** Lecturas fuera de rango más recientes, con el límite del contrato. */
    List<TemperatureLogView> alerts(long establishmentId);
}
