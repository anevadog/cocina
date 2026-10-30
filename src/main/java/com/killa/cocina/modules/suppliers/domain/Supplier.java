package com.killa.cocina.modules.suppliers.domain;

import java.time.Instant;

/**
 * Proveedor del establecimiento.
 *
 * <p>Nunca se borra: se desactiva con {@code active=false} para no romper el histórico de lotes.</p>
 */
public record Supplier(Long id, long establishmentId, String name, String contact, boolean active,
        Instant createdAt, Instant updatedAt) {

    public Supplier update(String name, String contact, Boolean active, Instant now) {
        return new Supplier(id, establishmentId, name == null ? this.name : name,
                contact == null ? this.contact : contact,
                active == null ? this.active : active, createdAt, now);
    }
}
