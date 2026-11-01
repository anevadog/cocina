package com.killa.cocina.modules.users.domain;

/**
 * Acciones autorizables del sistema (PER-03, PER-05).
 *
 * <p>El código es el que viaja como autoridad en el token y el que se usa en {@code @PreAuthorize}.
 * Añadir un permiso obliga a decidir qué roles lo tienen: la matriz está en {@link Role} y se
 * documenta en {@code docs/arquitectura.md}.</p>
 */
public enum Permission {
    USERS_READ("users:read"),
    USERS_WRITE("users:write"),

    INVENTORY_READ("inventory:read"),
    INVENTORY_WRITE("inventory:write"),

    SUPPLIERS_READ("suppliers:read"),
    SUPPLIERS_WRITE("suppliers:write"),

    RECIPES_READ("recipes:read"),
    RECIPES_WRITE("recipes:write"),

    PRODUCTION_READ("production:read"),
    PRODUCTION_WRITE("production:write"),

    QUALITY_READ("quality:read"),
    QUALITY_WRITE("quality:write"),

    REPORTING_READ("reporting:read");

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
