package com.killa.cocina.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Política de bloqueo por intentos fallidos (NFR-SEG-01).
 *
 * @param maxFailedAttempts intentos antes de bloquear la cuenta
 * @param lockDuration duración del bloqueo temporal
 */
@ConfigurationProperties(prefix = "cocina.security.login")
public record LoginProperties(int maxFailedAttempts, Duration lockDuration) {}
