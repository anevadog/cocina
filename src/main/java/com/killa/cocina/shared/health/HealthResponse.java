package com.killa.cocina.shared.health;

import java.time.Instant;

/**
 * Respuesta de {@code /health}: estado del proceso, sin tocar dependencias externas (PLA-04).
 */
public record HealthResponse(String status, String service, Instant timestamp) {
}
