package com.killa.cocina.modules.reporting.domain;

/**
 * Informes agregados de solo lectura (ECO-01, ECO-06).
 *
 * <p>El módulo de informes nunca escribe: compone datos publicados por recetas e inventario. La
 * implementación transaccional vive en {@code infrastructure} (ADR-020).</p>
 */
public interface ReportingService {

    /** Histórico de costes por versión publicada, con la evolución entre versiones. */
    RecipeCostReport recipeCosts(long establishmentId);

    /**
     * Lotes que caducan dentro de {@code horizonDays} con su valor en riesgo.
     *
     * @param horizonDays entre {@link ExpiryRiskReport#MIN_HORIZON_DAYS} y
     *        {@link ExpiryRiskReport#MAX_HORIZON_DAYS}
     */
    ExpiryRiskReport expiryRisk(long establishmentId, int horizonDays);
}
