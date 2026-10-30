package com.killa.cocina.modules.auth.infrastructure;

import com.killa.cocina.config.JwtProperties;
import com.killa.cocina.modules.auth.domain.AccessTokenIssuer;
import com.killa.cocina.modules.users.domain.AppUser;
import com.killa.cocina.modules.users.domain.Permission;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Emisión de tokens de acceso JWT firmados con HS256 y vida corta (NFR-SEG-02). */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;

    private final Clock clock;

    private final JwtProperties properties;

    public JwtAccessTokenIssuer(JwtEncoder encoder, Clock clock, JwtProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public IssuedAccessToken issue(AppUser user) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(properties.accessTtl());
        List<String> authorities = user.role().permissions().stream()
                .map(Permission::code)
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cocina-api")
                .subject(String.valueOf(user.id()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("username", user.username())
                .claim("establishment_id", user.establishmentId())
                .claim("role", user.role().name())
                .claim("authorities", authorities)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }
}
