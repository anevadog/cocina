package com.killa.cocina.modules.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Invariantes del producto: alerta de mínimo, archivo y campos inmutables (INV-01, INV-07). */
class ProductTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    @Test
    @DisplayName("Un producto activo por debajo del mínimo está en alerta")
    void detectaStockBajoMinimo() {
        assertThat(product(new BigDecimal("1000"), new BigDecimal("999.999"), true).isLowStock())
                .isTrue();
        assertThat(product(new BigDecimal("1000"), new BigDecimal("1000"), true).isLowStock())
                .isFalse();
        assertThat(product(new BigDecimal("1000"), BigDecimal.ZERO, false).isLowStock()).isFalse();
    }

    @Test
    @DisplayName("Solo se archiva un producto con stock cero")
    void soloSeArchivaSinStock() {
        assertThat(product(BigDecimal.ZERO, BigDecimal.ZERO, true).canBeArchived()).isTrue();
        assertThat(product(BigDecimal.ZERO, new BigDecimal("0.001"), true).canBeArchived()).isFalse();
    }

    @Test
    @DisplayName("Editar no cambia unidad base, establecimiento ni fecha de creación")
    void laEdicionConservaLosCamposInmutables() {
        Product original = product(BigDecimal.ONE, BigDecimal.TEN, true);
        Instant later = NOW.plusSeconds(60);

        Product updated = original.update("Pollo limpio", 9L, new BigDecimal("2000"), true, false,
                later);

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.establishmentId()).isEqualTo(original.establishmentId());
        assertThat(updated.baseUnit()).isEqualTo(original.baseUnit());
        assertThat(updated.createdAt()).isEqualTo(original.createdAt());
        assertThat(updated.updatedAt()).isEqualTo(later);
        assertThat(updated.name()).isEqualTo("Pollo limpio");
        assertThat(updated.categoryId()).isEqualTo(9L);
        assertThat(updated.minStock()).isEqualByComparingTo("2000");
        assertThat(updated.allowNegative()).isTrue();
        assertThat(updated.active()).isFalse();
    }

    @Test
    @DisplayName("El alta normaliza el nombre y deja el mínimo a cero con tres decimales")
    void elAltaNormalizaLosDatos() {
        ProductRegistration registration = new ProductRegistration("  Pollo   entero ", 1L,
                BaseUnit.G, null, false, List.of());

        assertThat(registration.name()).isEqualTo("Pollo entero");
        assertThat(registration.minStock()).isEqualByComparingTo("0");
        assertThat(registration.minStock().scale()).isEqualTo(Quantities.STOCK_SCALE);
    }

    @Test
    @DisplayName("Un mínimo negativo se rechaza antes de tocar la base")
    void rechazaMinimoNegativo() {
        assertThatThrownBy(() -> new ProductRegistration("Pollo", 1L, BaseUnit.G,
                new BigDecimal("-1"), false, List.of()))
                .isInstanceOf(ApiException.class);
    }

    private static Product product(BigDecimal minStock, BigDecimal currentStock, boolean active) {
        return new Product(1L, 1L, 1L, "Pollo", BaseUnit.G, minStock, currentStock, BigDecimal.ZERO,
                false, active, NOW, NOW);
    }
}
