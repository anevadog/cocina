package com.killa.cocina.modules.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Informes de desviaciones y de consumo teórico vs real (PRD-05, ECO-03).
 *
 * <p>El periodo se agrupa por la fecha de fin real del batch, así que los tests usan el día de hoy
 * en {@code Europe/Madrid}. Las aserciones se hacen sobre el producto o batch propios: el contenedor
 * es compartido y otras clases también producen y ajustan el mismo día.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductionReportingIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Madrid");

    private static final LocalDate TODAY = LocalDate.now(BUSINESS_ZONE);

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
                new UserRegistration("report.manager", "Jefa de informes", PASSWORD, Role.KITCHEN_MANAGER));
        users.create(
                establishment.id(),
                null,
                new UserRegistration("report.cook", "Cocinero informes", PASSWORD, Role.COOK));
        managerToken = loginToken("report.manager");
        cookToken = loginToken("report.cook");
        categoryId = categories
                .create(establishment.id(), null, "Informes producción IT")
                .id();
        supplierId = suppliers
                .create(establishment.id(), null, new SupplierRegistration("Proveedor informes IT", null))
                .id();
    }

    @Test
    @DisplayName("PRD-05: el informe lista solo desviaciones y explica el motivo")
    void informaDeLasDesviaciones() throws Exception {
        long firstProduct = createProduct("Informe desviación A " + next());
        long secondProduct = createProduct("Informe desviación B " + next());
        RecipeFixture withDeviation = publishedRecipe("Receta con desviación " + next(), """
                [{"productId":%d,"quantity":100,"unit":"g","wastePct":0}]
                """.formatted(firstProduct));
        RecipeFixture withoutDeviation =
                publishedRecipe("Receta sin desviación " + next(), """
                [{"productId":%d,"quantity":50,"unit":"g","wastePct":0}]
                """.formatted(secondProduct));

        long planId = id(createPlan(TODAY, "DINNER"), "$.id");
        String plan = replacePlanItems(planId, """
                [{"recipeVersionId":%d,"plannedRations":3},
                 {"recipeVersionId":%d,"plannedRations":2}]
                """.formatted(withDeviation.versionId(), withoutDeviation.versionId()));
        long deviatingItem = itemAt(plan, 0);
        long completeItem = itemAt(plan, 1);

        long deviatingBatch = id(startBatch(planId, deviatingItem), "$.id");
        transitionBatch(deviatingBatch, "READY", 2, "menos comensales");
        long completeBatch = id(startBatch(planId, completeItem), "$.id");
        transitionBatch(completeBatch, "READY", 2, null);

        List<Map<String, Object>> lines =
                reportLines("/api/v1/production-reports/deviations", TODAY, TODAY, "$.deviations");

        Map<String, Object> line = lineOf(lines, "batchId", deviatingBatch);
        assertThat(number(line, "planId")).isEqualTo(planId);
        assertThat(line.get("serviceDate")).isEqualTo(TODAY.toString());
        assertThat(line.get("serviceType")).isEqualTo("DINNER");
        assertThat(number(line, "planItemId")).isEqualTo(deviatingItem);
        assertThat(line.get("recipeName")).isEqualTo(withDeviation.name());
        assertThat(number(line, "versionNumber")).isEqualTo(1);
        assertThat(number(line, "plannedRations")).isEqualTo(3);
        assertThat(number(line, "producedRations")).isEqualTo(2);
        assertThat(number(line, "deviation")).isEqualTo(-1);
        assertThat(line.get("deviationReason")).isEqualTo("menos comensales");
        assertThat(line.get("finishedAt")).isNotNull();

        assertThat(lines).noneMatch(candidate -> number(candidate, "batchId") == completeBatch);

        assertThat(reportLines(
                        "/api/v1/production-reports/deviations",
                        TODAY.minusDays(1),
                        TODAY.minusDays(1),
                        "$.deviations"))
                .isEmpty();
    }

    @Test
    @DisplayName("ECO-03: consumo teórico, producción, merma y ajustes cuadran por producto")
    void comparaConsumoTeoricoYReal() throws Exception {
        String flourName = "Harina informe " + next();
        long flour = createProduct(flourName);
        long salt = createProduct("Sal informe " + next());
        RecipeFixture recipe = publishedRecipe("Receta consumo " + next(), """
                [{"productId":%d,"quantity":100,"unit":"g","wastePct":10},
                 {"productId":%d,"quantity":50,"unit":"g","wastePct":0}]
                """.formatted(flour, salt));

        long planId = id(createPlan(TODAY, "LUNCH"), "$.id");
        long planItemId = itemAt(replacePlanItems(planId, """
                [{"recipeVersionId":%d,"plannedRations":2}]
                """.formatted(recipe.versionId())), 0);
        long batchId = id(startBatch(planId, planItemId), "$.id");
        transitionBatch(batchId, "READY", 2, null);

        adjustOut(flour, "0.03", "Merma de línea");

        List<Map<String, Object>> lines =
                reportLines("/api/v1/production-reports/consumption", TODAY, TODAY, "$.products");

        Map<String, Object> flourLine = lineOf(lines, "productId", flour);
        assertThat(flourLine.get("productName")).isEqualTo(flourName);
        assertThat(flourLine.get("unit")).isEqualTo("g");
        assertThat(decimal(flourLine, "theoreticalQuantity")).isEqualByComparingTo("200");
        assertThat(decimal(flourLine, "productionQuantity")).isEqualByComparingTo("220");
        assertThat(decimal(flourLine, "wasteQuantity")).isEqualByComparingTo("20");
        assertThat(decimal(flourLine, "adjustmentQuantity")).isEqualByComparingTo("30");
        assertThat(decimal(flourLine, "realQuantity")).isEqualByComparingTo("250");
        assertThat(decimal(flourLine, "deviation")).isEqualByComparingTo("50");
        assertThat(decimal(flourLine, "deviation"))
                .isEqualByComparingTo(
                        decimal(flourLine, "wasteQuantity").add(decimal(flourLine, "adjustmentQuantity")));

        Map<String, Object> saltLine = lineOf(lines, "productId", salt);
        assertThat(decimal(saltLine, "theoreticalQuantity")).isEqualByComparingTo("100");
        assertThat(decimal(saltLine, "productionQuantity")).isEqualByComparingTo("100");
        assertThat(decimal(saltLine, "wasteQuantity")).isZero();
        assertThat(decimal(saltLine, "adjustmentQuantity")).isZero();
        assertThat(decimal(saltLine, "deviation")).isZero();

        List<Long> ordered =
                lines.stream().map(line -> number(line, "productId")).toList();
        assertThat(ordered).isSorted();
    }

    @Test
    @DisplayName("Un periodo sin producción devuelve informes vacíos")
    void unPeriodoSinDatosDevuelveListasVacias() throws Exception {
        LocalDate old = LocalDate.of(2019, 1, 1);
        assertThat(reportLines("/api/v1/production-reports/deviations", old, old.plusDays(1), "$.deviations"))
                .isEmpty();
        assertThat(reportLines("/api/v1/production-reports/consumption", old, old.plusDays(1), "$.products"))
                .isEmpty();
    }

    @Test
    @DisplayName("El rango del informe se valida antes de consultar")
    void validaElRango() throws Exception {
        mvc.perform(get("/api/v1/production-reports/deviations")
                        .param("from", TODAY.toString())
                        .param("to", TODAY.minusDays(1).toString())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("to"));

        mvc.perform(get("/api/v1/production-reports/consumption")
                        .param("from", "2026-01-01")
                        .param("to", "2026-02-01")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("to"));
    }

    @Test
    @DisplayName("Los informes exigen token y están abiertos a cocina")
    void respetaLosPermisos() throws Exception {
        mvc.perform(get("/api/v1/production-reports/consumption")
                        .param("from", TODAY.toString())
                        .param("to", TODAY.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/production-reports/deviations")
                        .param("from", TODAY.toString())
                        .param("to", TODAY.toString())
                        .header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk());
    }

    private record RecipeFixture(long recipeId, long versionId, String name) {}

    private List<Map<String, Object>> reportLines(String path, LocalDate from, LocalDate to, String jsonPath)
            throws Exception {
        String body = mvc.perform(get(path)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, jsonPath);
    }

    private RecipeFixture publishedRecipe(String name, String itemsJson) throws Exception {
        String body = mvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","baseRations":1}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long recipeId = id(body, "$.id");
        long versionId = id(body, "$.draft.id");
        mvc.perform(put("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":" + itemsJson + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/publish")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        return new RecipeFixture(recipeId, versionId, name);
    }

    private String createPlan(LocalDate serviceDate, String serviceType) throws Exception {
        return mvc.perform(post("/api/v1/production-plans")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","serviceType":"%s"}
                                """.formatted(serviceDate, serviceType)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String replacePlanItems(long planId, String itemsJson) throws Exception {
        return mvc.perform(put("/api/v1/production-plans/" + planId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":" + itemsJson + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String startBatch(long planId, long itemId) throws Exception {
        return mvc.perform(post("/api/v1/production-plans/" + planId + "/items/" + itemId + "/batches")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void transitionBatch(long batchId, String target, Integer rations, String reason) throws Exception {
        StringBuilder body = new StringBuilder("{\"status\":\"").append(target).append("\"");
        if (rations != null) {
            body.append(",\"rations\":").append(rations);
        }
        if (reason != null) {
            body.append(",\"deviationReason\":\"").append(reason).append("\"");
        }
        body.append("}");
        mvc.perform(patch("/api/v1/production-batches/" + batchId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk());
    }

    private long createProduct(String name) throws Exception {
        long productId = products.create(
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
        mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":%d,"quantity":10,"unit":"kg","unitPrice":2.00}
                                """.formatted(supplierId)))
                .andExpect(status().isCreated());
        return productId;
    }

    private void adjustOut(long productId, String quantityKg, String reason) throws Exception {
        mvc.perform(post("/api/v1/products/" + productId + "/adjustments")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ADJUSTMENT_OUT","quantity":%s,"unit":"kg","reason":"%s"}
                                """.formatted(quantityKg, reason)))
                .andExpect(status().isCreated());
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

    private static long itemAt(String body, int index) {
        List<Number> ids = JsonPath.read(body, "$.items[*].id");
        return ids.get(index).longValue();
    }

    private static Map<String, Object> lineOf(List<Map<String, Object>> lines, String key, long id) {
        return lines.stream()
                .filter(line -> number(line, key) == id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay línea para " + key + "=" + id));
    }

    private static long number(Map<String, Object> line, String key) {
        return ((Number) line.get(key)).longValue();
    }

    private static BigDecimal decimal(Map<String, Object> line, String key) {
        return new BigDecimal(String.valueOf(line.get(key)));
    }
}
