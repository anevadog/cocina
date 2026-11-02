package com.killa.cocina.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos del contrato OpenAPI generado desde el código (PLA-01).
 *
 * <p>El documento se sirve en {@code /v3/api-docs} y la interfaz en {@code /swagger-ui.html}. El
 * esquema {@code bearerAuth} permite pegar el access token en Swagger UI: sin él, la interfaz no
 * muestra el botón <em>Authorize</em> y no hay forma de probar los endpoints protegidos.</p>
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI cocinaOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Cocina API").version("v1").description("""
                                API REST para la gestión interna de la cocina: inventario, recetas,
                                producción, permisos y trazabilidad.

                                Todas las fechas se expresan en ISO 8601 UTC y las cantidades y el
                                dinero con aritmética decimal exacta.
                                """))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("""
                                                Token obtenido en POST /api/v1/auth/login.
                                                Pegar solo el valor de accessToken, sin el prefijo Bearer.
                                                """)));
    }

    /**
     * Exige el token en todo lo que no sea público.
     *
     * <p>Los endpoints de autenticación, {@code /health} y {@code /ready} se dejan sin requisito:
     * Swagger los marcaría con candado y enviaría un token innecesario o vacío. El resto recibe el
     * requisito para que la interfaz añada la cabecera {@code Authorization} al ejecutar.</p>
     */
    @Bean
    public OpenApiCustomizer bearerSecurityCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (isPublic(path)) {
                    return;
                }
                for (Operation operation : item.readOperations()) {
                    operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
                }
            });
        };
    }

    private static boolean isPublic(String path) {
        return path.equals("/health")
                || path.equals("/ready")
                || path.startsWith("/api/v1/auth/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }
}
