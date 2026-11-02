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
import com.killa.cocina.modules.production.domain.ProductionService;
import com.killa.cocina.modules.production.domain.ProductionStatus;
import com.killa.cocina.modules.suppliers.domain.SupplierRegistration;
import com.killa.cocina.modules.suppliers.domain.SupplierService;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Ejecución de la producción y descuento transaccional (PRD-03..05, REC-06, CAL-04).
 *
 * <p>Se prueba contra PostgreSQL real: empezar y cancelar no tocan stock; terminar descuenta una sola
 * vez, por lotes vigentes y en una transacción; un fallo parcial no deja rastro; dos producciones
 * simultáneas no sobrevenden. Los nombres y fechas son únicos por test porque Testcontainers
 * comparte la base entre clases.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductionExecutionIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    private static final LocalDate BASE_DATE = LocalDate.of(2032, 1, 1);

    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserService users;

    @Autowired
    private CategoryService categories;

    @Autowired
    private ProductService products;

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private ProductionService production;

    @Autowired
    private EstablishmentProperties establishment;

    private long categoryId;

    private long supplierId;

    private long managerId;

    private String managerToken;

    private String cookToken;

    private String storekeeperToken;

    private int sequence;

    @BeforeAll
    void prepararDatos() throws Exception {
        managerId = users.create(
                        establishment.id(),
                        null,
                        new UserRegistration("exec.manager", "Jefa de ejecución", PASSWORD, Role.KITCHEN_MANAGER))
                .id();
        users.create(establishment.id(), null, new UserRegistration("exec.cook", "Cocinero", PASSWORD, Role.COOK));
        users.create(
                establishment.id(), null, new UserRegistration("exec.store", "Almacén", PASSWORD, Role.STOREKEEPER));
        managerToken = loginToken("exec.manager");
        cookToken = loginToken("exec.cook");
        storekeeperToken = loginToken("exec.store");
        categoryId = categories
                .create(establishment.id(), null, "Producción ejecución IT")
                .id();
        supplierId = suppliers
                .create(establishment.id(), null, new SupplierRegistration("Proveedor ejecución IT", null))
                .id();
    }

    @Test
    @DisplayName("PRD-03/PRD-04: empezar no descuenta y READY consume una sola vez por lote vigente")
    void terminaLaProduccionConDescuentoAtomico() throws Exception {
        long productId = createProduct("Harina ejecución " + next());
        RecipeFixture recipe = publishedRecipe("Receta ejecución " + next(), """
                [{"productId":%d,"quantity":250,"unit":"g","wastePct":0}]
                """.formatted(productId));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");
        long itemId = id(replacePlanItems(planId, recipe.versionId(), 4), "$.items[0].id");

        String started = startBatch(planId, itemId);
        long batchId = id(started, "$.id");
        assertThat(string(started, "$.status")).isEqualTo("IN_PROGRESS");
        assertThat(intValue(started, "$.plannedRations")).isEqualTo(4);
        assertThat(intValue(started, "$.rations")).isEqualTo(4);
        assertThat(json(started, "$.finishedAt")).isNull();
        assertThat(planStatus(planId)).isEqualTo("IN_PROGRESS");
        assertThat(stock(productId)).isEqualByComparingTo("1000.000");
        assertThat(productionMovements(productId)).isEmpty();

        String fetched = mvc.perform(
                        get("/api/v1/production-batches/" + batchId).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(longValue(fetched, "$.id")).isEqualTo(batchId);

        String ready = transitionBatch(batchId, "READY", 4, null);
        assertThat(string(ready, "$.status")).isEqualTo("READY");
        assertThat(intValue(ready, "$.deviation")).isZero();
        assertThat(json(ready, "$.finishedAt")).isNotNull();
        assertThat(longValue(ready, "$.finishedBy")).isEqualTo(managerId);
        assertThat(planStatus(planId)).isEqualTo("READY");
        assertThat(stock(productId)).isEqualByComparingTo("0.000");

        List<MovementRow> movements = productionMovements(productId);
        assertThat(movements).hasSize(1);
        MovementRow movement = movements.getFirst();
        assertThat(movement.quantity()).isEqualByComparingTo("1000.000");
        assertThat(movement.balanceAfter()).isEqualByComparingTo("0.000");
        assertThat(movement.unitCost()).isEqualByComparingTo("0.0020");
        assertThat(movement.lotId()).isNotNull();
        assertThat(movement.referenceType()).isEqualTo("production_batch");
        assertThat(movement.referenceId()).isEqualTo(batchId);
        assertThat(movement.actorId()).isEqualTo(managerId);

        String served = transitionBatch(batchId, "SERVED", null, null);
        assertThat(string(served, "$.status")).isEqualTo("SERVED");
        assertThat(intValue(served, "$.rations")).isEqualTo(4);
        assertThat(planStatus(planId)).isEqualTo("SERVED");
        assertThat(productionMovements(productId)).hasSize(1);

        assertThat(auditActions(batchId))
                .containsExactly("PRODUCTION_BATCH_STARTED", "PRODUCTION_BATCH_FINISHED", "PRODUCTION_BATCH_SERVED");
    }

    @Test
    @DisplayName("PRD-04: cancelar antes de terminar no descuenta y cierra la ejecución")
    void cancelarNoDescuentaNiAdmiteMasTransiciones() throws Exception {
        long productId = createProduct("Sal ejecución " + next());
        RecipeFixture recipe = publishedRecipe("Receta cancelada " + next(), """
                [{"productId":%d,"quantity":100,"unit":"g"}]
                """.formatted(productId));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");
        long itemId = id(replacePlanItems(planId, recipe.versionId(), 2), "$.items[0].id");

        long batchId = id(startBatch(planId, itemId), "$.id");
        assertThat(planStatus(planId)).isEqualTo("IN_PROGRESS");

        mvc.perform(post("/api/v1/production-plans/" + planId + "/items/" + itemId + "/batches")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        String cancelled = transitionBatch(batchId, "CANCELLED", null, null);
        assertThat(string(cancelled, "$.status")).isEqualTo("CANCELLED");
        assertThat(json(cancelled, "$.finishedAt")).isNull();
        assertThat(planStatus(planId)).isEqualTo("CANCELLED");
        assertThat(stock(productId)).isEqualByComparingTo("1000.000");
        assertThat(productionMovements(productId)).isEmpty();
        assertThat(auditActions(batchId)).containsExactly("PRODUCTION_BATCH_STARTED", "PRODUCTION_BATCH_CANCELLED");

        mvc.perform(patch("/api/v1/production-batches/" + batchId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\",\"rations\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
        mvc.perform(patch("/api/v1/production-batches/" + batchId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SERVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        long otherPlan = id(createPlan(nextDate(), "LUNCH"), "$.id");
        long otherItem = id(replacePlanItems(otherPlan, recipe.versionId(), 1), "$.items[0].id");
        mvc.perform(patch("/api/v1/production-plans/" + otherPlan)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/production-plans/" + otherPlan + "/items/" + otherItem + "/batches")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
        assertThat(stock(productId)).isEqualByComparingTo("1000.000");
    }

    @Test
    @DisplayName("PRD-05: la desviación exige motivo y descuenta las raciones reales")
    void laDesviacionExigeMotivoYDescuentaLoReal() throws Exception {
        long productId = createProduct("Arroz ejecución " + next());
        RecipeFixture recipe = publishedRecipe("Receta desviación " + next(), """
                [{"productId":%d,"quantity":250,"unit":"g"}]
                """.formatted(productId));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");
        long itemId = id(replacePlanItems(planId, recipe.versionId(), 4), "$.items[0].id");
        long batchId = id(startBatch(planId, itemId), "$.id");

        mvc.perform(patch("/api/v1/production-batches/" + batchId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\",\"rations\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("deviationReason"));
        assertThat(stock(productId)).isEqualByComparingTo("1000.000");
        assertThat(productionMovements(productId)).isEmpty();
        assertThat(batchStatus(batchId)).isEqualTo("IN_PROGRESS");

        mvc.perform(patch("/api/v1/production-batches/" + batchId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("rations"));

        String ready = transitionBatch(batchId, "READY", 3, "menos comensales");
        assertThat(string(ready, "$.status")).isEqualTo("READY");
        assertThat(intValue(ready, "$.rations")).isEqualTo(3);
        assertThat(intValue(ready, "$.deviation")).isEqualTo(-1);
        assertThat(string(ready, "$.deviationReason")).isEqualTo("menos comensales");
        assertThat(stock(productId)).isEqualByComparingTo("250.000");
        assertThat(productionMovements(productId)).hasSize(1);
        assertThat(productionMovements(productId).getFirst().quantity()).isEqualByComparingTo("750.000");
        assertThat(planStatus(planId)).isEqualTo("READY");
    }

    @Test
    @DisplayName("CAL-04: un lote caducado no se consume y el vigente sí")
    void losLotesCaducadosNoSeConsumen() throws Exception {
        Instant now = Instant.now();
        long productId = createProductWithPurchase(
                "Caducado ejecución " + next(),
                "1",
                "2.00",
                now.minus(10, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS));
        RecipeFixture recipe = publishedRecipe("Receta caducada " + next(), """
                [{"productId":%d,"quantity":1000,"unit":"g"}]
                """.formatted(productId));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");
        long itemId = id(replacePlanItems(planId, recipe.versionId(), 1), "$.items[0].id");
        long batchId = id(startBatch(planId, itemId), "$.id");

        mvc.perform(patch("/api/v1/production-batches/" + batchId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\",\"rations\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("caducados")));
        assertThat(stock(productId)).isEqualByComparingTo("1000.000");
        assertThat(productionMovements(productId)).isEmpty();
        assertThat(batchStatus(batchId)).isEqualTo("IN_PROGRESS");

        purchase(productId, "1", "2.00", null, now.plus(10, ChronoUnit.DAYS));
        long validLot = jdbcTemplate.queryForObject(
                "select id from stock_lots where product_id = ? and expires_at > now()", Long.class, productId);

        String ready = transitionBatch(batchId, "READY", 1, null);
        assertThat(string(ready, "$.status")).isEqualTo("READY");
        assertThat(stock(productId)).isEqualByComparingTo("1000.000");
        List<MovementRow> movements = productionMovements(productId);
        assertThat(movements).hasSize(1);
        assertThat(movements.getFirst().lotId()).isEqualTo(validLot);
        assertThat(lotRemaining(productId, movements.getFirst().lotId())).isEqualByComparingTo("0.000");
        BigDecimal expiredRemaining = jdbcTemplate.queryForObject(
                "select coalesce(sum(remaining_qty), 0) from stock_lots"
                        + " where product_id = ? and expires_at <= now()",
                BigDecimal.class,
                productId);
        assertThat(expiredRemaining).isEqualByComparingTo("1000.000");
    }

    @Test
    @DisplayName("NFR-INT-01: si un ingrediente falla no se descuenta ninguno")
    void elFalloParcialNoDejaRastro() throws Exception {
        long enough = createProduct("Ingrediente suficiente " + next());
        long scarce = createProductWithPurchase("Ingrediente escaso " + next(), "0.1", "5.00", null, null);
        RecipeFixture recipe = publishedRecipe("Receta parcial " + next(), """
                [
                    {"productId":%d,"quantity":100,"unit":"g"},
                    {"productId":%d,"quantity":100,"unit":"g"}
                ]
                """.formatted(enough, scarce));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");
        long itemId = id(replacePlanItems(planId, recipe.versionId(), 4), "$.items[0].id");
        long batchId = id(startBatch(planId, itemId), "$.id");

        mvc.perform(patch("/api/v1/production-batches/" + batchId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\",\"rations\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(stock(enough)).isEqualByComparingTo("1000.000");
        assertThat(stock(scarce)).isEqualByComparingTo("100.000");
        assertThat(productionMovements(enough)).isEmpty();
        assertThat(productionMovements(scarce)).isEmpty();
        assertThat(batchStatus(batchId)).isEqualTo("IN_PROGRESS");
        assertThat(planStatus(planId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("NFR-INT-01: dos producciones simultáneas no sobrevenden el mismo stock")
    void laConcurrenciaNoSobrevende() throws Exception {
        long productId = createProduct("Concurrencia ejecución " + next());
        RecipeFixture recipe = publishedRecipe("Receta concurrencia " + next(), """
                [{"productId":%d,"quantity":100,"unit":"g"}]
                """.formatted(productId));
        long firstPlan = id(createPlan(nextDate(), "LUNCH"), "$.id");
        long firstItem = id(replacePlanItems(firstPlan, recipe.versionId(), 6), "$.items[0].id");
        long firstBatch = id(startBatch(firstPlan, firstItem), "$.id");

        long secondPlan = id(createPlan(nextDate(), "LUNCH"), "$.id");
        long secondItem = id(replacePlanItems(secondPlan, recipe.versionId(), 6), "$.items[0].id");
        long secondBatch = id(startBatch(secondPlan, secondItem), "$.id");

        List<Callable<Void>> tasks = List.of(
                () -> {
                    production.transitionBatch(
                            establishment.id(), managerId, firstBatch, ProductionStatus.READY, 6, null);
                    return null;
                },
                () -> {
                    production.transitionBatch(
                            establishment.id(), managerId, secondBatch, ProductionStatus.READY, 6, null);
                    return null;
                });

        List<Throwable> errors = runConcurrently(tasks);
        assertThat(errors).filteredOn(Objects::isNull).hasSize(1);
        assertThat(errors)
                .filteredOn(Objects::nonNull)
                .hasSize(1)
                .allSatisfy(error -> assertThat(error)
                        .isInstanceOfSatisfying(
                                ApiException.class,
                                failure -> assertThat(failure.code()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK)));

        assertThat(stock(productId)).isEqualByComparingTo("400.000");
        assertThat(productionMovements(productId)).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from production_batches where id in (?, ?) and status = 'READY'",
                        Integer.class,
                        firstBatch,
                        secondBatch))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from production_batches where id in (?, ?) and status = 'IN_PROGRESS'",
                        Integer.class,
                        firstBatch,
                        secondBatch))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("El almacén no ejecuta ni lee producción; el cocinero sí")
    void respetaLaMatrizDePermisos() throws Exception {
        long productId = createProduct("Permisos ejecución " + next());
        RecipeFixture recipe = publishedRecipe("Receta permisos " + next(), """
                [{"productId":%d,"quantity":10,"unit":"g"}]
                """.formatted(productId));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");
        long itemId = id(replacePlanItems(planId, recipe.versionId(), 1), "$.items[0].id");

        mvc.perform(post("/api/v1/production-plans/" + planId + "/items/" + itemId + "/batches")
                        .header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        String started = mvc.perform(post("/api/v1/production-plans/" + planId + "/items/" + itemId + "/batches")
                        .header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long batchId = id(started, "$.id");

        mvc.perform(get("/api/v1/production-batches/" + batchId).header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/production-batches/" + batchId).header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    private record RecipeFixture(long recipeId, long versionId, String name) {}

    private record MovementRow(
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal balanceAfter,
            Long lotId,
            String referenceType,
            Long referenceId,
            long actorId) {}

    private RecipeFixture draftRecipe(String name, String itemsJson) throws Exception {
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
        return new RecipeFixture(recipeId, versionId, name);
    }

    private RecipeFixture publishedRecipe(String name, String itemsJson) throws Exception {
        RecipeFixture draft = draftRecipe(name, itemsJson);
        mvc.perform(post("/api/v1/recipes/" + draft.recipeId() + "/versions/" + draft.versionId() + "/publish")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        return draft;
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

    private String transitionBatch(long batchId, String target, Integer rations, String reason) throws Exception {
        StringBuilder body = new StringBuilder("{\"status\":\"").append(target).append("\"");
        if (rations != null) {
            body.append(",\"rations\":").append(rations);
        }
        if (reason != null) {
            body.append(",\"deviationReason\":\"").append(reason).append("\"");
        }
        body.append("}");
        return mvc.perform(patch("/api/v1/production-batches/" + batchId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private long createProduct(String name) throws Exception {
        return createProductWithPurchase(name, "1", "2.00", null, null);
    }

    private long createProductWithPurchase(
            String name, String quantityKg, String unitPrice, Instant receivedAt, Instant expiresAt) throws Exception {
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
        purchase(productId, quantityKg, unitPrice, receivedAt, expiresAt);
        return productId;
    }

    private void purchase(long productId, String quantityKg, String unitPrice, Instant receivedAt, Instant expiresAt)
            throws Exception {
        StringBuilder body = new StringBuilder("""
                {"supplierId":%d,"quantity":%s,"unit":"kg","unitPrice":%s
                """.formatted(supplierId, quantityKg, unitPrice));
        if (receivedAt != null) {
            body.append(",\"receivedAt\":\"").append(receivedAt).append("\"");
        }
        if (expiresAt != null) {
            body.append(",\"expiresAt\":\"").append(expiresAt).append("\"");
        }
        body.append("}");
        mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
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

    private BigDecimal stock(long productId) {
        return products.find(establishment.id(), productId).currentStock();
    }

    private String planStatus(long planId) {
        return jdbcTemplate.queryForObject("select status from production_plans where id = ?", String.class, planId);
    }

    private String batchStatus(long batchId) {
        return jdbcTemplate.queryForObject("select status from production_batches where id = ?", String.class, batchId);
    }

    private BigDecimal lotRemaining(long productId, long lotId) {
        return jdbcTemplate.queryForObject(
                "select remaining_qty from stock_lots where product_id = ? and id = ?",
                BigDecimal.class,
                productId,
                lotId);
    }

    private List<MovementRow> productionMovements(long productId) {
        return jdbcTemplate.query(
                """
                select quantity_base, unit_cost, balance_after, lot_id, reference_type, reference_id, actor_id
                from stock_movements
                where product_id = ? and type = 'PRODUCTION_OUT'
                order by id
                """,
                (rs, rowNum) -> new MovementRow(
                        rs.getBigDecimal("quantity_base"),
                        rs.getBigDecimal("unit_cost"),
                        rs.getBigDecimal("balance_after"),
                        rs.getObject("lot_id", Long.class),
                        rs.getString("reference_type"),
                        rs.getObject("reference_id", Long.class),
                        rs.getLong("actor_id")),
                productId);
    }

    private List<String> auditActions(long batchId) {
        return jdbcTemplate.queryForList(
                "select action from audit_logs"
                        + " where entity_type = 'production_batch' and entity_id = ? order by id",
                String.class,
                batchId);
    }

    private LocalDate nextDate() {
        return BASE_DATE.plusDays(next());
    }

    private int next() {
        return ++sequence;
    }

    private static Object json(String body, String path) {
        return JsonPath.read(body, path);
    }

    private static String string(String body, String path) {
        return JsonPath.read(body, path);
    }

    private static long id(String body, String path) {
        return ((Number) json(body, path)).longValue();
    }

    private static long longValue(String body, String path) {
        return ((Number) json(body, path)).longValue();
    }

    private static int intValue(String body, String path) {
        return ((Number) json(body, path)).intValue();
    }

    /**
     * Ejecuta las tareas a la vez tras una barrera común y devuelve el fallo de cada una. Un tiempo
     * límite evita que un bloqueo mal resuelto cuelgue la suite.
     */
    private static List<Throwable> runConcurrently(List<? extends Callable<?>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size(), runnable -> {
            Thread thread = new Thread(runnable, "production-concurrency");
            thread.setDaemon(true);
            return thread;
        });
        CyclicBarrier barrier = new CyclicBarrier(tasks.size());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            List<Throwable> errors = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    errors.add(null);
                } catch (ExecutionException failure) {
                    errors.add(failure.getCause());
                }
            }
            return errors;
        } finally {
            pool.shutdownNow();
        }
    }
}
