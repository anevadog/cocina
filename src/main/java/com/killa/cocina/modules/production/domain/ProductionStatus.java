package com.killa.cocina.modules.production.domain;

/**
 * Estados de un plan y de su ejecución (PRD-04).
 *
 * <p>El plan usa todos los estados; el batch empieza en {@code IN_PROGRESS} y solo avanza por las
 * transiciones permitidas. Cancelar antes de terminar no descuenta stock; una vez terminado, el
 * consumo ya está en el kardex y se corrige con un ajuste, no con otra transición.</p>
 */
public enum ProductionStatus {

    /** Planificado, aún sin ejecución. */
    PENDING,

    /** En preparación. */
    IN_PROGRESS,

    /** Terminado: el stock ya está descontado. */
    READY,

    /** Servido. */
    SERVED,

    /** Cancelado sin descuento de stock. */
    CANCELLED;

    /** Transiciones válidas de un batch. */
    public boolean canTransitionTo(ProductionStatus target) {
        return switch (this) {
            case IN_PROGRESS -> target == READY || target == CANCELLED;
            case READY -> target == SERVED;
            default -> false;
        };
    }
}
