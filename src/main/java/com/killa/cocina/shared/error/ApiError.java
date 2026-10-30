package com.killa.cocina.shared.error;

import java.util.List;

/**
 * Cuerpo de error uniforme de la API (PLA-02).
 *
 * <p>Forma exacta del contrato: {@code { code, message, details, requestId }}. {@code details}
 * siempre está presente (vacío si el error no afecta a campos concretos) y {@code requestId}
 * permite correlacionar con los logs.</p>
 */
public record ApiError(String code, String message, List<FieldViolation> details, String requestId) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }

    /** Campo concreto que no cumple una regla, con su motivo en español. */
    public record FieldViolation(String field, String message) {
    }
}
