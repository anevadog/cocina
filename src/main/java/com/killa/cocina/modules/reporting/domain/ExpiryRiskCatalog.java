package com.killa.cocina.modules.reporting.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Puerto de lectura de lotes próximos a caducar, implementado sobre inventario (ECO-06). */
public interface ExpiryRiskCatalog {

    /** Lotes con saldo que caducan antes de {@code until}, en orden de caducidad, hasta {@code limit}. */
    List<ExpiringLotSnapshot> expiringLots(long establishmentId, Instant until, int limit);

    /** Número real de lotes en riesgo, aunque el listado se trunque. */
    long countExpiring(long establishmentId, Instant until);

    /** Valor total al coste del lote de todo lo que caduca antes de {@code until}. */
    BigDecimal valueExpiring(long establishmentId, Instant until);
}
