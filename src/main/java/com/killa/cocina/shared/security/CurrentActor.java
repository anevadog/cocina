package com.killa.cocina.shared.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Identidad del usuario autenticado en la petición actual (PER-04).
 *
 * <p>Los datos se leen del token: {@code sub} (identificador) y {@code establishment_id}. Así
 * ninguna operación de negocio puede atribuirse a un usuario distinto del que firma la petición.</p>
 */
@Component
public class CurrentActor {

    /** Usuario autenticado y establecimiento al que pertenece. */
    public record Actor(long userId, long establishmentId) {}

    public Optional<Actor> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token
                && token.getToken().getSubject() != null) {
            Object establishment = token.getToken().getClaim("establishment_id");
            if (establishment instanceof Number number) {
                return Optional.of(new Actor(Long.parseLong(token.getToken().getSubject()), number.longValue()));
            }
        }
        return Optional.empty();
    }
}
