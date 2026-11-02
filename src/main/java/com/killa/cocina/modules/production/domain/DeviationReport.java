package com.killa.cocina.modules.production.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Informe de desviaciones previsto vs real (PRD-05).
 *
 * <p>Solo incluye ejecuciones terminadas con desviación distinta de cero: cumplir lo previsto no es
 * una desviación y no necesita explicación. El motivo ya lo exige el dominio al terminar.</p>
 */
public record DeviationReport(LocalDate from, LocalDate to, List<DeviationLine> deviations) {

    public DeviationReport {
        deviations = deviations == null ? List.of() : List.copyOf(deviations);
    }

    /** Ejecución con desviación, con la receta y el plan a los que pertenece. */
    public record DeviationLine(
            long planId,
            LocalDate serviceDate,
            ServiceType serviceType,
            long planItemId,
            long batchId,
            Long recipeId,
            String recipeName,
            int versionNumber,
            int plannedRations,
            int producedRations,
            int deviation,
            String deviationReason,
            Instant finishedAt,
            Long finishedBy) {}
}
