package com.killa.cocina.shared.health;

import java.util.List;

/**
 * Respuesta de {@code /ready}: agregado de las comprobaciones de preparación (PLA-04).
 */
public record ReadinessResponse(String status, List<Check> checks) {

    public ReadinessResponse {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    /** Resultado de una comprobación concreta. */
    public record Check(String name, String status) {}
}
