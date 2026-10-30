package com.killa.cocina.modules.users.api;

import com.killa.cocina.modules.users.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de usuario (PER-01).
 *
 * <p>La política de contraseña se valida además en el dominio
 * ({@link com.killa.cocina.modules.users.domain.PasswordPolicy}): aquí solo se comprueba la forma.</p>
 */
public record CreateUserRequest(
        @NotBlank(message = "es obligatorio")
        @Size(min = 3, max = 60, message = "debe tener entre 3 y 60 caracteres")
        String username,

        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String fullName,

        @NotBlank(message = "es obligatorio")
        @Size(min = 12, max = 128, message = "debe tener entre 12 y 128 caracteres")
        String password,

        @NotNull(message = "es obligatorio")
        Role role) {
}
