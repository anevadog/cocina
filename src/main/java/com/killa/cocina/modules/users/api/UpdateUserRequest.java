package com.killa.cocina.modules.users.api;

import com.killa.cocina.modules.users.domain.Role;
import jakarta.validation.constraints.Size;

/**
 * Edición parcial de usuario (PER-01). Un campo {@code null} no se modifica.
 *
 * <p>La baja es lógica: se envía {@code active: false}; no existe endpoint de borrado.</p>
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 120, message = "debe tener entre 1 y 120 caracteres")
        String fullName,

        Role role,
        Boolean active) {}
