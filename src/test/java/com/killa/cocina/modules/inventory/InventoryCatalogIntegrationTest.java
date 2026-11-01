package com.killa.cocina.modules.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.killa.cocina.config.EstablishmentProperties;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import com.killa.cocina.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Catálogo de inventario de punta a punta contra PostgreSQL (INV-01, INV-02, INV-07).
 *
 * <p>Cubre RBAC por permiso, unicidad de nombres, conversiones incompatibles, archivo lógico y
 * auditoría con el actor del token.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryCatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService users;

    @Autowired
    private EstablishmentProperties establishment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long storekeeperId;

    private String storeToken;

    private String cookToken;

    @BeforeAll
    void prepararUsuarios() throws Exception {
        storekeeperId = users.create(
                        establishment.id(),
                        null,
                        new UserRegistration("catalog.store", "Almacenera", PASSWORD, Role.STOREKEEPER))
                .id();
        users.create(establishment.id(), null, new UserRegistration("catalog.cook", "Cocinera", PASSWORD, Role.COOK));
        storeToken = loginToken("catalog.store");
        cookToken = loginToken("catalog.cook");
    }

    @Test
    @DisplayName("El alta de producto guarda conversiones, stock cero y queda auditada con su actor")
    void elAltaDeProductoQuedaAuditada() throws Exception {
        long categoryId = createCategory("Carnes IT", storeToken);

        String body = mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pollo IT","categoryId":%d,"baseUnit":"G","minStock":1000,
                                 "conversions":[{"unit":"kg","factorToBase":1000}]}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pollo IT"))
                .andExpect(jsonPath("$.baseUnit").value("G"))
                .andExpect(jsonPath("$.currentStock").isNumber())
                .andExpect(jsonPath("$.currentAvgCost").isNumber())
                .andExpect(jsonPath("$.lowStock").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.conversions[0].unit").value("kg"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int productId = JsonPath.read(body, "$.id");
        assertThat(new BigDecimal(JsonPath.read(body, "$.currentStock").toString()))
                .isEqualByComparingTo("0");
        assertThat(new BigDecimal(JsonPath.read(body, "$.currentAvgCost").toString()))
                .isEqualByComparingTo("0");

        mvc.perform(get("/api/v1/products/" + productId).header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pollo IT"));

        mvc.perform(get("/api/v1/products?categoryId=" + categoryId).header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Pollo IT"));

        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select actor_id, entity_type, after_json::text as after_json
                from audit_logs
                where action = 'PRODUCT_CREATED' and entity_id = ?
                """, (long) productId);

        assertThat(audit.get("actor_id")).isEqualTo(storekeeperId);
        assertThat(audit.get("entity_type")).isEqualTo("product");
        assertThat((String) audit.get("after_json")).contains("Pollo IT").contains("kg");
    }

    @Test
    @DisplayName("Un COOK consulta el catálogo pero recibe 403 al escribirlo")
    void elCocineroSoloLeeElCatalogo() throws Exception {
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Prohibida IT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Proveedor prohibido IT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Un nombre de categoría o de producto repetido devuelve 409 DUPLICATE_NAME")
    void losNombresRepetidosDevuelven409() throws Exception {
        String categoryName = "Bebidas IT";
        long categoryId = createCategory(categoryName, storeToken);

        mvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + categoryName + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));

        String productName = "Agua IT";
        createProduct(productName, categoryId, storeToken);
        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","categoryId":%d,"baseUnit":"ML"}
                                """.formatted(productName, categoryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));
    }

    @Test
    @DisplayName("Renombrar una categoría funciona y chocar con otra devuelve 409")
    void renombrarCategoriaControlaDuplicados() throws Exception {
        long categoryId = createCategory("Verduras IT", storeToken);
        createCategory("Secos IT", storeToken);

        mvc.perform(patch("/api/v1/product-categories/" + categoryId)
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Verduras frescas IT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Verduras frescas IT"));

        mvc.perform(patch("/api/v1/product-categories/" + categoryId)
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Secos IT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));
    }

    @Test
    @DisplayName("Un producto con stock no se archiva; sin stock sí, y la baja es lógica")
    void elArchivoExigeStockCero() throws Exception {
        long categoryId = createCategory("Archivo IT", storeToken);
        long withStock = createProduct("Producto con stock IT", categoryId, storeToken);
        jdbcTemplate.update("update products set current_stock = 500 where id = ?", withStock);

        mvc.perform(patch("/api/v1/products/" + withStock)
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        mvc.perform(get("/api/v1/products/" + withStock).header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        long withoutStock = createProduct("Producto sin stock IT", categoryId, storeToken);
        mvc.perform(patch("/api/v1/products/" + withoutStock)
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from audit_logs where action = 'PRODUCT_ARCHIVED' and entity_id = ?",
                        Integer.class,
                        withoutStock))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Reemplazar conversiones rechaza unidades base y de otra dimensión")
    void lasConversionesInvalidasSeRechazan() throws Exception {
        long categoryId = createCategory("Conversiones IT", storeToken);
        long productId = createProduct("Harina IT", categoryId, storeToken);

        mvc.perform(put("/api/v1/products/" + productId + "/conversions")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversions\":[{\"unit\":\"g\",\"factorToBase\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(put("/api/v1/products/" + productId + "/conversions")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversions\":[{\"unit\":\"l\",\"factorToBase\":1000}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNIT_INCOMPATIBLE"));

        mvc.perform(put("/api/v1/products/" + productId + "/conversions")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversions":[{"unit":"kg","factorToBase":1000},
                                                 {"unit":"saco","factorToBase":25000}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].unit").value("kg"))
                .andExpect(jsonPath("$[1].unit").value("saco"));

        mvc.perform(get("/api/v1/products/" + productId + "/conversions")
                        .header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Los proveedores se crean, se desactivan y no admiten nombres repetidos")
    void losProveedoresSeDesactivanSinBorrar() throws Exception {
        String name = "Proveedor IT";

        String body = mvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"contact\":\"proveedor@example.test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int supplierId = JsonPath.read(body, "$.id");

        mvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));

        mvc.perform(patch("/api/v1/suppliers/" + supplierId)
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/v1/suppliers/" + supplierId).header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
    }

    private long createCategory(String name, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createProduct(String name, long categoryId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","categoryId":%d,"baseUnit":"G"}
                                """.formatted(name, categoryId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String loginToken(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
