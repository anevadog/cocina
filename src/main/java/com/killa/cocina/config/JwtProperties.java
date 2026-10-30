package com.killa.cocina.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de los tokens de acceso.
 *
 * @param secret secreto HS256 compartido; obligatorio y de al menos 32 bytes
 * @param accessTtl vida del token de acceso (máximo 15 minutos por NFR-SEG-02)
 * @param refreshTtl vida del refresh token
 */
@ConfigurationProperties(prefix = "cocina.security.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
