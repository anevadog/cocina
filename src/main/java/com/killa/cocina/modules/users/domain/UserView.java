package com.killa.cocina.modules.users.domain;

import java.time.Instant;

/** Vista de un usuario para la API: nunca incluye el hash de la contraseña. */
public record UserView(Long id, String username, String fullName, Role role, boolean active,
        Instant createdAt, Instant updatedAt) {

    public static UserView from(AppUser user) {
        return new UserView(user.id(), user.username(), user.fullName(), user.role(), user.active(),
                user.createdAt(), user.updatedAt());
    }
}
