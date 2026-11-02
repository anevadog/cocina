package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cambio de estado de una incidencia (CAL-05).
 *
 * <p>La resolución solo se admite al pasar a {@code CLOSED}; en ese caso es obligatoria.</p>
 */
public record UpdateIncidentRequest(
        @NotNull(message = "es obligatorio") IncidentStatus status,

        @Size(min = 5, max = 500, message = "debe tener entre 5 y 500 caracteres")
        String resolution) {}
