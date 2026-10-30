package com.killa.cocina.modules.users.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Roles del establecimiento y su matriz de permisos (PER-02).
 *
 * <p>Mínimo privilegio (PER-05): un rol solo tiene los permisos que necesita para su puesto. La
 * matriz se refleja en {@code docs/arquitectura.md} y se prueba en {@code RoleMatrixTest}.</p>
 */
public enum Role {

    /** Administrador: control total, incluida la gestión de usuarios. */
    ADMIN(EnumSet.allOf(Permission.class)),

    /** Responsable de cocina: toda la operación salvo gestionar usuarios. */
    KITCHEN_MANAGER(EnumSet.complementOf(EnumSet.of(Permission.USERS_WRITE))),

    /** Cocinero: produce y registra calidad; consulta inventario y recetas. */
    COOK(EnumSet.of(
            Permission.PRODUCTION_READ,
            Permission.PRODUCTION_WRITE,
            Permission.RECIPES_READ,
            Permission.INVENTORY_READ,
            Permission.QUALITY_READ,
            Permission.QUALITY_WRITE,
            Permission.REPORTING_READ)),

    /** Almacén: entradas, ajustes y proveedores; consulta recetas y calidad. */
    STOREKEEPER(EnumSet.of(
            Permission.INVENTORY_READ,
            Permission.INVENTORY_WRITE,
            Permission.SUPPLIERS_READ,
            Permission.SUPPLIERS_WRITE,
            Permission.RECIPES_READ,
            Permission.QUALITY_READ,
            Permission.REPORTING_READ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean allows(Permission permission) {
        return permissions.contains(permission);
    }
}
