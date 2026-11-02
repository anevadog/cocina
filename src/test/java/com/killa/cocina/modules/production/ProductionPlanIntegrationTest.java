package com.killa.cocina.modules.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Plan de producción y necesidades agregadas (PRD-01, PRD-02).
 *
 * <p>Se prueba contra PostgreSQL real: la versión publicada queda fijada en el plan, el duplicado de
 * fecha y servicio se rechaza, los ítems solo se editan mientras el plan está pendiente y las
 * necesidades se calculan con un único redondeo por producto.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductionPlanIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    private static final LocalDate BASE_DATE = LocalDate.of(2031, 1, 1);

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
                        new UserRegistration("prod.manager", "Jefa de cocina", PASSWORD, Role.KITCHEN_MANAGER))
                .id();
        users.create(establishment.id(), null, new UserRegistration("prod.cook", "Cocinero", PASSWORD, Role.COOK));
        users.create(
                establishment.id(), null, new UserRegistration("prod.store", "Almacén", PASSWORD, Role.STOREKEEPER));
        managerToken = loginToken("prod.manager");
        cookToken = loginToken("prod.cook");
        storekeeperToken = loginToken("prod.store");
        categoryId =
                categories.create(establishment.id(), null, "Producción IT").id();
        supplierId = suppliers
                .create(establishment.id(), null, new SupplierRegistration("Proveedor producción IT", null))
                .id();
    }

    @Test
    @DisplayName("PRD-01: hay un plan por fecha y servicio, sin duplicados")
    void creaElPlanDelServicioYRechazaDuplicados() throws Exception {
        LocalDate date = nextDate();
        String lunch = createPlan(date, "LUNCH");

        assertThat(longValue(lunch, "$.id")).isPositive();
        assertThat(string(lunch, "$.serviceDate")).isEqualTo(date.toString());
        assertThat(string(lunch, "$.serviceType")).isEqualTo("LUNCH");
        assertThat(string(lunch, "$.status")).isEqualTo("PENDING");
        assertThat((List<?>) json(lunch, "$.items")).isEmpty();

        mvc.perform(post("/api/v1/production-plans")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(date, "LUNCH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        String dinner = createPlan(date, "DINNER");
        assertThat(longValue(dinner, "$.id")).isNotEqualTo(longValue(lunch, "$.id"));

        String listed = listPlans(date);
        assertThat(longValue(listed, "$.total")).isEqualTo(2);
        assertThat((List<?>) json(listed, "$.content")).hasSize(2);
        assertThat(longValue(listPlans(date.plusDays(1)), "$.total")).isZero();
    }

    @Test
    @DisplayName("Los ítems guardan la versión publicada exacta y su receta")
    void losItemsGuardanLaVersionPublicada() throws Exception {
        RecipeFixture recipe = publishedRecipe(
                "Guiso producción " + next(), """
                [{"productId":%d,"quantity":250,"unit":"g","wastePct":10}]
                """.formatted(createProduct("Harina producción " + next())));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");

        String body = replacePlanItems(planId, """
                {"items":[{"recipeVersionId":%d,"plannedRations":10}]}
                """.formatted(recipe.versionId()), managerToken);

        assertThat(longValue(body, "$.items[0].id")).isPositive();
        assertThat(longValue(body, "$.items[0].recipeId")).isEqualTo(recipe.recipeId());
        assertThat(string(body, "$.items[0].recipeName")).isEqualTo(recipe.name());
        assertThat(longValue(body, "$.items[0].versionId")).isEqualTo(recipe.versionId());
        assertThat(intValue(body, "$.items[0].versionNumber")).isEqualTo(1);
        assertThat(intValue(body, "$.items[0].plannedRations")).isEqualTo(10);
        assertThat(string(body, "$.items[0].status")).isEqualTo("PENDING");
        assertThat(json(body, "$.items[0].batch")).isNull();

        String fetched = mvc.perform(
                        get("/api/v1/production-plans/" + planId).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat((List<?>) json(fetched, "$.items")).hasSize(1);
        assertThat(longValue(fetched, "$.items[0].versionId")).isEqualTo(recipe.versionId());
    }

    @Test
    @DisplayName("No se planifica un borrador ni una receta archivada")
    void soloSePlanificanVersionesPublicadasDeRecetasActivas() throws Exception {
        long productId = createProduct("Sal producción " + next());
        RecipeFixture draft = draftRecipe("Borrador producción " + next(), """
                [{"productId":%d,"quantity":3,"unit":"g"}]
                """.formatted(productId));
        long draftPlan = id(createPlan(nextDate(), "LUNCH"), "$.id");

        mvc.perform(put("/api/v1/production-plans/" + draftPlan + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsBody(draft.versionId(), 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no está publicada")));

        RecipeFixture archived = publishedRecipe("Receta archivada " + next(), """
                [{"productId":%d,"quantity":3,"unit":"g"}]
                """.formatted(productId));
        mvc.perform(patch("/api/v1/recipes/" + archived.recipeId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        long archivedPlan = id(createPlan(nextDate(), "LUNCH"), "$.id");
        mvc.perform(put("/api/v1/production-plans/" + archivedPlan + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsBody(archived.versionId(), 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("archivada")));
    }

    @Test
    @DisplayName("El plan no repite la misma versión ni dos versiones de la misma receta")
    void elPlanNoRepiteVersionesNiRecetas() throws Exception {
        RecipeFixture first = publishedRecipe(
                "Receta versionada " + next(), """
                [{"productId":%d,"quantity":10,"unit":"g"}]
                """.formatted(createProduct("Producto versionado " + next())));
        long secondVersion = createVersion(first.recipeId());
        publishVersion(first.recipeId(), secondVersion);
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");

        mvc.perform(put("/api/v1/production-plans/" + planId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                    {"recipeVersionId":%d,"plannedRations":1},
                                    {"recipeVersionId":%d,"plannedRations":1}
                                ]}
                                """.formatted(first.versionId(), secondVersion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("recipeVersionId"));

        mvc.perform(put("/api/v1/production-plans/" + planId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                    {"recipeVersionId":%d,"plannedRations":1},
                                    {"recipeVersionId":%d,"plannedRations":2}
                                ]}
                                """.formatted(first.versionId(), first.versionId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        long missingVersion = 999_999_999L;
        mvc.perform(put("/api/v1/production-plans/" + planId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsBody(missingVersion, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    }

    @Test
    @DisplayName("PRD-02: las necesidades se agregan por producto y se redondean una sola vez")
    void agregaLasNecesidadesPorProducto() throws Exception {
        long shared = createProduct("Producto agregado " + next());
        long extra = createProduct("Producto extra " + next());

        // 0,001 g al 50 % = 0,0015 g por línea. Dos recetas suman 0,0030 → 0,003, no 0,004.
        RecipeFixture first = publishedRecipe("Agregado A " + next(), """
                [
                    {"productId":%d,"quantity":0.001,"unit":"g","wastePct":50},
                    {"productId":%d,"quantity":100,"unit":"g","wastePct":10}
                ]
                """.formatted(shared, extra));
        RecipeFixture second = publishedRecipe("Agregado B " + next(), """
                [{"productId":%d,"quantity":0.001,"unit":"g","wastePct":50}]
                """.formatted(shared));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");
        replacePlanItems(planId, """
                {"items":[
                    {"recipeVersionId":%d,"plannedRations":1},
                    {"recipeVersionId":%d,"plannedRations":1}
                ]}
                """.formatted(first.versionId(), second.versionId()), managerToken);

        String requirements = mvc.perform(get("/api/v1/production-plans/" + planId + "/requirements")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(longValue(requirements, "$.planId")).isEqualTo(planId);
        assertThat((List<?>) json(requirements, "$.lines")).hasSize(2);
        assertThat(requirement(requirements, shared)).isEqualByComparingTo("0.003");
        assertThat(requirement(requirements, extra)).isEqualByComparingTo("110.000");
        assertThat(lineString(requirements, shared, "unit")).isEqualTo("g");
    }

    @Test
    @DisplayName("Los ítems se sustituyen en bloque y se pueden quitar de uno en uno")
    void editaYEliminaItemsMientrasElPlanEstaPendiente() throws Exception {
        String product = "Producto edición " + next();
        RecipeFixture first = publishedRecipe("Edición A " + next(), """
                [{"productId":%d,"quantity":1,"unit":"g"}]
                """.formatted(createProduct(product)));
        RecipeFixture second = publishedRecipe("Edición B " + next(), """
                [{"productId":%d,"quantity":1,"unit":"g"}]
                """.formatted(createProduct("Otro " + product)));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");

        String both = replacePlanItems(planId, """
                {"items":[
                    {"recipeVersionId":%d,"plannedRations":4},
                    {"recipeVersionId":%d,"plannedRations":6}
                ]}
                """.formatted(first.versionId(), second.versionId()), managerToken);
        assertThat((List<?>) json(both, "$.items")).hasSize(2);

        String onlyFirst = replacePlanItems(planId, itemsBody(first.versionId(), 5), managerToken);
        assertThat((List<?>) json(onlyFirst, "$.items")).hasSize(1);
        assertThat(intValue(onlyFirst, "$.items[0].plannedRations")).isEqualTo(5);
        long itemId = id(onlyFirst, "$.items[0].id");

        String emptied = mvc.perform(delete("/api/v1/production-plans/" + planId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat((List<?>) json(emptied, "$.items")).isEmpty();

        mvc.perform(delete("/api/v1/production-plans/" + planId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PRD-04: cancelar no deja editar el plan y no admite otros estados manuales")
    void cancelaElPlanPendiente() throws Exception {
        RecipeFixture recipe = publishedRecipe(
                "Cancelación " + next(), """
                [{"productId":%d,"quantity":1,"unit":"g"}]
                """.formatted(createProduct("Producto cancelación " + next())));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");
        replacePlanItems(planId, itemsBody(recipe.versionId(), 3), managerToken);

        String cancelled = mvc.perform(patch("/api/v1/production-plans/" + planId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(string(cancelled, "$.status")).isEqualTo("CANCELLED");
        assertThat(string(cancelled, "$.items[0].status")).isEqualTo("CANCELLED");

        mvc.perform(put("/api/v1/production-plans/" + planId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsBody(recipe.versionId(), 4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        mvc.perform(delete("/api/v1/production-plans/" + planId).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        mvc.perform(patch("/api/v1/production-plans/" + planId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(patch("/api/v1/production-plans/999999999")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Borrar un plan pendiente elimina sus ítems y deja auditoría")
    void borraElPlanPendiente() throws Exception {
        RecipeFixture recipe =
                publishedRecipe("Borrado " + next(), """
                [{"productId":%d,"quantity":1,"unit":"g"}]
                """.formatted(createProduct("Producto borrado " + next())));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");
        replacePlanItems(planId, itemsBody(recipe.versionId(), 3), managerToken);

        mvc.perform(delete("/api/v1/production-plans/" + planId).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/production-plans/" + planId).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/production-plans/" + planId + "/requirements")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from production_plan_items where plan_id = ?", Integer.class, planId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from audit_logs where action = 'PRODUCTION_PLAN_DELETED'"
                                + " and entity_type = 'production_plan' and entity_id = ? and actor_id = ?",
                        Integer.class,
                        planId,
                        managerId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Cada cambio del plan queda auditado con su actor")
    void auditaLosCambiosDelPlan() throws Exception {
        RecipeFixture recipe =
                publishedRecipe("Auditoría " + next(), """
                [{"productId":%d,"quantity":1,"unit":"g"}]
                """.formatted(createProduct("Producto auditoría " + next())));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");
        replacePlanItems(planId, itemsBody(recipe.versionId(), 7), managerToken);
        mvc.perform(patch("/api/v1/production-plans/" + planId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        List<String> actions = jdbcTemplate.queryForList(
                "select action from audit_logs where entity_type = 'production_plan' and entity_id = ? order by id",
                String.class,
                planId);
        assertThat(actions)
                .containsExactly("PRODUCTION_PLAN_CREATED", "PRODUCTION_PLAN_UPDATED", "PRODUCTION_PLAN_CANCELLED");

        String after = jdbcTemplate.queryForObject(
                "select after_json from audit_logs where action = 'PRODUCTION_PLAN_UPDATED' and entity_id = ?",
                String.class,
                planId);
        assertThat(after).contains(String.valueOf(recipe.versionId())).contains("\"plannedRations\": 7");
    }

    @Test
    @DisplayName("El almacén no planifica; el cocinero sí")
    void respetaLaMatrizDePermisos() throws Exception {
        LocalDate date = nextDate();
        mvc.perform(post("/api/v1/production-plans")
                        .header("Authorization", "Bearer " + storekeeperToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(date, "LUNCH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/v1/production-plans?serviceDate=" + date)
                        .header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isForbidden());

        String created = mvc.perform(post("/api/v1/production-plans")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(date, "LUNCH")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long planId = id(created, "$.id");

        RecipeFixture recipe =
                publishedRecipe("Cocinero " + next(), """
                [{"productId":%d,"quantity":1,"unit":"g"}]
                """.formatted(createProduct("Producto cocinero " + next())));
        replacePlanItems(planId, itemsBody(recipe.versionId(), 2), cookToken);
    }

    @Test
    @DisplayName("El plan exige fecha y servicio, y la lista exige fecha")
    void validaLaPeticion() throws Exception {
        mvc.perform(post("/api/v1/production-plans")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(post("/api/v1/production-plans")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"2031-01-01","serviceType":"BRUNCH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mvc.perform(get("/api/v1/production-plans").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("El trigger de BD rechaza planificar una versión en borrador")
    void elTriggerSoloAdmiteVersionesPublicadas() throws Exception {
        long productId = createProduct("Producto trigger " + next());
        RecipeFixture draft = draftRecipe("Trigger producción " + next(), """
                [{"productId":%d,"quantity":1,"unit":"g"}]
                """.formatted(productId));
        long planId = id(createPlan(nextDate(), "LUNCH"), "$.id");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into production_plan_items"
                                + " (establishment_id, plan_id, recipe_version_id, planned_rations)"
                                + " values (?, ?, ?, 1)",
                        establishment.id(),
                        planId,
                        draft.versionId()))
                .isInstanceOf(DataAccessException.class);
    }

    private record RecipeFixture(long recipeId, long versionId, String name) {}

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
        publishVersion(draft.recipeId(), draft.versionId());
        return draft;
    }

    private long createVersion(long recipeId) throws Exception {
        String body = mvc.perform(post("/api/v1/recipes/" + recipeId + "/versions")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return id(body, "$.id");
    }

    private void publishVersion(long recipeId, long versionId) throws Exception {
        mvc.perform(post("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/publish")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    private String createPlan(LocalDate serviceDate, String serviceType) throws Exception {
        return mvc.perform(post("/api/v1/production-plans")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(serviceDate, serviceType)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String listPlans(LocalDate serviceDate) throws Exception {
        return mvc.perform(get("/api/v1/production-plans?serviceDate=" + serviceDate)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String replacePlanItems(long planId, String body, String token) throws Exception {
        return mvc.perform(put("/api/v1/production-plans/" + planId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
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
        purchase(productId, "1", "kg", "1.00");
        return productId;
    }

    private void purchase(long productId, String quantity, String unit, String unitPrice) throws Exception {
        mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":%d,"quantity":%s,"unit":"%s","unitPrice":%s}
                                """.formatted(supplierId, quantity, unit, unitPrice)))
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

    private static String planBody(LocalDate serviceDate, String serviceType) {
        return """
                {"serviceDate":"%s","serviceType":"%s"}
                """.formatted(serviceDate, serviceType);
    }

    private static String itemsBody(long versionId, int plannedRations) {
        return """
                {"items":[{"recipeVersionId":%d,"plannedRations":%d}]}
                """.formatted(versionId, plannedRations);
    }

    private static BigDecimal requirement(String body, long productId) {
        List<Number> values = JsonPath.read(body, "$.lines[?(@.productId == " + productId + ")].quantity");
        return new BigDecimal(values.getFirst().toString());
    }

    private static String lineString(String body, long productId, String field) {
        List<String> values = JsonPath.read(body, "$.lines[?(@.productId == " + productId + ")]." + field);
        return values.getFirst();
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
}
