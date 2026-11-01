package com.killa.cocina.modules.suppliers.api;

import com.killa.cocina.modules.suppliers.domain.SupplierView;
import java.time.Instant;

/** Proveedor expuesto por la API. */
public record SupplierResponse(
        Long id, String name, String contact, boolean active, Instant createdAt, Instant updatedAt) {

    public static SupplierResponse from(SupplierView view) {
        return new SupplierResponse(
                view.id(), view.name(), view.contact(), view.active(), view.createdAt(), view.updatedAt());
    }
}
