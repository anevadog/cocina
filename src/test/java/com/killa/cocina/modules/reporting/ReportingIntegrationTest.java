package com.killa.cocina.modules.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.killa.cocina.config.EstablishmentProperties;
import com.killa.cocina.modules.inventory.domain.BaseUnit;
import com.killa.cocina.modules.inventory.domain.CategoryService;
import com.killa.cocina.modules.inventory.domain.ProductRegistration;
import com.killa.cocina.modules.inventory.domain.ProductService;
import com.killa.cocina.modules.inventory.domain.UnitConversion;
import com.killa.cocina.modules.suppliers.domain.SupplierRegistration;
import com.killa.cocina.modules.suppliers.domain.SupplierService;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import com.killa.cocina.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Informes agregados de costes y caducidades (ECO-01, ECO-06).
 *
 * <p>El contenedor es compartido con otras clases, así que las aserciones se hacen siempre sobre la
 * receta, el producto o el lote creados en el propio test.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportingIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService users;

    @Autowired
    private CategoryService categories;

    @Autowired
    private ProductService products;

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private EstablishmentProperties establishment;

    private long categoryId;

    private long supplierId;

    private String managerToken;

    private String cookToken;

    private int sequence;

    @BeforeAll
    void prepararDatos() throws Exception {
        users.create(
                establishment.id(),
                null,
                new UserRegistration("reports.manager", "Jefa de informes", PASSWORD, Role.KITCHEN_MANAGER));
        users.create(
                establishment.id(),
                null,
                new UserRegistration("reports.cook", "Cocinero informes", PASSWORD, Role.COOK));
        managerToken = loginToken("reports.manager");
        cookToken = loginToken("reports.cook");
        categoryId = categories
                .create(establishment.id(), null, "Informes agregados IT")
                .id();
        supplierId = suppliers
                .create(establishment.id(), null, new SupplierRegistration("Proveedor informes agregados IT", null))
                .id();
    }

    @Test
    @DisplayName("ECO-01: el histórico conserva el coste congelado y calcula la evolución")
    void informaDeCostesPorVersion() throws Exception {
        long productId = createProduct("Harina costes " + next());
        purchase(productId, "10", "2.00", null);

        RecipeFixture recipe = publishedRecipe("Receta costes " + next(), productId);
        purchase(productId, "10", "4.00", null);
        long secondVersion = publishNewVersion(recipe.recipeId(), productId);
        long unpublishedDraft = createDraft(recipe.recipeId());

        String body = mvc.perform(get("/api/v1/reports/recipe-costs").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> lines = JsonPath.read(body, "$.versions");
        List<Map<String, Object>> mine = lines.stream()
                .filter(line -> number(line, "recipeId") == recipe.recipeId())
                .toList();

        assertThat(mine).hasSize(2);
        assertThat(mine).extracting(line -> number(line, "versionNumber")).containsExactly(1L, 2L);
        assertThat(mine).extracting(line -> number(line, "versionId")).doesNotContain(unpublishedDraft);

        Map<String, Object> first = mine.getFirst();
        assertThat(first.get("recipeName")).isEqualTo(recipe.name());
        assertThat(decimal(first, "totalCost")).isEqualByComparingTo("0.4000");
        assertThat(decimal(first, "costPerRation")).isEqualByComparingTo("0.2000");
        assertThat(first.get("previousCostPerRation")).isNull();
        assertThat(first.get("variationAmount")).isNull();
        assertThat(first.get("variationPct")).isNull();

        Map<String, Object> second = mine.get(1);
        assertThat(number(second, "versionId")).isEqualTo(secondVersion);
        assertThat(decimal(second, "totalCost")).isEqualByComparingTo("0.6000");
        assertThat(decimal(second, "costPerRation")).isEqualByComparingTo("0.3000");
        assertThat(decimal(second, "previousCostPerRation")).isEqualByComparingTo("0.2000");
        assertThat(decimal(second, "variationAmount")).isEqualByComparingTo("0.1000");
        assertThat(decimal(second, "variationPct")).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("ECO-06: lista los lotes del horizonte con días restantes y valor en riesgo")
    void informaDeCaducidades() throws Exception {
        Instant expiresAt =
                Instant.now().plus(Duration.ofDays(3)).plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
        long productId = createProduct("Yogur caducidad " + next());
        long lotId = purchase(productId, "1", "5.00", expiresAt);
        long noExpiryProduct = createProduct("Producto sin caducidad " + next());
        purchase(noExpiryProduct, "1", "5.00", null);

        String body = mvc.perform(get("/api/v1/reports/expiring-lots")
                        .param("days", "7")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horizonDays").value(7))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> lots = JsonPath.read(body, "$.lots");
        Map<String, Object> line = lots.stream()
                .filter(candidate -> number(candidate, "lotId") == lotId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("El lote " + lotId + " no aparece en el informe"));

        assertThat(number(line, "productId")).isEqualTo(productId);
        assertThat(Instant.parse(String.valueOf(line.get("expiresAt")))).isEqualTo(expiresAt);
        assertThat(number(line, "daysRemaining")).isEqualTo(3);
        assertThat(decimal(line, "remainingQty")).isEqualByComparingTo("1000.000");
        assertThat(decimal(line, "unitCost")).isEqualByComparingTo("0.0050");
        assertThat(decimal(line, "value")).isEqualByComparingTo("5.0000");

        long totalLots = number(JsonPath.<Map<String, Object>>read(body, "$"), "totalLots");
        long returnedLots = number(JsonPath.<Map<String, Object>>read(body, "$"), "returnedLots");
        assertThat(totalLots).isGreaterThanOrEqualTo(returnedLots).isPositive();
        assertThat(decimal(JsonPath.<Map<String, Object>>read(body, "$"), "totalValue"))
                .isGreaterThanOrEqualTo(new BigDecimal("5.0000"));

        String narrow = mvc.perform(get("/api/v1/reports/expiring-lots")
                        .param("days", "2")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<Map<String, Object>>>read(narrow, "$.lots"))
                .noneMatch(candidate -> number(candidate, "lotId") == lotId);
    }

    @Test
    @DisplayName("El horizonte del informe se valida entre 1 y 90 días")
    void validaElHorizonte() throws Exception {
        mvc.perform(get("/api/v1/reports/expiring-lots")
                        .param("days", "0")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field", endsWith("days")));

        mvc.perform(get("/api/v1/reports/expiring-lots")
                        .param("days", "91")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field", endsWith("days")));
    }

    @Test
    @DisplayName("Los informes exigen token y están abiertos a cocina")
    void respetaLosPermisos() throws Exception {
        mvc.perform(get("/api/v1/reports/recipe-costs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/reports/expiring-lots").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/recipe-costs").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk());
    }

    private record RecipeFixture(long recipeId, long versionId, String name) {}

    private long createProduct(String name) {
        return products.create(
                        establishment.id(),
                        null,
                        new ProductRegistration(
                                name,
                                categoryId,
                                BaseUnit.G,
                                BigDecimal.ZERO,
                                false,
                                List.of(new UnitConversion("kg", new BigDecimal("1000")))))
                .id();
    }

    private long purchase(long productId, String quantityKg, String unitPrice, Instant expiresAt) throws Exception {
        String body = mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":%d,"quantity":%s,"unit":"kg","unitPrice":%s%s}
                                """.formatted(
                                        supplierId,
                                        quantityKg,
                                        unitPrice,
                                        expiresAt == null ? "" : ",\"expiresAt\":\"" + expiresAt + "\"")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return id(body, "$.lotId");
    }

    private RecipeFixture publishedRecipe(String name, long productId) throws Exception {
        String body = mvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","baseRations":2}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long recipeId = id(body, "$.id");
        long versionId = id(body, "$.draft.id");
        replaceItems(recipeId, versionId, productId);
        publish(recipeId, versionId);
        return new RecipeFixture(recipeId, versionId, name);
    }

    private long createDraft(long recipeId) throws Exception {
        String body = mvc.perform(post("/api/v1/recipes/" + recipeId + "/versions")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return id(body, "$.id");
    }

    private long publishNewVersion(long recipeId, long productId) throws Exception {
        long versionId = createDraft(recipeId);
        replaceItems(recipeId, versionId, productId);
        publish(recipeId, versionId);
        return versionId;
    }

    private void replaceItems(long recipeId, long versionId, long productId) throws Exception {
        mvc.perform(put("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":%d,"quantity":100,"unit":"g","wastePct":0}]}
                                """.formatted(productId)))
                .andExpect(status().isOk());
    }

    private void publish(long recipeId, long versionId) throws Exception {
        mvc.perform(post("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/publish")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
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

    private int next() {
        return ++sequence;
    }

    private static long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private static long number(Map<String, Object> line, String key) {
        return ((Number) line.get(key)).longValue();
    }

    private static BigDecimal decimal(Map<String, Object> line, String key) {
        return new BigDecimal(String.valueOf(line.get(key)));
    }
}
