package com.killa.cocina.modules.reporting.api;

import com.killa.cocina.modules.reporting.domain.RecipeCostReport;
import java.util.List;

/** Histórico de costes por versión publicada (ECO-01). */
public record RecipeCostReportResponse(int recipeCount, int versionCount, List<RecipeCostLineResponse> versions) {

    public static RecipeCostReportResponse from(RecipeCostReport report) {
        return new RecipeCostReportResponse(
                report.recipeCount(),
                report.versionCount(),
                report.versions().stream().map(RecipeCostLineResponse::from).toList());
    }
}
