package com.killa.cocina.modules.reporting.infrastructure;

import com.killa.cocina.modules.reporting.domain.ExpiryRiskCatalog;
import com.killa.cocina.modules.reporting.domain.ExpiryRiskReport;
import com.killa.cocina.modules.reporting.domain.RecipeCostCatalog;
import com.killa.cocina.modules.reporting.domain.RecipeCostReport;
import com.killa.cocina.modules.reporting.domain.ReportingService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementación de solo lectura de los informes agregados (ECO-01, ECO-06). */
@Service
@Transactional(readOnly = true)
public class ReportingApplicationService implements ReportingService {

    private final RecipeCostCatalog recipeCosts;

    private final ExpiryRiskCatalog expiryRisk;

    private final Clock clock;

    ReportingApplicationService(RecipeCostCatalog recipeCosts, ExpiryRiskCatalog expiryRisk, Clock clock) {
        this.recipeCosts = recipeCosts;
        this.expiryRisk = expiryRisk;
        this.clock = clock;
    }

    @Override
    public RecipeCostReport recipeCosts(long establishmentId) {
        return RecipeCostReport.from(recipeCosts.publishedVersions(establishmentId));
    }

    @Override
    public ExpiryRiskReport expiryRisk(long establishmentId, int horizonDays) {
        Instant now = Instant.now(clock);
        Instant until = now.plus(Duration.ofDays(horizonDays));
        return ExpiryRiskReport.from(
                horizonDays,
                now,
                expiryRisk.expiringLots(establishmentId, until, ExpiryRiskReport.MAX_LOTS),
                expiryRisk.countExpiring(establishmentId, until),
                expiryRisk.valueExpiring(establishmentId, until));
    }
}
