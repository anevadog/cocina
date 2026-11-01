package com.killa.cocina.modules.suppliers.domain;

import java.time.Instant;

/** Proveedor expuesto por la API. */
public record SupplierView(Long id, String name, String contact, boolean active, Instant createdAt, Instant updatedAt) {

    public static SupplierView from(Supplier supplier) {
        return new SupplierView(
                supplier.id(),
                supplier.name(),
                supplier.contact(),
                supplier.active(),
                supplier.createdAt(),
                supplier.updatedAt());
    }
}
