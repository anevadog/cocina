package com.killa.cocina.modules.inventory.domain;

/**
 * Tipos de movimiento del kardex (INV-06, D11/ADR-026).
 *
 * <p>El enum define todos los tipos desde F3, pero cada fase habilita los suyos:
 * {@code PRODUCTION_OUT} llega en F5 y {@code WASTE_OUT} en F6.</p>
 */
public enum MovementType {

    /** Entrada de compra con lote, proveedor y coste. */
    PURCHASE_IN,

    /** Ajuste positivo de inventario. */
    ADJUSTMENT_IN,

    /** Ajuste negativo de inventario. */
    ADJUSTMENT_OUT,

    /** Consumo por producción (F5). */
    PRODUCTION_OUT,

    /** Merma o desperdicio (F6). */
    WASTE_OUT;

    /** Una entrada suma stock; una salida lo resta. */
    public boolean isInbound() {
        return this == PURCHASE_IN || this == ADJUSTMENT_IN;
    }

    /** Solo los ajustes manuales se registran con motivo. */
    public boolean isAdjustment() {
        return this == ADJUSTMENT_IN || this == ADJUSTMENT_OUT;
    }
}
