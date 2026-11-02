package com.killa.cocina.modules.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Trazabilidad de lote a preparación (CAL-01).
 *
 * <p>Produce de verdad contra PostgreSQL: compra un lote, lo consume una receta publicada y
 * comprueba que la consulta devuelve la preparación, la fecha, la versión y la cantidad consumida.
 * También cubre el lote sin consumos y el lote inexistente.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LotTraceabilityIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    private static final LocalDate BASE_DATE = LocalDate.of(2033, 1, 1);

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

    private String storekeeperToken;

    private int sequence;

    @BeforeAll
    void prepararDatos() throws Exception {
        users.create(
                establishment.id(),
                null,
                new UserRegistration("trace.manager", "Jefa de trazabilidad", PASSWORD, Role.KITCHEN_MANAGER));
        users.create(
                establishment.id(),
                null,
                new UserRegistration("trace.store", "Almacén de trazabilidad", PASSWORD, Role.STOREKEEPER));
        managerToken = loginToken("trace.manager");
        storekeeperToken = loginToken("trace.store");
        categoryId =
                categories.create(establishment.id(), null, "Trazabilidad IT").id();
        supplierId = suppliers
                .create(
                        establishment.id(),
                        null,
                        new SupplierRegistration("Proveedor trazabilidad IT", "trace@example.test"))
                .id();
    }

    @Test
    @DisplayName("CAL-01: un lote consumido por una preparación se traza con batch, plan y versión")
    void trazaElConsumoDeProduccion() throws Exception {
        String productName = "Harina trazabilidad " + next();
        long productId = createProduct(productName);
        long lotId = purchase(productId, "1", "2.00");
        RecipeFixture recipe = publishedRecipe("Receta trazabilidad " + next(), """
                [{"productId":%d,"quantity":250,"unit":"g","wastePct":0}]
                """.formatted(productId));
        LocalDate serviceDate = nextDate();
        long planId = id(createPlan(serviceDate, "LUNCH"), "$.id");
        long itemId = id(replacePlanItems(planId, recipe.versionId(), 2), "$.items[0].id");
        long batchId = id(startBatch(planId, itemId), "$.id");
        transitionBatch(batchId, "READY", 2);

        mvc.perform(get("/api/v1/stock-lots/" + lotId + "/traceability")
                        .header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotId").value(lotId))
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.productName").value(productName))
                .andExpect(jsonPath("$.unit").value("g"))
                .andExpect(jsonPath("$.initialQty").value(1000.000))
                .andExpect(jsonPath("$.remainingQty").value(500.000))
                .andExpect(jsonPath("$.expiresAt").value(nullValue()))
                .andExpect(jsonPath("$.consumptions.length()").value(1))
                .andExpect(jsonPath("$.consumptions[0].batchId").value(batchId))
                .andExpect(jsonPath("$.consumptions[0].planId").value(planId))
                .andExpect(jsonPath("$.consumptions[0].serviceDate").value(serviceDate.toString()))
                .andExpect(jsonPath("$.consumptions[0].serviceType").value("LUNCH"))
                .andExpect(jsonPath("$.consumptions[0].recipeId").value(recipe.recipeId()))
                .andExpect(jsonPath("$.consumptions[0].recipeName").value(recipe.name()))
                .andExpect(jsonPath("$.consumptions[0].recipeVersionNumber").value(1))
                .andExpect(jsonPath("$.consumptions[0].quantityBase").value(500.000))
                .andExpect(jsonPath("$.consumptions[0].occurredAt").isNotEmpty());
    }

    @Test
    @DisplayName("CAL-01: un lote sin consumos devuelve la lista vacía, no un error")
    void unLoteSinConsumosDevuelveListaVacia() throws Exception {
        long productId = createProduct("Sal trazabilidad " + next());
        long lotId = purchase(productId, "1", "1.50");

        String body = mvc.perform(get("/api/v1/stock-lots/" + lotId + "/traceability")
                        .header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumptions.length()").value(0))
                .andExpect(jsonPath("$.remainingQty").value(1000.000))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(((Number) JsonPath.read(body, "$.lotId")).longValue()).isEqualTo(lotId);
    }

    @Test
    @DisplayName("Un lote inexistente devuelve 404 y sin token 401")
    void validaElLoteYLaAutenticacion() throws Exception {
        mvc.perform(get("/api/v1/stock-lots/999999/traceability").header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mvc.perform(get("/api/v1/stock-lots/1/traceability")).andExpect(status().isUnauthorized());
    }

    private record RecipeFixture(long recipeId, long versionId, String name) {}

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
        return productId;
    }

    private long purchase(long productId, String quantityKg, String unitPrice) throws Exception {
        String body = mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":%d,"quantity":%s,"unit":"kg","unitPrice":%s}
                                """.formatted(supplierId, quantityKg, unitPrice)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return id(body, "$.lotId");
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

    private String replacePlanItems(long planId, long versionId, int plannedRations) throws Exception {
        return mvc.perform(put("/api/v1/production-plans/" + planId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"recipeVersionId":%d,"plannedRations":%d}]}
                                """.formatted(versionId, plannedRations)))
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

    private void transitionBatch(long batchId, String target, int rations) throws Exception {
        mvc.perform(patch("/api/v1/production-batches/" + batchId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s","rations":%d}
                                """.formatted(target, rations)))
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

    private LocalDate nextDate() {
        return BASE_DATE.plusDays(next());
    }

    private int next() {
        return ++sequence;
    }

    private static long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }
}
