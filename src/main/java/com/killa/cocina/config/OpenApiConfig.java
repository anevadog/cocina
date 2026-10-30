package com.killa.cocina.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos del contrato OpenAPI generado desde el código (PLA-01).
 *
 * <p>El documento se sirve en {@code /v3/api-docs} y la interfaz en {@code /swagger-ui.html}.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cocinaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cocina API")
                        .version("v1")
                        .description("""
                                API REST para la gestión interna de la cocina: inventario, recetas,
                                producción, permisos y trazabilidad.

                                Todas las fechas se expresan en ISO 8601 UTC y las cantidades y el
                                dinero con aritmética decimal exacta.
                                """));
    }
}
