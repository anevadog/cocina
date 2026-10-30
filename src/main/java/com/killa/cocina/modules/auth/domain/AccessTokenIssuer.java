package com.killa.cocina.modules.auth.domain;

import com.killa.cocina.modules.users.domain.AppUser;
import java.time.Instant;

/** Puerto de emisión del token de acceso (JWT firmado). */
public interface AccessTokenIssuer {

    /** Emite un token para el usuario y devuelve también su caducidad. */
    IssuedAccessToken issue(AppUser user);

    /** Token emitido con su instante de caducidad. */
    record IssuedAccessToken(String token, Instant expiresAt) {
    }
}
