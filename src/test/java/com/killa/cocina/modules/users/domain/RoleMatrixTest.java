package com.killa.cocina.modules.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Matriz de permisos por rol (PER-02, PER-05).
 *
 * <p>Este test es el contrato: si alguien amplía un rol sin decidirlo, aquí falla.</p>
 */
class RoleMatrixTest {

    @Test
    @DisplayName("ADMIN tiene todos los permisos, incluidas las dos acciones de usuarios")
    void adminTieneTodosLosPermisos() {
        assertThat(Role.ADMIN.permissions())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
        assertThat(Role.ADMIN.allows(Permission.USERS_READ)).isTrue();
        assertThat(Role.ADMIN.allows(Permission.USERS_WRITE)).isTrue();
    }

    @Test
    @DisplayName("KITCHEN_MANAGER opera todo salvo la gestión de usuarios")
    void responsableDeCocinaNoGestionaUsuarios() {
        assertThat(Role.KITCHEN_MANAGER.allows(Permission.USERS_WRITE)).isFalse();
        assertThat(Role.KITCHEN_MANAGER.allows(Permission.INVENTORY_WRITE)).isTrue();
        assertThat(Role.KITCHEN_MANAGER.allows(Permission.RECIPES_WRITE)).isTrue();
        assertThat(Role.KITCHEN_MANAGER.allows(Permission.PRODUCTION_WRITE)).isTrue();
        assertThat(Role.KITCHEN_MANAGER.allows(Permission.QUALITY_WRITE)).isTrue();
    }

    @Test
    @DisplayName("COOK no ajusta stock, no gestiona usuarios ni proveedores y no publica recetas")
    void cocineroSoloProduceYRegistraCalidad() {
        assertThat(Role.COOK.allows(Permission.INVENTORY_WRITE)).isFalse();
        assertThat(Role.COOK.allows(Permission.USERS_WRITE)).isFalse();
        assertThat(Role.COOK.allows(Permission.USERS_READ)).isFalse();
        assertThat(Role.COOK.allows(Permission.SUPPLIERS_WRITE)).isFalse();
        assertThat(Role.COOK.allows(Permission.RECIPES_WRITE)).isFalse();
        assertThat(Role.COOK.allows(Permission.PRODUCTION_WRITE)).isTrue();
        assertThat(Role.COOK.allows(Permission.QUALITY_WRITE)).isTrue();
    }

    @Test
    @DisplayName("STOREKEEPER mueve inventario y proveedores, pero no produce ni publica recetas")
    void almacenGestionaInventarioYProveedores() {
        assertThat(Role.STOREKEEPER.allows(Permission.INVENTORY_WRITE)).isTrue();
        assertThat(Role.STOREKEEPER.allows(Permission.SUPPLIERS_WRITE)).isTrue();
        assertThat(Role.STOREKEEPER.allows(Permission.PRODUCTION_WRITE)).isFalse();
        assertThat(Role.STOREKEEPER.allows(Permission.RECIPES_WRITE)).isFalse();
        assertThat(Role.STOREKEEPER.allows(Permission.USERS_WRITE)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Todo rol tiene al menos un permiso y ninguno queda vacío")
    void ningunRolSeQuedaSinPermisos(Role role) {
        assertThat(role.permissions()).isNotEmpty();
    }

    @Test
    @DisplayName("Solo ADMIN puede escribir usuarios: es la frontera que protege el resto del sistema")
    void soloAdminGestionaUsuarios() {
        assertThat(EnumSet.allOf(Role.class)).filteredOn(role -> role.allows(Permission.USERS_WRITE))
                .containsExactly(Role.ADMIN);
    }
}
