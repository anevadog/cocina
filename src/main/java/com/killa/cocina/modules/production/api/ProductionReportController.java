package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionPeriod;
import com.killa.cocina.modules.production.domain.ProductionReportService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informes de producción (PRD-05, ECO-03).
 *
 * <p>Ambos informes exigen un rango de fechas obligatorio de como máximo 31 días y son de solo
 * lectura. Requieren {@code reporting:read}, igual que el resto de informes.</p>
 */
@RestController
@RequestMapping("/api/v1/production-reports")
@Validated
public class ProductionReportController {

    private final ProductionReportService reportService;

    private final CurrentActor currentActor;

    public ProductionReportController(ProductionReportService reportService, CurrentActor currentActor) {
        this.reportService = reportService;
        this.currentActor = currentActor;
    }

    @GetMapping("/deviations")
    @PreAuthorize("hasAuthority('reporting:read')")
    public DeviationReportResponse deviations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CurrentActor.Actor actor = requireActor();
        ProductionPeriod period = new ProductionPeriod(from, to);
        return DeviationReportResponse.from(reportService.deviations(actor.establishmentId(), period));
    }

    @GetMapping("/consumption")
    @PreAuthorize("hasAuthority('reporting:read')")
    public ConsumptionReportResponse consumption(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CurrentActor.Actor actor = requireActor();
        ProductionPeriod period = new ProductionPeriod(from, to);
        return ConsumptionReportResponse.from(reportService.consumption(actor.establishmentId(), period));
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
