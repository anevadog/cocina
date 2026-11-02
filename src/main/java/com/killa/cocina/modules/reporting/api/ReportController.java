package com.killa.cocina.modules.reporting.api;

import com.killa.cocina.modules.reporting.domain.ExpiryRiskReport;
import com.killa.cocina.modules.reporting.domain.ReportingService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informes agregados de solo lectura (ECO-01, ECO-06).
 *
 * <p>Ambos exigen {@code reporting:read}. El de costes devuelve el histórico completo de versiones
 * publicadas; el de caducidades acota el horizonte entre 1 y 90 días.</p>
 */
@RestController
@RequestMapping("/api/v1/reports")
@Validated
public class ReportController {

    private final ReportingService reporting;

    private final CurrentActor currentActor;

    public ReportController(ReportingService reporting, CurrentActor currentActor) {
        this.reporting = reporting;
        this.currentActor = currentActor;
    }

    @GetMapping("/recipe-costs")
    @PreAuthorize("hasAuthority('reporting:read')")
    public RecipeCostReportResponse recipeCosts() {
        CurrentActor.Actor actor = requireActor();
        return RecipeCostReportResponse.from(reporting.recipeCosts(actor.establishmentId()));
    }

    @GetMapping("/expiring-lots")
    @PreAuthorize("hasAuthority('reporting:read')")
    public ExpiryRiskReportResponse expiringLots(
            @RequestParam(defaultValue = "7")
                    @Min(value = ExpiryRiskReport.MIN_HORIZON_DAYS, message = "debe ser al menos 1")
                    @Max(value = ExpiryRiskReport.MAX_HORIZON_DAYS, message = "no puede superar 90")
                    int days) {
        CurrentActor.Actor actor = requireActor();
        return ExpiryRiskReportResponse.from(reporting.expiryRisk(actor.establishmentId(), days));
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
