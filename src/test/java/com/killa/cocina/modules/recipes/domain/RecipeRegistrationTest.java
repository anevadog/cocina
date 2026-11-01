package com.killa.cocina.modules.recipes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reglas de alta y edición de recetas, sin Spring ni base de datos (REC-01, REC-02).
 */
class RecipeRegistrationTest {

    @Test
    @DisplayName("Normaliza textos y aplica rendimiento y tiempo por defecto")
    void normalizaTextosYAplicaValoresPorDefecto() {
        RecipeRegistration registration =
                new RecipeRegistration("  Tarta   de queso ", "  Postres  ", 8, null, null, "   ");

        assertThat(registration.name()).isEqualTo("Tarta de queso");
        assertThat(registration.category()).isEqualTo("Postres");
        assertThat(registration.baseRations()).isEqualTo(8);
        assertThat(registration.yieldPct()).isEqualByComparingTo("100.00");
        assertThat(registration.prepTimeMinutes()).isNull();
        assertThat(registration.instructions()).isNull();
    }

    @Test
    @DisplayName("Rechaza raciones, rendimiento, tiempo y textos fuera de rango")
    void rechazaValoresFueraDeRango() {
        assertThatThrownBy(() -> new RecipeRegistration("Tarta", null, 0, null, null, null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new RecipeRegistration("Tarta", null, 10_001, null, null, null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new RecipeRegistration("Tarta", null, 4, new BigDecimal("100.01"), null, null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new RecipeRegistration("Tarta", null, 4, null, 1_441, null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new RecipeRegistration("Tarta", null, 4, null, null, "x".repeat(4_001)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new RecipeRegistration("T", null, 4, null, null, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("La merma se normaliza a dos decimales y se acota al 99,99%")
    void normalizaLaMerma() {
        RecipeItemRegistration item = new RecipeItemRegistration(1, new BigDecimal("0.25"), " KG ", null);

        assertThat(item.unit()).isEqualTo("kg");
        assertThat(item.wastePct()).isEqualByComparingTo("0.00");

        RecipeItemRegistration withWaste =
                new RecipeItemRegistration(1, new BigDecimal("0.25"), "kg", new BigDecimal("10"));
        assertThat(withWaste.wastePct()).isEqualByComparingTo("10.00");

        assertThatThrownBy(() -> new RecipeItemRegistration(1, new BigDecimal("0.25"), "kg", new BigDecimal("100")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new RecipeItemRegistration(1, BigDecimal.ZERO, "kg", new BigDecimal("10")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new RecipeItemRegistration(0, BigDecimal.ONE, "kg", null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Una categoría vacía elimina la categoría y el nombre se puede cambiar")
    void unaCategoriaVaciaLaElimina() {
        Instant now = Instant.now();
        Recipe recipe = new Recipe(1L, 1L, "Tarta", "Postres", true, null, now, now);

        Recipe updated = new RecipeModification(null, "", null).applyTo(recipe, now);

        assertThat(updated.name()).isEqualTo("Tarta");
        assertThat(updated.category()).isNull();
        assertThat(updated.active()).isTrue();
    }

    @Test
    @DisplayName("Una modificación vacía no toca nada")
    void unaModificacionVaciaNoCambiaNada() {
        Instant now = Instant.now();
        RecipeVersion version = new RecipeVersion(
                null,
                1L,
                1L,
                1,
                RecipeVersionStatus.DRAFT,
                4,
                new BigDecimal("100.00"),
                30,
                "Mezclar",
                null,
                null,
                null,
                null,
                1L,
                now,
                now);
        RecipeVersionModification modification = new RecipeVersionModification(null, null, null, null);

        assertThat(modification.isEmpty()).isTrue();
        assertThat(new RecipeModification(null, null, null).isEmpty()).isTrue();

        RecipeVersion unchanged = modification.applyTo(version, now);
        assertThat(unchanged.instructions()).isEqualTo("Mezclar");
        assertThat(unchanged.baseRations()).isEqualTo(4);
        assertThat(unchanged.yieldPct()).isEqualByComparingTo("100.00");
    }
}
