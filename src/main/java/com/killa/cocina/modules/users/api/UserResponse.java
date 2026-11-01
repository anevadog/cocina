package com.killa.cocina.modules.users.api;

import com.killa.cocina.modules.users.domain.UserView;
import java.time.Instant;

/** Usuario expuesto por la API: nunca incluye credenciales. */
public record UserResponse(
        Long id, String username, String fullName, String role, boolean active, Instant createdAt, Instant updatedAt) {

    public static UserResponse from(UserView view) {
        return new UserResponse(
                view.id(),
                view.username(),
                view.fullName(),
                view.role().name(),
                view.active(),
                view.createdAt(),
                view.updatedAt());
    }
}
