package com.killa.cocina.modules.users.infrastructure;

import com.killa.cocina.modules.users.domain.AppUser;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link UserRepository}. */
@Repository
class AppUserRepositoryAdapter implements UserRepository {

    private final AppUserJpaRepository repository;

    AppUserRepositoryAdapter(AppUserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AppUser> findById(long establishmentId, long userId) {
        return repository.findByIdAndEstablishmentId(userId, establishmentId)
                .map(AppUserEntity::toDomain);
    }

    @Override
    public Optional<AppUser> findByUsername(long establishmentId, String username) {
        return repository.findByEstablishmentIdAndUsername(establishmentId, username)
                .map(AppUserEntity::toDomain);
    }

    @Override
    public boolean existsByUsername(long establishmentId, String username) {
        return repository.existsByEstablishmentIdAndUsername(establishmentId, username);
    }

    @Override
    public AppUser save(AppUser user) {
        AppUserEntity entity = user.id() == null
                ? AppUserEntity.from(user)
                : repository.findById(user.id())
                        .orElseGet(() -> AppUserEntity.from(user));
        if (user.id() != null) {
            entity.apply(user);
        }
        return repository.save(entity).toDomain();
    }

    @Override
    public List<AppUser> findAll(long establishmentId, int page, int size) {
        return repository.findByEstablishmentIdOrderByUsernameAsc(establishmentId,
                        PageRequest.of(Math.max(page, 0), Math.max(size, 1)))
                .stream()
                .map(AppUserEntity::toDomain)
                .toList();
    }

    @Override
    public long count(long establishmentId) {
        return repository.countByEstablishmentId(establishmentId);
    }

    @Override
    public long countActiveAdmins(long establishmentId, long excludedUserId) {
        return repository.countByEstablishmentIdAndRoleAndActiveTrueAndIdNot(
                establishmentId, Role.ADMIN, excludedUserId);
    }
}
