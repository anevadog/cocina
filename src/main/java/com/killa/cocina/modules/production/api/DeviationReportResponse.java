package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.DeviationReport;
import com.killa.cocina.modules.production.domain.ServiceType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Informe de desviaciones previsto vs real (PRD-05). */
public record DeviationReportResponse(LocalDate from, LocalDate to, List<DeviationLineResponse> deviations) {

    public static DeviationReportResponse from(DeviationReport report) {
        return new DeviationReportResponse(
                report.from(),
                report.to(),
                report.deviations().stream().map(DeviationLineResponse::from).toList());
    }

    /** Ejecución con desviación y el motivo que la explica. */
    public record DeviationLineResponse(
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
            Long finishedBy) {

        static DeviationLineResponse from(DeviationReport.DeviationLine line) {
            return new DeviationLineResponse(
                    line.planId(),
                    line.serviceDate(),
                    line.serviceType(),
                    line.planItemId(),
                    line.batchId(),
                    line.recipeId(),
                    line.recipeName(),
                    line.versionNumber(),
                    line.plannedRations(),
                    line.producedRations(),
                    line.deviation(),
                    line.deviationReason(),
                    line.finishedAt(),
                    line.finishedBy());
        }
    }
}
