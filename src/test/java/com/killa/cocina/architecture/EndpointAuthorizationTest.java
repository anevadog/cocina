package com.killa.cocina.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.killa.cocina.modules.users.domain.Permission;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guardia de mínimo privilegio (PER-05).
 *
 * <p>Un endpoint sin {@code @PreAuthorize} no tiene permiso declarado, así que no debe existir. Esta
 * prueba falla en cuanto alguien añade un método de controlador y olvida el permiso, en lugar de
 * descubrirlo en producción.</p>
 */
class EndpointAuthorizationTest {

    /** Endpoints públicos por diseño: el login no puede exigir token y refresh/logout se autentican
     * con el propio refresh token. */
    private static final Set<String> PUBLIC_CONTROLLERS = Set.of("com.killa.cocina.modules.auth.api.AuthController");

    private static final Pattern QUOTED_CODE = Pattern.compile("'([^']+)'");

    private static final JavaClasses MODULE_CLASSES =
            new ClassFileImporter().importPackages("com.killa.cocina.modules");

    @Test
    @DisplayName("Todo endpoint de negocio declara su permiso con @PreAuthorize")
    void todoEndpointDeclaraPermiso() {
        List<String> withoutPermission = new ArrayList<>();
        int endpoints = 0;

        for (JavaClass controller : MODULE_CLASSES) {
            if (!controller.isAnnotatedWith(RestController.class)
                    || PUBLIC_CONTROLLERS.contains(controller.getName())) {
                continue;
            }
            for (JavaMethod method : controller.getMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                endpoints++;
                if (!method.isAnnotatedWith(PreAuthorize.class)) {
                    withoutPermission.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(endpoints).as("debe haber endpoints de negocio que revisar").isPositive();
        assertThat(withoutPermission)
                .as("endpoints sin permiso declarado (PER-05)")
                .isEmpty();
    }

    @Test
    @DisplayName("Cada permiso citado en @PreAuthorize existe en la matriz de permisos")
    void losPermisosCitadosExisten() {
        Set<String> known =
                Arrays.stream(Permission.values()).map(Permission::code).collect(Collectors.toSet());
        List<String> unknown = new ArrayList<>();

        for (JavaClass controller : MODULE_CLASSES) {
            if (!controller.isAnnotatedWith(RestController.class)) {
                continue;
            }
            for (JavaMethod method : controller.getMethods()) {
                if (!method.isAnnotatedWith(PreAuthorize.class)) {
                    continue;
                }
                String expression =
                        method.getAnnotationOfType(PreAuthorize.class).value();
                Matcher matcher = QUOTED_CODE.matcher(expression);
                while (matcher.find()) {
                    if (!known.contains(matcher.group(1))) {
                        unknown.add(method.getFullName() + " -> " + matcher.group(1));
                    }
                }
            }
        }

        assertThat(unknown)
                .as("permisos citados que no existen en Permission: una errata deja el endpoint inaccesible")
                .isEmpty();
    }

    private static boolean isEndpoint(JavaMethod method) {
        return method.isAnnotatedWith(RequestMapping.class) || method.isMetaAnnotatedWith(RequestMapping.class);
    }
}
