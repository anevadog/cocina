package com.killa.cocina.modules.users.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.time.Instant;
import java.util.List;

/**
 * Cambios parciales sobre un usuario (PER-01). Un campo {@code null} significa «no lo toques».
 *
 * <p>La baja es lógica: se cambia {@code active}. Nunca se borra un usuario (PER-01).</p>
 */
public record UserModification(String fullName, Role role, Boolean active) {

    public UserModification {
        if (fullName != null) {
            fullName = fullName.trim();
            if (fullName.isEmpty() || fullName.length() > 120) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "La petición contiene datos no válidos.",
                        List.of(new ApiError.FieldViolation("fullName",
                                "debe tener entre 1 y 120 caracteres")));
            }
        }
    }

    public boolean isEmpty() {
        return fullName == null && role == null && active == null;
    }

    /** Aplica los cambios presentes y devuelve el usuario resultante. */
    public AppUser applyTo(AppUser user, Instant now) {
        return user.update(
                fullName == null ? user.fullName() : fullName,
                role == null ? user.role() : role,
                active == null ? user.active() : active,
                now);
    }
}
