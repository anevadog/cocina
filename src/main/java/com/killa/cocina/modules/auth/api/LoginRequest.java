package com.killa.cocina.modules.auth.api;

import jakarta.validation.constraints.NotBlank;

/** Credenciales de acceso. */
public record LoginRequest(
        @NotBlank(message = "es obligatorio") String username,
        @NotBlank(message = "es obligatorio") String password) {}
