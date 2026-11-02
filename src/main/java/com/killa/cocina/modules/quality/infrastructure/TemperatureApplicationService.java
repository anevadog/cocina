package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.quality.domain.TemperatureLog;
import com.killa.cocina.modules.quality.domain.TemperatureLogRegistration;
import com.killa.cocina.modules.quality.domain.TemperatureLogRepository;
import com.killa.cocina.modules.quality.domain.TemperatureLogView;
import com.killa.cocina.modules.quality.domain.TemperatureService;
import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.pagination.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Casos de uso de temperaturas (CAL-02).
 *
 * <p>Vive en {@code infrastructure} porque necesita transacción y serialización (ADR-020). La
 * lectura fuera de rango no se rechaza: se guarda con {@code withinRange=false} y aparece en las
 * alertas con actor y hora.</p>
 */
@Service
@Transactional
public class TemperatureApplicationService implements TemperatureService {

    private static final int MAX_PAGE_SIZE = 100;

    /** Límite del contrato de alertas: las 100 lecturas fuera de rango más recientes. */
    static final int MAX_ALERTS = 100;

    private final TemperatureLogRepository logs;

    private final AuditRecorder audit;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    public TemperatureApplicationService(
            TemperatureLogRepository logs, AuditRecorder audit, ObjectMapper objectMapper, Clock clock) {
        this.logs = logs;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public TemperatureLogView register(long establishmentId, Long actorId, TemperatureLogRegistration registration) {
        long actor = requireActor(actorId);
        Instant now = Instant.now(clock);
        Instant recordedAt = registration.recordedAt() == null ? now : registration.recordedAt();
        if (recordedAt.isAfter(now)) {
            throw invalid("recordedAt", "no puede ser futura");
        }

        TemperatureLog log = new TemperatureLog(
                null,
                establishmentId,
                registration.kind(),
                registration.equipment(),
                registration.valueC(),
                registration.minC(),
                registration.maxC(),
                TemperatureLog.isWithinRange(registration.valueC(), registration.minC(), registration.maxC()),
                actor,
                recordedAt,
                now);
        TemperatureLog saved = logs.save(log);
        audit.record(
                establishmentId,
                actor,
                AuditAction.TEMPERATURE_LOGGED,
                "temperature_log",
                saved.id(),
                null,
                snapshot(saved));
        return TemperatureLogView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TemperatureLogView find(long establishmentId, long temperatureLogId) {
        return TemperatureLogView.from(requireLog(establishmentId, temperatureLogId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TemperatureLogView> list(long establishmentId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<TemperatureLogView> items = logs.findPage(establishmentId, safePage, safeSize).stream()
                .map(TemperatureLogView::from)
                .toList();
        return new PageResult<>(items, logs.count(establishmentId), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemperatureLogView> alerts(long establishmentId) {
        return logs.findAlerts(establishmentId, MAX_ALERTS).stream()
                .map(TemperatureLogView::from)
                .toList();
    }

    private TemperatureLog requireLog(long establishmentId, long temperatureLogId) {
        return logs.findById(establishmentId, temperatureLogId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND, "La lectura de temperatura " + temperatureLogId + " no existe."));
    }

    private static long requireActor(Long actorId) {
        if (actorId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "El registro de temperaturas exige un usuario autenticado.");
        }
        return actorId;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }

    private String snapshot(TemperatureLog log) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("kind", log.kind().name());
        values.put("equipment", log.equipment());
        values.put("valueC", log.valueC().toPlainString());
        values.put("minC", log.minC().toPlainString());
        values.put("maxC", log.maxC().toPlainString());
        values.put("withinRange", log.withinRange());
        values.put("recordedAt", log.recordedAt().toString());
        return objectMapper.writeValueAsString(values);
    }
}
