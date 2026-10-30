package com.killa.cocina.modules.users.domain;

import com.killa.cocina.shared.pagination.PageResult;

/**
 * Casos de uso de gestión de usuarios (PER-01).
 *
 * <p>La implementación transaccional vive en {@code infrastructure}; el API solo depende de esta
 * interfaz, de modo que la capa web nunca toca entidades JPA.</p>
 */
public interface UserService {

    /** Alta de usuario. El actor queda registrado en la auditoría. */
    UserView create(long establishmentId, Long actorId, UserRegistration registration);

    /** Edición parcial o baja lógica. */
    UserView update(long establishmentId, Long actorId, long userId, UserModification modification);

    UserView find(long establishmentId, long userId);

    PageResult<UserView> list(long establishmentId, int page, int size);
}
