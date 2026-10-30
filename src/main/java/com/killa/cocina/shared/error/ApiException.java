package com.killa.cocina.shared.error;

import java.util.List;

/**
 * Fallo de negocio con un {@link ErrorCode} estable y, opcionalmente, detalle por campo.
 *
 * <p>Es Java puro a propósito: las capas de dominio y aplicación pueden lanzarlo sin depender de
 * Spring ni de Jakarta. {@code GlobalExceptionHandler} lo traduce al cuerpo de error uniforme
 * (PLA-02).</p>
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    private final transient List<ApiError.FieldViolation> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<ApiError.FieldViolation> details) {
        super(message);
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ApiError.FieldViolation> details() {
        return details;
    }
}
