package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.IncidentRegistration;
import com.killa.cocina.modules.quality.domain.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta de una incidencia (CAL-05). */
public record CreateIncidentRequest(
        @NotBlank(message = "es obligatoria")
        @Size(min = 10, max = 1000, message = "debe tener entre 10 y 1000 caracteres")
        String description,

        @NotNull(message = "es obligatoria") IncidentSeverity severity) {

    public IncidentRegistration toRegistration() {
        return new IncidentRegistration(description, severity);
    }
}
