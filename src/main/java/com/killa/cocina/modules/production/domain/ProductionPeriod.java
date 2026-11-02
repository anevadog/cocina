package com.killa.cocina.modules.production.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Periodo de un informe de producción, en días de servicio.
 *
 * <p>Las fechas son las del negocio ({@code Europe/Madrid}): el informe de un día cubre desde las
 * 00:00 de ese día hasta las 00:00 del siguiente, con el desfase de verano aplicado por la zona
 * (`docs/arquitectura.md` §11). El rango se acota a 31 días para que una consulta no recorra años de kardex.</p>
 */
public record ProductionPeriod(LocalDate from, LocalDate to) {

    /** Días naturales máximos del informe. */
    public static final int MAX_DAYS = 31;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Madrid");

    public ProductionPeriod {
        if (from == null) {
            throw ProductionLimits.invalid("from", "es obligatorio");
        }
        if (to == null) {
            throw ProductionLimits.invalid("to", "es obligatorio");
        }
        if (to.isBefore(from)) {
            throw ProductionLimits.invalid("to", "no puede ser anterior a from");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw ProductionLimits.invalid("to", "el rango no puede superar " + MAX_DAYS + " días");
        }
    }

    /** Primer instante incluido, en UTC. */
    public Instant startInclusive() {
        return from.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    /** Primer instante excluido, en UTC. */
    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    }
}
