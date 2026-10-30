package com.killa.cocina.modules.users.infrastructure;

import com.killa.cocina.modules.users.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de usuarios. */
interface AppUserJpaRepository extends JpaRepository<AppUserEntity, Long> {

    Optional<AppUserEntity> findByEstablishmentIdAndUsername(long establishmentId, String username);

    Optional<AppUserEntity> findByIdAndEstablishmentId(long id, long establishmentId);

    boolean existsByEstablishmentIdAndUsername(long establishmentId, String username);

    List<AppUserEntity> findByEstablishmentIdOrderByUsernameAsc(long establishmentId, Pageable pageable);

    long countByEstablishmentId(long establishmentId);

    long countByEstablishmentIdAndRoleAndActiveTrueAndIdNot(long establishmentId, Role role,
            long excludedId);
}
