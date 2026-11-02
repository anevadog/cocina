package com.killa.cocina.modules.quality.domain;

import java.util.List;

/**
 * Puerto publicado por calidad hacia producción para reconstruir el consumo de un lote (CAL-01).
 *
 * <p>Calidad no accede a las entidades de producción: este contrato traduce el kardex y los puertos
 * publicados de producción a filas de trazabilidad (ADR-040).</p>
 */
public interface ProductionTraceCatalog {

    /** Consumos de producción del lote, del más antiguo al más reciente. */
    List<ProductionConsumptionTrace> consumptionOfLot(long establishmentId, long lotId);
}
