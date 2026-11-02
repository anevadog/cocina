package com.killa.cocina.modules.production.domain;

import com.killa.cocina.shared.pagination.PageResult;
import java.time.LocalDate;
import java.util.List;

/**
 * Casos de uso de producción (PRD-01..05, REC-06, ECO-03).
 *
 * <p>La implementación transaccional vive en {@code infrastructure}; el API solo ve esta interfaz
 * (ADR-020). El descuento de stock se delega en el puerto de inventario, nunca en sus entidades.</p>
 */
public interface ProductionService {

    /** Crea el plan de una fecha y servicio; no admite duplicados (PRD-01). */
    ProductionPlanView create(long establishmentId, Long actorId, ProductionPlanRegistration registration);

    ProductionPlanView find(long establishmentId, long planId);

    PageResult<ProductionPlanView> list(long establishmentId, LocalDate serviceDate, int page, int size);

    /** Cancela un plan que aún no ha empezado; no descuenta stock. */
    ProductionPlanView cancel(long establishmentId, Long actorId, long planId);

    /** Borra un plan que aún no ha empezado. */
    void delete(long establishmentId, Long actorId, long planId);

    /** Sustituye los ítems de un plan en {@code PENDING} por versiones publicadas (PRD-01). */
    ProductionPlanView replaceItems(
            long establishmentId, Long actorId, long planId, List<ProductionItemRegistration> items);

    /** Quita un ítem que todavía no tiene batch. */
    ProductionPlanView deleteItem(long establishmentId, Long actorId, long planId, long itemId);

    /** Necesidades agregadas por producto, con merma y un único redondeo (PRD-02). */
    ProductionRequirements requirements(long establishmentId, long planId);
}
