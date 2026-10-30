package com.killa.cocina.modules.auth.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de refresh tokens. */
interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
            update RefreshTokenEntity token
               set token.revokedAt = :now
             where token.userId = :userId
               and token.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") long userId, @Param("now") java.time.Instant now);
}
