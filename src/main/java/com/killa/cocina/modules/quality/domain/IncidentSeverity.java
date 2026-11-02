package com.killa.cocina.modules.quality.domain;

/** Severidad de una incidencia de calidad (CAL-05). */
public enum IncidentSeverity {

    /** Molesta pero no afecta al servicio. */
    LOW,

    /** Requiere seguimiento. */
    MEDIUM,

    /** Afecta a la operación o a la seguridad alimentaria. */
    HIGH,

    /** Exige parada y corrección inmediata. */
    CRITICAL
}
