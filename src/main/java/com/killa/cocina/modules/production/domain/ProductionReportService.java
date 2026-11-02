package com.killa.cocina.modules.production.domain;

/**
 * Informes de producción: desviaciones y consumo teórico frente a real (PRD-05, ECO-03).
 *
 * <p>La implementación transaccional vive en {@code infrastructure}. Los informes son de solo
 * lectura: no escriben movimientos ni congelan nada.</p>
 */
public interface ProductionReportService {

    /** Ejecuciones terminadas en el periodo con desviación distinta de cero (PRD-05). */
    DeviationReport deviations(long establishmentId, ProductionPeriod period);

    /** Consumo teórico, producción real, merma y ajustes por producto (ECO-03). */
    ConsumptionReport consumption(long establishmentId, ProductionPeriod period);
}
