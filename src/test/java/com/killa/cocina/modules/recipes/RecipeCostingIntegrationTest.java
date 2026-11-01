package com.killa.cocina.modules.recipes;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Coste vigente y escalado contra PostgreSQL real (REC-03, REC-04, ECO-01).
 *
 * <p>Los PMP se construyen con compras reales para no simular el coste: lo que se prueba es el mismo
 * dato que verá el descuento de producción en F5.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecipeCostingIntegrationTest extends AbstractIntegrationTest {

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

    private String storekeeperToken;

    private int sequence;

    @BeforeAll
    void prepararDatos() throws Exception {
        users.create(
                establishment.id(),
                null,
                new UserRegistration("cost.manager", "Jefa de cocina", PASSWORD, Role.KITCHEN_MANAGER));
        users.create(establishment.id(), null, new UserRegistration("cost.cook", "Cocinera", PASSWORD, Role.COOK));
        users.create(
                establishment.id(), null, new UserRegistration("cost.store", "Almacén", PASSWORD, Role.STOREKEEPER));
        managerToken = loginToken("cost.manager");
        cookToken = loginToken("cost.cook");
        storekeeperToken = loginToken("cost.store");
        categoryId =
                categories.create(establishment.id(), null, "Coste recetas IT").id();
        supplierId = suppliers
                .create(
                        establishment.id(),
                        null,
                        new SupplierRegistration("Proveedor coste IT", "compras@example.test"))
                .id();
    }

    @Test
    @DisplayName("El coste actual de la versión sale del PMP vigente y el borrador no tiene coste congelado")
    void elCosteActualUsaElPmpVigente() throws Exception {
        RecipeFixture recipe = recipeWithFlourAndSalt();

        String body = version(recipe);
        assertThat(decimal(body, "$.frozenCostPerRation")).isNull();
        assertThat(decimal(body, "$.frozenTotalCost")).isNull();
        // 250 g al 10 % de merma a 0,0100 €/g = 2,7500; 3 g a 0,0010 €/g = 0,0030.
        assertThat(decimal(body, "$.currentCostPerRation")).isEqualByComparingTo("2.7530");
        // 4 raciones base: 2,7530 × 4 = 11,0120.
        assertThat(decimal(body, "$.currentTotalCost")).isEqualByComparingTo("11.0120");
    }

    @Test
    @DisplayName("El escalado multiplica cantidades y lote, y mantiene el coste por ración")
    void elEscaladoMultiplicaLasCantidades() throws Exception {
        RecipeFixture recipe = recipeWithFlourAndSalt();

        String body = mvc.perform(
                        get(scalePath(recipe) + "?rations=3").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(intValue(body, "$.rations")).isEqualTo(3);
        // 250 g × 1,10 × 3 = 825,000 g; 3 g × 3 = 9,000 g.
        assertThat(decimal(body, "$.ingredients[0].quantityBase")).isEqualByComparingTo("825.000");
        assertThat(decimal(body, "$.ingredients[0].cost")).isEqualByComparingTo("8.2500");
        assertThat(decimal(body, "$.ingredients[1].quantityBase")).isEqualByComparingTo("9.000");
        assertThat(decimal(body, "$.ingredients[1].cost")).isEqualByComparingTo("0.0090");
        // El coste por ración no cambia con el número de raciones; el lote sí.
        assertThat(decimal(body, "$.costPerRation")).isEqualByComparingTo("2.7530");
        assertThat(decimal(body, "$.totalCost")).isEqualByComparingTo("8.2590");
        assertThat(boolValue(body, "$.costComplete")).isTrue();
    }

    @Test
    @DisplayName("El coste actual sigue al PMP: una compra nueva lo mueve, el escalado no lo congela")
    void elCosteActualSigueAlPmp() throws Exception {
        RecipeFixture recipe = recipeWithFlourAndSalt();

        // Otra compra de harina al doble de precio: PMP de 0,0100 a 0,0150 €/g.
        purchase(recipe.flourId(), "1", "kg", "20.00");

        String body = version(recipe);
        assertThat(decimal(body, "$.currentCostPerRation")).isEqualByComparingTo("4.1280");
        assertThat(decimal(body, "$.currentTotalCost")).isEqualByComparingTo("16.5120");

        String scaled = mvc.perform(
                        get(scalePath(recipe) + "?rations=2").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(decimal(scaled, "$.totalCost")).isEqualByComparingTo("8.2560");
    }

    @Test
    @DisplayName("El escalado valida las raciones y la existencia de la versión")
    void elEscaladoValidaSusEntradas() throws Exception {
        RecipeFixture recipe = recipeWithFlourAndSalt();

        mvc.perform(get(scalePath(recipe) + "?rations=0").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get(scalePath(recipe) + "?rations=10001").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/v1/recipes/" + recipe.recipeId() + "/versions/999999/scale?rations=2")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Cocina y almacén pueden escalar: es una consulta, no una escritura")
    void elEscaladoEsSoloLectura() throws Exception {
        RecipeFixture recipe = recipeWithFlourAndSalt();

        mvc.perform(get(scalePath(recipe) + "?rations=2").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk());
        mvc.perform(get(scalePath(recipe) + "?rations=2").header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk());
        mvc.perform(get(scalePath(recipe) + "?rations=2")).andExpect(status().isUnauthorized());
    }

    private record RecipeFixture(long recipeId, long versionId, long flourId, long saltId) {}

    private RecipeFixture recipeWithFlourAndSalt() throws Exception {
        long flour = createProduct("Harina coste " + next());
        long salt = createProduct("Sal coste " + next());
        purchase(flour, "1", "kg", "10.00");
        purchase(salt, "1", "kg", "1.00");

        String recipe = mvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Receta coste %d","baseRations":4}
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

    private String version(RecipeFixture recipe) throws Exception {
        return mvc.perform(get("/api/v1/recipes/" + recipe.recipeId() + "/versions/" + recipe.versionId())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String scalePath(RecipeFixture recipe) {
        return "/api/v1/recipes/" + recipe.recipeId() + "/versions/" + recipe.versionId() + "/scale";
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

    private static long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private static int intValue(String body, String path) {
        return ((Number) JsonPath.read(body, path)).intValue();
    }

    private static boolean boolValue(String body, String path) {
        return (boolean) JsonPath.read(body, path);
    }

    private static BigDecimal decimal(String body, String path) {
        Object value = JsonPath.read(body, path);
        return value == null ? null : new BigDecimal(value.toString());
    }
}
