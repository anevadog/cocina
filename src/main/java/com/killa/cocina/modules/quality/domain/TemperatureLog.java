package com.killa.cocina.modules.quality.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Lectura de temperatura con el rango con el que se juzgó (CAL-02).
 *
 * <p>El rango forma parte del registro y no de una configuración por equipo: una lectura pasada debe
 * conservar el criterio que se aplicó ese día. Una lectura fuera de rango es un hecho y se guarda;
 * la alerta es su consecuencia, no un motivo para rechazarla.</p>
 *
 * @param withinRange derivado de {@code valueC} y el rango, nunca recibido del cliente
 */
public record TemperatureLog(
        Long id,
        long establishmentId,
        TemperatureKind kind,
        String equipment,
        BigDecimal valueC,
        BigDecimal minC,
        BigDecimal maxC,
        boolean withinRange,
        long actorId,
        Instant recordedAt,
        Instant createdAt) {

    /** Decimales de toda temperatura (numeric(5,2)). */
    public static final int SCALE = 2;

    /** Valor absoluto máximo que cabe en numeric(5,2). */
    public static final BigDecimal MAX_ABSOLUTE = new BigDecimal("999.99");

    /** Redondeo del módulo, igual que en inventario. */
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public TemperatureLog {
        if (kind == null) {
            throw invalid("kind", "es obligatorio");
        }
        equipment = TemperatureLogRegistration.normalizeEquipment(equipment);
        valueC = normalize("valueC", valueC);
        minC = normalize("minC", minC);
        maxC = normalize("maxC", maxC);
        if (minC.compareTo(maxC) >= 0) {
            throw invalid("minC", "debe ser menor que maxC");
        }
        if (actorId <= 0) {
            throw invalid("actorId", "es obligatorio");
        }
        if (recordedAt == null) {
            throw invalid("recordedAt", "es obligatorio");
        }
        if (createdAt == null) {
            throw invalid("createdAt", "es obligatorio");
        }
    }

    /** Calcula la alerta con el rango de la propia lectura. */
    public static boolean isWithinRange(BigDecimal valueC, BigDecimal minC, BigDecimal maxC) {
        return valueC.compareTo(minC) >= 0 && valueC.compareTo(maxC) <= 0;
    }

    static BigDecimal normalize(String field, BigDecimal value) {
        if (value == null) {
            throw invalid(field, "es obligatorio");
        }
        if (value.abs().compareTo(MAX_ABSOLUTE) > 0) {
            throw invalid(field, "debe estar entre -999.99 y 999.99");
        }
        BigDecimal trimmed = value.stripTrailingZeros();
        if (trimmed.scale() > SCALE) {
            throw invalid(field, "admite como máximo 2 decimales");
        }
        return value.setScale(SCALE, ROUNDING);
    }

    static ApiException invalid(String field, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
