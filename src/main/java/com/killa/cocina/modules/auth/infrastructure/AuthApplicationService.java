package com.killa.cocina.modules.auth.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.auth.domain.AccessTokenIssuer;
import com.killa.cocina.modules.auth.domain.AuthService;
import com.killa.cocina.modules.auth.domain.AuthSession;
import com.killa.cocina.modules.auth.domain.RefreshToken;
import com.killa.cocina.modules.auth.domain.RefreshTokenRepository;
import com.killa.cocina.modules.auth.domain.SecuritySettings;
import com.killa.cocina.modules.auth.domain.TokenHash;
import com.killa.cocina.modules.users.domain.AppUser;
import com.killa.cocina.modules.users.domain.UserRepository;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, rotación de refresh tokens y logout (NFR-SEG-02).
 *
 * <p>El refresh token es opaco y aleatorio; en base de datos solo queda su hash. Si se presenta un
 * token ya rotado se revoca toda la familia del usuario: alguien ha reutilizado un token robado.</p>
 *
 * <p>{@code noRollbackFor = ApiException.class} es deliberado: los fallos de credenciales y la
 * detección de reutilización <strong>deben</strong> persistir lo que registran (intento fallido,
 * revocación de la familia y su auditoría) aunque la petición termine en error. Con el rollback por
 * defecto, el contador de intentos nunca subiría y el bloqueo no llegaría a activarse.</p>
 */
@Service
@Transactional(noRollbackFor = ApiException.class)
public class AuthApplicationService implements AuthService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository users;

    private final RefreshTokenRepository refreshTokens;

    private final AccessTokenIssuer accessTokens;

    private final PasswordEncoder passwordEncoder;

    private final AuditRecorder audit;

    private final SecuritySettings settings;

    private final Clock clock;

    private final SecureRandom random = new SecureRandom();

    public AuthApplicationService(UserRepository users, RefreshTokenRepository refreshTokens,
            AccessTokenIssuer accessTokens, PasswordEncoder passwordEncoder, AuditRecorder audit,
            SecuritySettings settings, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public AuthSession login(long establishmentId, String username, String password) {
        Instant now = Instant.now(clock);
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        AppUser user = users.findByUsername(establishmentId, normalized)
                .orElseThrow(AuthApplicationService::invalidCredentials);

        user = user.unlockIfExpired(now);
        if (!user.active()) {
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "La cuenta está bloqueada temporalmente por intentos fallidos.");
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            users.save(user.registerFailedLogin(now, settings.maxFailedAttempts(),
                    settings.lockDuration()));
            audit.record(establishmentId, user.id(), AuditAction.LOGIN_FAILED, "app_user", user.id(),
                    null, null);
            throw invalidCredentials();
        }

        AppUser authenticated = users.save(user.registerSuccessfulLogin(now));
        return newSession(authenticated, now).session();
    }

    @Override
    public AuthSession refresh(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        RefreshToken stored = refreshTokens.findByHash(TokenHash.of(requireToken(rawRefreshToken)))
                .orElseThrow(AuthApplicationService::invalidRefreshToken);

        if (stored.isRevoked()) {
            refreshTokens.revokeAll(stored.userId(), now);
            audit.record(stored.establishmentId(), stored.userId(),
                    AuditAction.REFRESH_TOKEN_REUSED, "refresh_token", stored.id(), null, null);
            throw invalidRefreshToken();
        }
        if (stored.isExpired(now)) {
            throw invalidRefreshToken();
        }

        AppUser user = users.findById(stored.establishmentId(), stored.userId())
                .orElseThrow(AuthApplicationService::invalidRefreshToken);
        if (!user.active()) {
            throw invalidRefreshToken();
        }

        IssuedSession issued = newSession(user, now);
        refreshTokens.save(stored.revoke(now, issued.stored().id()));
        return issued.session();
    }

    @Override
    public void logout(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        refreshTokens.findByHash(TokenHash.of(requireToken(rawRefreshToken)))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> refreshTokens.save(token.revoke(now, null)));
    }

    /**
     * Emite el par de tokens y persiste el refresh.
     *
     * <p>Devuelve también la fila persistida porque la rotación necesita su {@code id} para enlazar
     * {@code replaced_by}. Persistir dos veces el mismo token rompería la unicidad del hash.</p>
     */
    private IssuedSession newSession(AppUser user, Instant now) {
        AccessTokenIssuer.IssuedAccessToken access = accessTokens.issue(user);
        String rawRefresh = newRefreshToken();
        Instant refreshExpiresAt = now.plus(settings.refreshTtl());
        RefreshToken stored = refreshTokens.save(newToken(user.establishmentId(), user.id(),
                rawRefresh, refreshExpiresAt, now));
        return new IssuedSession(
                new AuthSession(access.token(), access.expiresAt(), rawRefresh, refreshExpiresAt),
                stored);
    }

    private record IssuedSession(AuthSession session, RefreshToken stored) {
    }

    private RefreshToken newToken(long establishmentId, long userId, String rawToken,
            Instant expiresAt, Instant now) {
        return new RefreshToken(null, establishmentId, userId, TokenHash.of(rawToken), expiresAt,
                null, null, now);
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String requireToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        return rawRefreshToken;
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Usuario o contraseña incorrectos.");
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN,
                "El refresh token no es válido o ha caducado.");
    }
}
