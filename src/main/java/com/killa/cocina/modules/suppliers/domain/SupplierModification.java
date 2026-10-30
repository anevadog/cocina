package com.killa.cocina.modules.suppliers.domain;

import java.time.Instant;

/** Cambios parciales sobre un proveedor; un campo {@code null} no se modifica. */
public record SupplierModification(String name, String contact, Boolean active) {

    public SupplierModification {
        if (name != null) {
            name = SupplierNames.name(name);
        }
        if (contact != null) {
            contact = SupplierNames.contact(contact);
        }
    }

    public boolean isEmpty() {
        return name == null && contact == null && active == null;
    }

    public Supplier applyTo(Supplier supplier, Instant now) {
        return supplier.update(name, contact, active, now);
    }
}
