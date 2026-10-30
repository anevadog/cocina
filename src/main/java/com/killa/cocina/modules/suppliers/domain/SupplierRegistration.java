package com.killa.cocina.modules.suppliers.domain;

/** Datos para dar de alta un proveedor. */
public record SupplierRegistration(String name, String contact) {

    public SupplierRegistration {
        name = SupplierNames.name(name);
        contact = SupplierNames.contact(contact);
    }
}
