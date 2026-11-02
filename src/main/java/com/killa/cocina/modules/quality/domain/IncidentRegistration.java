package com.killa.cocina.modules.quality.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.util.List;

/**
 * Alta de una incidencia (CAL-05).
 *
 * <p>La descripción se recorta y se normaliza; no se puede editar después de crear la incidencia
 * (ADR-040).</p>
 *
 * @param description descripción del problema, de 10 a 1000 caracteres
 * @param severity severidad inicial
 */
public record IncidentRegistration(String description, IncidentSeverity severity) {

    /** Longitud mínima de la descripción. */
    public static final int MIN_DESCRIPTION_LENGTH = 10;

    /** Longitud máxima de la descripción (coincide con varchar(1000)). */
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    public IncidentRegistration {
        description = normalizeDescription(description);
        if (severity == null) {
            throw invalid("severity", "es obligatoria");
        }
    }

    static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            throw invalid("description", "es obligatoria");
        }
        String normalized = description.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_DESCRIPTION_LENGTH || normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw invalid(
                    "description",
                    "debe tener entre " + MIN_DESCRIPTION_LENGTH + " y " + MAX_DESCRIPTION_LENGTH + " caracteres");
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
