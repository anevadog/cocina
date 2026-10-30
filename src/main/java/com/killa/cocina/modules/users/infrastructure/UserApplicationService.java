package com.killa.cocina.modules.users.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.users.domain.AppUser;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserModification;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserRepository;
import com.killa.cocina.modules.users.domain.UserService;
import com.killa.cocina.modules.users.domain.UserView;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.pagination.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Casos de uso de usuarios (PER-01, PER-04).
 *
 * <p>Vive en {@code infrastructure} porque necesita transacción, codificador de contraseñas y
 * serialización; el API solo ve la interfaz {@link UserService}.</p>
 */
@Service
@Transactional
public class UserApplicationService implements UserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository users;

    private final PasswordEncoder passwordEncoder;

    private final AuditRecorder audit;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    public UserApplicationService(UserRepository users, PasswordEncoder passwordEncoder,
            AuditRecorder audit, ObjectMapper objectMapper, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public UserView create(long establishmentId, Long actorId, UserRegistration registration) {
        if (users.existsByUsername(establishmentId, registration.username())) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN,
                    "El nombre de usuario ya está en uso en este establecimiento.");
        }
        Instant now = Instant.now(clock);
        AppUser saved = users.save(new AppUser(null, establishmentId, registration.username(),
                registration.fullName(), passwordEncoder.encode(registration.password()),
                registration.role(), true, 0, null, now, now));
        audit.record(establishmentId, actorId, AuditAction.USER_CREATED, "app_user", saved.id(),
                null, snapshot(saved));
        return UserView.from(saved);
    }

    @Override
    public UserView update(long establishmentId, Long actorId, long userId,
            UserModification modification) {
        AppUser current = users.findById(establishmentId, userId)
                .orElseThrow(() -> notFound(userId));
        if (modification.isEmpty()) {
            return UserView.from(current);
        }
        AppUser updated = modification.applyTo(current, Instant.now(clock));
        ensureAtLeastOneActiveAdmin(current, updated);
        AppUser saved = users.save(updated);

        audit.record(establishmentId, actorId, actionFor(current, saved), "app_user", saved.id(),
                snapshot(current), snapshot(saved));
        return UserView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserView find(long establishmentId, long userId) {
        return users.findById(establishmentId, userId).map(UserView::from)
                .orElseThrow(() -> notFound(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserView> list(long establishmentId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<UserView> items = users.findAll(establishmentId, safePage, safeSize).stream()
                .map(UserView::from)
                .toList();
        return new PageResult<>(items, users.count(establishmentId), safePage, safeSize);
    }

    /** Un sistema sin administradores activos queda bloqueado sin intervención manual en la base. */
    private void ensureAtLeastOneActiveAdmin(AppUser before, AppUser after) {
        boolean wasAdmin = before.role() == Role.ADMIN && before.active();
        boolean staysAdmin = after.role() == Role.ADMIN && after.active();
        if (wasAdmin && !staysAdmin && users.countActiveAdmins(before.establishmentId(), before.id()) == 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "La operación dejaría el establecimiento sin administradores activos.",
                    List.of(new com.killa.cocina.shared.error.ApiError.FieldViolation(
                            "role", "debe existir al menos un administrador activo")));
        }
    }

    private static AuditAction actionFor(AppUser before, AppUser after) {
        if (before.role() != after.role()) {
            return AuditAction.USER_ROLE_CHANGED;
        }
        if (before.active() && !after.active()) {
            return AuditAction.USER_DEACTIVATED;
        }
        return AuditAction.USER_UPDATED;
    }

    private String snapshot(AppUser user) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("username", user.username());
        values.put("fullName", user.fullName());
        values.put("role", user.role().name());
        values.put("active", user.active());
        return objectMapper.writeValueAsString(values);
    }

    private static ApiException notFound(long userId) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                "El usuario " + userId + " no existe.");
    }
}
