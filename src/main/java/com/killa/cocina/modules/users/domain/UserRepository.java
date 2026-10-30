package com.killa.cocina.modules.users.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de usuarios.
 *
 * <p>La implementación JPA vive en {@code infrastructure}; el dominio solo ve este contrato.</p>
 */
public interface UserRepository {

    Optional<AppUser> findById(long establishmentId, long userId);

    Optional<AppUser> findByUsername(long establishmentId, String username);

    boolean existsByUsername(long establishmentId, String username);

    AppUser save(AppUser user);

    List<AppUser> findAll(long establishmentId, int page, int size);

    long count(long establishmentId);

    /** Administradores activos distintos del usuario indicado: evita dejar el sistema sin acceso. */
    long countActiveAdmins(long establishmentId, long excludedUserId);
}
