package com.killa.cocina.modules.recipes;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Receta, borrador e ingredientes de punta a punta (REC-01, REC-02, REC-05).
 *
 * <p>Cubre el ciclo del borrador antes de publicar, la conversión de unidades en el borde, el
 * nombre único, el archivado y el RBAC contra PostgreSQL real.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecipeDraftIntegrationTest extends AbstractIntegrationTest {

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
    private EstablishmentProperties establishment;

    private long categoryId;

    private String managerToken;

    private String cookToken;

    private String storekeeperToken;

    private int sequence;

    @BeforeAll
    void prepararDatos() throws Exception {
        users.create(
                establishment.id(),
                null,
                new UserRegistration("recipe.manager", "Jefa de cocina", PASSWORD, Role.KITCHEN_MANAGER));
        users.create(establishment.id(), null, new UserRegistration("recipe.cook", "Cocinera", PASSWORD, Role.COOK));
        users.create(
                establishment.id(), null, new UserRegistration("recipe.store", "Almacén", PASSWORD, Role.STOREKEEPER));
        managerToken = loginToken("recipe.manager");
        cookToken = loginToken("recipe.cook");
        storekeeperToken = loginToken("recipe.store");
        categoryId = categories.create(establishment.id(), null, "Recetas IT").id();
    }

    @Test
    @DisplayName("Crear una receta crea su primera versión en borrador")
    void creaLaRecetaConSuPrimeraVersionEnBorrador() throws Exception {
        String body = createRecipe("Tarta de queso " + next(), 8);

        assertThat(id(body, "$.id")).isPositive();
        assertThat(json(body, "$.category")).isEqualTo("Postres");
        assertThat(json(body, "$.currentVersion")).isNull();
        assertThat(intValue(body, "$.draft.versionNumber")).isEqualTo(1);
        assertThat(json(body, "$.draft.status")).isEqualTo("DRAFT");
        assertThat(intValue(body, "$.draft.baseRations")).isEqualTo(8);
        assertThat(json(body, "$.draft.costPerRation")).isNull();

        String fetched = mvc.perform(
                        get("/api/v1/recipes/" + id(body, "$.id")).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(intValue(fetched, "$.draft.versionNumber")).isEqualTo(1);
    }

    @Test
    @DisplayName("El nombre de la receta es único por establecimiento")
    void elNombreEsUnicoPorEstablecimiento() throws Exception {
        String name = "Tarta duplicada " + next();
        createRecipe(name, 4);

        mvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recipeBody(name, 4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));
    }

    @Test
    @DisplayName("Los ingredientes se guardan por ración en unidad base, con su merma y su orden")
    void losIngredientesSeConviertenAUnidadBase() throws Exception {
        String recipe = createRecipe("Bizcocho " + next(), 4);
        long recipeId = id(recipe, "$.id");
        long versionId = id(recipe, "$.draft.id");
        String flourName = "Harina recetas " + next();
        long flour = createProduct(flourName);
        long salt = createProduct("Sal recetas " + next());

        String body = replaceItems(recipeId, versionId, """
                {"items":[
                    {"productId":%d,"quantity":0.25,"unit":"kg","wastePct":10},
                    {"productId":%d,"quantity":3,"unit":"g"}
                ]}
                """.formatted(flour, salt));

        assertThat(decimal(body, "$.items[0].quantityBase")).isEqualByComparingTo("250.000");
        assertThat(decimal(body, "$.items[0].wastePct")).isEqualByComparingTo("10.00");
        assertThat(json(body, "$.items[0].unit")).isEqualTo("g");
        assertThat(json(body, "$.items[0].productName")).isEqualTo(flourName);
        assertThat(intValue(body, "$.items[0].sortOrder")).isEqualTo(0);
        assertThat(decimal(body, "$.items[1].quantityBase")).isEqualByComparingTo("3.000");
        assertThat(decimal(body, "$.items[1].wastePct")).isEqualByComparingTo("0.00");
        assertThat(intValue(body, "$.items[1].sortOrder")).isEqualTo(1);

        String fetched = mvc.perform(get("/api/v1/recipes/" + recipeId + "/versions/" + versionId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(decimal(fetched, "$.items[0].quantityBase")).isEqualByComparingTo("250.000");
    }

    @Test
    @DisplayName("Editar la cabecera del borrador cambia raciones, rendimiento, tiempo e instrucciones")
    void editaLaCabeceraDelBorrador() throws Exception {
        String recipe = createRecipe("Salsa " + next(), 4);
        long recipeId = id(recipe, "$.id");
        long versionId = id(recipe, "$.draft.id");

        String body = mvc.perform(patch("/api/v1/recipes/" + recipeId + "/versions/" + versionId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseRations":6,"yieldPct":85.5,"prepTimeMinutes":45,
                                 "instructions":"Sofreír y triturar."}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(intValue(body, "$.baseRations")).isEqualTo(6);
        assertThat(decimal(body, "$.yieldPct")).isEqualByComparingTo("85.50");
        assertThat(intValue(body, "$.prepTimeMinutes")).isEqualTo(45);

        String cleared = mvc.perform(patch("/api/v1/recipes/" + recipeId + "/versions/" + versionId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json(cleared, "$.instructions")).isNull();
    }

    @Test
    @DisplayName("Archivar una receta impide versionarla y la conserva en el listado")
    void archivarImpideVersionarYConservaElHistorial() throws Exception {
        String name = "Receta archivada " + next();
        String recipe = createRecipe(name, 2);
        long recipeId = id(recipe, "$.id");

        String archived = mvc.perform(patch("/api/v1/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"category\":\"\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json(archived, "$.active")).isEqualTo(false);
        assertThat(json(archived, "$.category")).isNull();

        mvc.perform(post("/api/v1/recipes/" + recipeId + "/versions").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        String listed = mvc.perform(get("/api/v1/recipes")
                        .param("name", name)
                        .param("active", "false")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(((Number) json(listed, "$.total")).longValue()).isEqualTo(1L);
        assertThat(intValue(listed, "$.content[0].draft.versionNumber")).isEqualTo(1);
    }

    @Test
    @DisplayName("Solo puede haber un borrador y la única versión no se descarta")
    void soloPuedeHaberUnBorradorYNoSeDescartaLaUnicaVersion() throws Exception {
        String recipe = createRecipe("Un solo borrador " + next(), 4);
        long recipeId = id(recipe, "$.id");
        long versionId = id(recipe, "$.draft.id");

        mvc.perform(post("/api/v1/recipes/" + recipeId + "/versions").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        mvc.perform(delete("/api/v1/recipes/" + recipeId + "/versions/" + versionId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    }

    @Test
    @DisplayName("El borrador valida unidades, productos repetidos y datos de entrada")
    void elBorradorValidaSusDatos() throws Exception {
        String recipe = createRecipe("Validación recetas " + next(), 4);
        long recipeId = id(recipe, "$.id");
        long versionId = id(recipe, "$.draft.id");
        long productId = createProduct("Producto validación " + next());

        mvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recipeBody("Raciones inválidas " + next(), 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(put("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":%d,"quantity":1,"unit":"g","wastePct":100}]}
                                """.formatted(productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(put("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":999999,"quantity":1,"unit":"g"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(put("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                    {"productId":%d,"quantity":1,"unit":"g"},
                                    {"productId":%d,"quantity":2,"unit":"g"}
                                ]}
                                """.formatted(productId, productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(put("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":%d,"quantity":1,"unit":"ml"}]}
                                """.formatted(productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNIT_INCOMPATIBLE"));
    }

    @Test
    @DisplayName("Cocina y almacén consultan recetas, pero solo la jefatura las escribe")
    void elCocineroYElAlmacenSoloLeen() throws Exception {
        String recipe = createRecipe("Permisos recetas " + next(), 4);
        long recipeId = id(recipe, "$.id");

        mvc.perform(get("/api/v1/recipes/" + recipeId).header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/recipes/" + recipeId).header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recipeBody("Receta de cocina " + next(), 4)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + storekeeperToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recipeBody("Receta de almacén " + next(), 4)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(patch("/api/v1/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Intento de cocina\"}"))
                .andExpect(status().isForbidden());
    }

    private String createRecipe(String name, int baseRations) throws Exception {
        return mvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recipeBody(name, baseRations)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String replaceItems(long recipeId, long versionId, String body) throws Exception {
        return mvc.perform(put("/api/v1/recipes/" + recipeId + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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

    private static String recipeBody(String name, int baseRations) {
        return """
                {"name":"%s","category":"Postres","baseRations":%d,"yieldPct":90,
                 "prepTimeMinutes":30,"instructions":"Mezclar y servir."}
                """.formatted(name, baseRations);
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

    private static int intValue(String body, String path) {
        return ((Number) json(body, path)).intValue();
    }

    private static long id(String body, String path) {
        return ((Number) json(body, path)).longValue();
    }

    private static BigDecimal decimal(String body, String path) {
        Object value = json(body, path);
        return value == null ? null : new BigDecimal(value.toString());
    }
}
