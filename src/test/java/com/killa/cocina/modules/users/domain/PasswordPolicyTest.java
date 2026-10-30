package com.killa.cocina.modules.users.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Política mínima de contraseñas (NFR-SEG-01, D2 de F2). */
class PasswordPolicyTest {

    private static final String VALID = "contrasena-larga-2026";

    @Test
    @DisplayName("Una contraseña de 12 caracteres o más que no es el usuario es aceptable")
    void aceptaContrasenaSuficientementeLarga() {
        assertThatCode(() -> PasswordPolicy.validate(VALID, "chef")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(PasswordPolicy.MIN_LENGTH), "chef"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Se rechaza cualquier contraseña por debajo del mínimo, sin excepciones por tipo")
    void rechazaContrasenaCorta() {
        String shortPassword = "a".repeat(PasswordPolicy.MIN_LENGTH - 1);

        assertThatThrownBy(() -> PasswordPolicy.validate(shortPassword, "chef"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ErrorCode.WEAK_PASSWORD);
    }

    @Test
    @DisplayName("La contraseña no puede ser el nombre de usuario, en ningún caso")
    void rechazaContrasenaIgualAlUsuario() {
        assertThatThrownBy(() -> PasswordPolicy.validate("JefaDeCocina", "jefadecocina"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ErrorCode.WEAK_PASSWORD);
    }

    @Test
    @DisplayName("Se rechazan contraseñas vacías o en blanco y las que superan el máximo")
    void rechazaValoresVaciosYDesmesurados() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null, "chef"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("   ", "chef"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(PasswordPolicy.MAX_LENGTH + 1), "chef"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ErrorCode.WEAK_PASSWORD);
    }

    @Test
    @DisplayName("El detalle del error señala el campo password, no filtra la contraseña")
    void elDetalleSenalaElCampoPassword() {
        ApiException exception = (ApiException) org.assertj.core.api.Assertions.catchThrowable(
                () -> PasswordPolicy.validate("corta", "chef"));

        assertThat(exception.details()).singleElement()
                .satisfies(detail -> assertThat(detail.field()).isEqualTo("password"));
        assertThat(exception.getMessage()).doesNotContain("corta");
    }
}
