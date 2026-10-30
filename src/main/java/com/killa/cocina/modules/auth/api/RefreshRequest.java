package com.killa.cocina.modules.auth.api;

import jakarta.validation.constraints.NotBlank;

/** Petición de rotación o de cierre de sesión. */
public record RefreshRequest(@NotBlank(message = "es obligatorio") String refreshToken) {
}
