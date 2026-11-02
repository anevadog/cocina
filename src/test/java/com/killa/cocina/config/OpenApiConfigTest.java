package com.killa.cocina.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

/**
 * El contrato OpenAPI debe declarar el token para que Swagger UI permita autenticarse (PLA-01).
 */
class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void declaresBearerScheme() {
        OpenAPI api = config.cocinaOpenApi();

        SecurityScheme scheme = api.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);

        assertThat(scheme).isNotNull();
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }

    @Test
    void requiresBearerOnProtectedOperationsOnly() {
        Paths paths = new Paths();
        paths.addPathItem("/api/v1/products", new PathItem().get(new Operation()));
        paths.addPathItem("/api/v1/auth/login", new PathItem().post(new Operation()));
        paths.addPathItem("/health", new PathItem().get(new Operation()));
        OpenAPI api = new OpenAPI().paths(paths);

        config.bearerSecurityCustomizer().customise(api);

        assertThat(api.getPaths().get("/api/v1/products").getGet().getSecurity())
                .singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey(OpenApiConfig.BEARER_SCHEME));
        assertThat(api.getPaths().get("/api/v1/auth/login").getPost().getSecurity())
                .isNull();
        assertThat(api.getPaths().get("/health").getGet().getSecurity()).isNull();
    }
}
