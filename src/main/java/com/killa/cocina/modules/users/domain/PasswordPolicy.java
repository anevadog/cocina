package com.killa.cocina.modules.users.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.util.List;

/**
 * Política mínima de contraseñas (NFR-SEG-01).
 *
 * <p>Se exige longitud y que la contraseña no sea el propio nombre de usuario. No se impone
 * caducidad ni composición forzada: la decisión está registrada en {@code docs/decisiones.md}
 * (ADR-016).</p>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /**
     * Valida la contraseña en claro antes de calcular el hash.
     *
     * @throws ApiException con {@link ErrorCode#WEAK_PASSWORD} si no cumple la política
     */
    public static void validate(String rawPassword, String username) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw weak("la contraseña es obligatoria");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw weak("la contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw weak("la contraseña no puede superar " + MAX_LENGTH + " caracteres");
        }
        if (username != null && rawPassword.equalsIgnoreCase(username)) {
            throw weak("la contraseña no puede coincidir con el nombre de usuario");
        }
    }

    private static ApiException weak(String message) {
        return new ApiException(ErrorCode.WEAK_PASSWORD, "La contraseña no cumple la política mínima.",
                List.of(new ApiError.FieldViolation("password", message)));
    }
}
