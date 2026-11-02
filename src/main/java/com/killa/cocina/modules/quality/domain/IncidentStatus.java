package com.killa.cocina.modules.quality.domain;

/**
 * Estados de una incidencia (CAL-05).
 *
 * <p>Una incidencia cerrada no se reabre en el MVP: el histórico conserva el cierre original y una
 * recaída se registra como incidencia nueva (ADR-040).</p>
 */
public enum IncidentStatus {

    /** Registrada, sin trabajo iniciado. */
    OPEN,

    /** En resolución. */
    IN_PROGRESS,

    /** Resuelta y cerrada; ya no admite cambios. */
    CLOSED;

    /** Transiciones válidas. */
    public boolean canTransitionTo(IncidentStatus target) {
        return switch (this) {
            case OPEN -> target == IN_PROGRESS || target == CLOSED;
            case IN_PROGRESS -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}
