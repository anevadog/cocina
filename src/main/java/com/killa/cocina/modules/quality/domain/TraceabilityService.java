package com.killa.cocina.modules.quality.domain;

/** Casos de uso de trazabilidad de lotes (CAL-01). */
public interface TraceabilityService {

    /** Traza un lote desde su entrada hasta las preparaciones que lo consumieron. */
    LotTraceabilityView traceLot(long establishmentId, long lotId);
}
