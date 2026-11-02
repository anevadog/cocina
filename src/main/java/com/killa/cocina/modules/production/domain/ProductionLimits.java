package com.killa.cocina.modules.production.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.util.List;

/** Límites y normalización de los datos de producción (PRD-01..05). */
final class ProductionLimits {

    static final int MAX_RATIONS = 10_000;

    static final int MIN_REASON = 5;

    static final int MAX_REASON = 200;

    private ProductionLimits() {}

    static int rations(int rations, String field) {
        if (rations < 1 || rations > MAX_RATIONS) {
            throw invalid(field, "debe estar entre 1 y " + MAX_RATIONS);
        }
        return rations;
    }

    /** Motivo de desviación: vacío se normaliza a {@code null}; si existe, 5–200 caracteres. */
    static String reason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String normalized = reason.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_REASON || normalized.length() > MAX_REASON) {
            throw invalid("deviationReason", "debe tener entre " + MIN_REASON + " y " + MAX_REASON + " caracteres");
        }
        return normalized;
    }

    static ApiException invalid(String field, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
