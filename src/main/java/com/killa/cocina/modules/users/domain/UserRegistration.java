package com.killa.cocina.modules.users.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.util.List;
import java.util.Locale;

/**
 * Datos para dar de alta un usuario (PER-01).
 *
 * <p>Normaliza y valida en el constructor: un {@code UserRegistration} que exista ya es válido.</p>
 */
public record UserRegistration(String username, String fullName, String password, Role role) {

    public UserRegistration {
        username = normalizeUsername(username);
        fullName = normalizeFullName(fullName);
        PasswordPolicy.validate(password, username);
        if (role == null) {
            throw invalid("role", "el rol es obligatorio");
        }
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw invalid("username", "el nombre de usuario es obligatorio");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,59}")) {
            throw invalid("username", "debe tener entre 3 y 60 caracteres: letras minúsculas, "
                    + "dígitos, punto, guion o guion bajo");
        }
        return normalized;
    }

    private static String normalizeFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw invalid("fullName", "el nombre completo es obligatorio");
        }
        String normalized = fullName.trim();
        if (normalized.length() > 120) {
            throw invalid("fullName", "no puede superar 120 caracteres");
        }
        return normalized;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
