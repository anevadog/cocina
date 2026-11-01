package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Producto del catálogo con su stock y coste vigentes (INV-01, INV-09).
 *
 * <p>{@code currentStock} y {@code currentAvgCost} son una desnormalización de rendimiento: la
 * fuente de verdad es el kardex ({@code stock_movements}). Un test comprueba que coinciden.</p>
 *
 * <p>Inmutable: cada cambio devuelve una instancia nueva.</p>
 */
public record Product(
        Long id,
        long establishmentId,
        long categoryId,
        String name,
        BaseUnit baseUnit,
        BigDecimal minStock,
        BigDecimal currentStock,
        BigDecimal currentAvgCost,
        boolean allowNegative,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    /** INV-07: un producto activo por debajo de su mínimo está en alerta. */
    public boolean isLowStock() {
        return active && currentStock.compareTo(minStock) < 0;
    }

    /** Aplica el resultado de un movimiento: nuevo saldo y, si procede, nuevo PMP. */
    public Product withStock(BigDecimal stock, BigDecimal avgCost, Instant now) {
        return new Product(
                id,
                establishmentId,
                categoryId,
                name,
                baseUnit,
                minStock,
                stock == null ? currentStock : stock,
                avgCost == null ? currentAvgCost : avgCost,
                allowNegative,
                active,
                createdAt,
                now);
    }

    /** La baja es lógica; el stock debe estar a cero para archivarlo (INV-01). */
    public boolean canBeArchived() {
        return currentStock.signum() == 0;
    }

    public Product update(
            String name, Long categoryId, BigDecimal minStock, Boolean allowNegative, Boolean active, Instant now) {
        return new Product(
                id,
                establishmentId,
                categoryId == null ? this.categoryId : categoryId,
                name == null ? this.name : name,
                baseUnit,
                minStock == null ? this.minStock : minStock,
                currentStock,
                currentAvgCost,
                allowNegative == null ? this.allowNegative : allowNegative,
                active == null ? this.active : active,
                createdAt,
                now);
    }
}
