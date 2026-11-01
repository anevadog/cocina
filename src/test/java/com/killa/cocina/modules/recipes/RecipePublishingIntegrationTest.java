package com.killa.cocina.modules.recipes;

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
import com.killa.cocina.modules.inventory.domain.ProductModification;
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
 * Publicación, congelado del coste e inmutabilidad de versiones (REC-04, REC-05, ECO-01).
 *
 * <p>Lo que se prueba es la promesa del producto: el coste de una versión publicada no cambia nunca,
 * ni por una compra posterior, ni por la API, ni por SQL directo.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecipePublishingIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

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

    private int sequence;

    @BeforeAll
    void prepararDatos() throws Exception {
        managerId = users.create(
                        establishment.id(),
                        null,
                        new UserRegistration("pub.manager", "Jefa de cocina", PASSWORD, Role.KITCHEN_MANAGER))
                .id();
        users.create(establishment.id(), null, new UserRegistration("pub.cook", "Cocinera", PASSWORD, Role.COOK));
        managerToken = loginToken("pub.manager");
        cookToken = loginToken("pub.cook");
        categoryId = categories
                .create(establishment.id(), null, "Publicación recetas IT")
                .id();
        supplierId = suppliers
                .create(
                        establishment.id(),
                        null,
                        new SupplierRegistration("Proveedor publicación IT", "compras@example.test"))
                .id();
    }

    @Test
    @DisplayName("Publicar congela el coste y marca la versión como vigente")
    void publicaYCongelaElCoste() throws Exception {
        RecipeFixture recipe = draftWithIngredients();

        String body = publish(recipe);
        assertThat(string(body, "$.status")).isEqualTo("PUBLISHED");
        assertThat(decimal(body, "$.frozenCostPerRation")).isEqualByComparingTo("2.7530");
        assertThat(decimal(body, "$.frozenTotalCost")).isEqualByComparingTo("11.0120");
        assertThat(string(body, "$.publishedAt")).isNotNull();
        assertThat(longValue(body, "$.publishedBy")).isEqualTo(managerId);

        String recipeBody = recipe(recipe.recipeId());
        assertThat(longValue(recipeBody, "$.currentVersion.id")).isEqualTo(recipe.versionId());
        assertThat(decimal(recipeBody, "$.currentVersion.costPerRation")).isEqualByComparingTo("2.7530");
        assertThat(json(recipeBody, "$.draft")).isNull();
    }

    @Test
    @DisplayName("Una compra posterior mueve el coste actual pero no el congelado")
    void elCosteCongeladoNoCambiaConElPmp() throws Exception {
        RecipeFixture recipe = draftWithIngredients();
        publish(recipe);

        purchase(recipe.flourId(), "1", "kg", "20.00");

        String body = version(recipe);
        assertThat(decimal(body, "$.frozenCostPerRation")).isEqualByComparingTo("2.7530");
        assertThat(decimal(body, "$.frozenTotalCost")).isEqualByComparingTo("11.0120");
        assertThat(decimal(body, "$.currentCostPerRation")).isEqualByComparingTo("4.1280");
        assertThat(decimal(body, "$.currentTotalCost")).isEqualByComparingTo("16.5120");

        String history = versions(recipe.recipeId());
        assertThat(decimal(history, "$[0].costPerRation")).isEqualByComparingTo("2.7530");
        assertThat(decimal(history, "$[0].totalCost")).isEqualByComparingTo("11.0120");
    }

    @Test
    @DisplayName("Una versión publicada no se edita, no se borra y no se vuelve a publicar")
    void unaVersionPublicadaEsInmutablePorApi() throws Exception {
        RecipeFixture recipe = draftWithIngredients();
        publish(recipe);

        mvc.perform(patch("/api/v1/recipes/" + recipe.recipeId() + "/versions/" + recipe.versionId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRations\":12}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IMMUTABLE"));

        mvc.perform(put("/api/v1/recipes/" + recipe.recipeId() + "/versions/" + recipe.versionId() + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":%d,"quantity":1,"unit":"g"}]}
                                """.formatted(recipe.flourId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IMMUTABLE"));

        mvc.perform(delete("/api/v1/recipes/" + recipe.recipeId() + "/versions/" + recipe.versionId())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IMMUTABLE"));

        mvc.perform(post("/api/v1/recipes/" + recipe.recipeId() + "/versions/" + recipe.versionId() + "/publish")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IMMUTABLE"));
    }

    @Test
    @DisplayName("El trigger de BD rechaza tocar una versión publicada o sus ingredientes")
    void elTriggerProtegeLaVersionPublicada() throws Exception {
        RecipeFixture recipe = draftWithIngredients();
        publish(recipe);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "update recipe_versions set base_rations = 12 where id = ?", recipe.versionId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("delete from recipe_versions where id = ?", recipe.versionId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "update recipe_items set quantity_base = 1 where version_id = ?", recipe.versionId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into recipe_items (establishment_id, version_id, product_id, quantity_base, waste_pct, sort_order)"
                                + " values (?, ?, ?, 1, 0, 99)",
                        establishment.id(),
                        recipe.versionId(),
                        recipe.flourId()))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from recipe_versions where id = ?", Integer.class, recipe.versionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select base_rations from recipe_versions where id = ?", Integer.class, recipe.versionId()))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("Tras publicar se clona la versión con sus ingredientes y solo hay un borrador")
    void clonaLaVersionPublicada() throws Exception {
        RecipeFixture recipe = draftWithIngredients();
        publish(recipe);

        String clone = mvc.perform(post("/api/v1/recipes/" + recipe.recipeId() + "/versions")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(intValue(clone, "$.versionNumber")).isEqualTo(2);
        assertThat(string(clone, "$.status")).isEqualTo("DRAFT");
        assertThat(json(clone, "$.frozenCostPerRation")).isNull();
        assertThat((List<?>) json(clone, "$.items")).hasSize(2);
        assertThat(decimal(clone, "$.items[0].quantityBase")).isEqualByComparingTo("250.000");
        assertThat(decimal(clone, "$.items[0].wastePct")).isEqualByComparingTo("10.00");

        mvc.perform(post("/api/v1/recipes/" + recipe.recipeId() + "/versions")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    }

    @Test
    @DisplayName("Publicar la versión nueva mueve la vigente sin tocar el coste de la anterior")
    void laVersionNuevaPasaASerLaVigente() throws Exception {
        RecipeFixture recipe = draftWithIngredients();
        publish(recipe);
        long secondVersion = createVersion(recipe.recipeId());

        mvc.perform(patch("/api/v1/recipes/" + recipe.recipeId() + "/versions/" + secondVersion)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRations\":8}"))
                .andExpect(status().isOk());
        purchase(recipe.flourId(), "1", "kg", "20.00");
        publishVersion(recipe.recipeId(), secondVersion);

        String recipeBody = recipe(recipe.recipeId());
        assertThat(longValue(recipeBody, "$.currentVersion.id")).isEqualTo(secondVersion);
        assertThat(intValue(recipeBody, "$.currentVersion.versionNumber")).isEqualTo(2);
        assertThat(decimal(recipeBody, "$.currentVersion.costPerRation")).isEqualByComparingTo("4.1280");
        assertThat(decimal(recipeBody, "$.currentVersion.totalCost")).isEqualByComparingTo("33.0240");

        String first = version(recipe.recipeId(), recipe.versionId());
        assertThat(string(first, "$.status")).isEqualTo("PUBLISHED");
        assertThat(decimal(first, "$.frozenCostPerRation")).isEqualByComparingTo("2.7530");
        assertThat(decimal(first, "$.frozenTotalCost")).isEqualByComparingTo("11.0120");
    }

    @Test
    @DisplayName("Descartar el borrador deja intacta la versión publicada")
    void descartarElBorradorNoTocaLaPublicada() throws Exception {
        RecipeFixture recipe = draftWithIngredients();
        publish(recipe);
        long secondVersion = createVersion(recipe.recipeId());

        mvc.perform(delete("/api/v1/recipes/" + recipe.recipeId() + "/versions/" + secondVersion)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNoContent());

        String history = versions(recipe.recipeId());
        assertThat((List<?>) json(history, "$")).hasSize(1);
        assertThat(intValue(history, "$[0].versionNumber")).isEqualTo(1);
        assertThat(decimal(history, "$[0].costPerRation")).isEqualByComparingTo("2.7530");
        assertThat(longValue(recipe(recipe.recipeId()), "$.currentVersion.id")).isEqualTo(recipe.versionId());
    }

    @Test
    @DisplayName("Publicar exige ingredientes, receta activa y productos activos")
    void publicarValidaElEstadoDeLaReceta() throws Exception {
        // Sin ingredientes.
        String empty = mvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Receta vacía %d","baseRations":2}
                                """.formatted(next())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(post("/api/v1/recipes/" + id(empty, "$.id") + "/versions/" + id(empty, "$.draft.id") + "/publish")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        // Receta archivada.
        RecipeFixture archivedRecipe = draftWithIngredients();
        mvc.perform(patch("/api/v1/recipes/" + archivedRecipe.recipeId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/recipes/" + archivedRecipe.recipeId() + "/versions/" + archivedRecipe.versionId()
                                + "/publish")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        // Producto archivado.
        RecipeFixture inactiveProduct = draftWithUnstockedSalt();
        products.update(
                establishment.id(),
                null,
                inactiveProduct.saltId(),
                new ProductModification(null, null, null, null, false));
        mvc.perform(post("/api/v1/recipes/" + inactiveProduct.recipeId() + "/versions/" + inactiveProduct.versionId()
                                + "/publish")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("está archivado")));
    }

    @Test
    @DisplayName("Solo quien puede escribir recetas puede publicar")
    void elCocineroNoPublica() throws Exception {
        RecipeFixture recipe = draftWithIngredients();

        mvc.perform(post("/api/v1/recipes/" + recipe.recipeId() + "/versions/" + recipe.versionId() + "/publish")
                        .header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Publicar deja una fila de auditoría con el coste congelado")
    void laPublicacionQuedaAuditada() throws Exception {
        RecipeFixture recipe = draftWithIngredients();
        publish(recipe);

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where action = 'RECIPE_VERSION_PUBLISHED'"
                        + " and entity_type = 'recipe_version' and entity_id = ? and actor_id = ?",
                Integer.class,
                recipe.versionId(),
                managerId);
        assertThat(rows).isEqualTo(1);

        String after = jdbcTemplate.queryForObject(
                "select after_json from audit_logs where action = 'RECIPE_VERSION_PUBLISHED'" + " and entity_id = ?",
                String.class,
                recipe.versionId());
        assertThat(after).contains("2.7530").contains("11.0120");
    }

    private record RecipeFixture(long recipeId, long versionId, long flourId, long saltId) {}

    /**
     * Receta con 250 g de harina al 10 % (0,0100 €/g) y 3 g de sal (0,0010 €/g): 2,7530 € por ración y
     * 11,0120 € el lote de 4.
     */
    private RecipeFixture draftWithIngredients() throws Exception {
        return draft(true);
    }

    /** Igual, pero la sal no tiene stock: así se puede archivar el producto para probar el bloqueo. */
    private RecipeFixture draftWithUnstockedSalt() throws Exception {
        return draft(false);
    }

    private RecipeFixture draft(boolean stockSalt) throws Exception {
        long flour = createProduct("Harina publicación " + next());
        long salt = createProduct("Sal publicación " + next());
        purchase(flour, "1", "kg", "10.00");
        if (stockSalt) {
            purchase(salt, "1", "kg", "1.00");
        }

        String recipe = mvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Receta publicación %d","baseRations":4}
                                """.formatted(next())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long recipeId = id(recipe, "$.id");
        long versionId = id(recipe, "$.draft.id");

        mvc.perform(put("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                    {"productId":%d,"quantity":250,"unit":"g","wastePct":10},
                                    {"productId":%d,"quantity":3,"unit":"g"}
                                ]}
                                """.formatted(flour, salt)))
                .andExpect(status().isOk());
        return new RecipeFixture(recipeId, versionId, flour, salt);
    }

    private String publish(RecipeFixture recipe) throws Exception {
        return publishVersion(recipe.recipeId(), recipe.versionId());
    }

    private String publishVersion(long recipeId, long versionId) throws Exception {
        return mvc.perform(post("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/publish")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
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

    private String recipe(long recipeId) throws Exception {
        return mvc.perform(get("/api/v1/recipes/" + recipeId).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String versions(long recipeId) throws Exception {
        return mvc.perform(get("/api/v1/recipes/" + recipeId + "/versions")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String version(RecipeFixture recipe) throws Exception {
        return version(recipe.recipeId(), recipe.versionId());
    }

    private String version(long recipeId, long versionId) throws Exception {
        return mvc.perform(get("/api/v1/recipes/" + recipeId + "/versions/" + versionId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

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

    private static BigDecimal decimal(String body, String path) {
        Object value = json(body, path);
        return value == null ? null : new BigDecimal(value.toString());
    }
}
