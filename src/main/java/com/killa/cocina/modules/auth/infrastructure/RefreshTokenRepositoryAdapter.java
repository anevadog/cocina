package com.killa.cocina.modules.auth.infrastructure;

import com.killa.cocina.modules.auth.domain.RefreshToken;
import com.killa.cocina.modules.auth.domain.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link RefreshTokenRepository}. */
@Repository
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;

    RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(RefreshTokenEntity::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenEntity entity = token.id() == null
                ? RefreshTokenEntity.from(token)
                : repository.findById(token.id()).orElseGet(() -> RefreshTokenEntity.from(token));
        if (token.id() != null) {
            entity.apply(token);
        }
        return repository.save(entity).toDomain();
    }

    @Override
    public int revokeAll(long userId, Instant now) {
        return repository.revokeAllForUser(userId, now);
    }
}
