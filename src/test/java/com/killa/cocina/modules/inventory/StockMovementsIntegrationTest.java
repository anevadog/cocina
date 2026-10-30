package com.killa.cocina.modules.inventory;

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
import com.killa.cocina.modules.suppliers.domain.SupplierModification;
import com.killa.cocina.modules.suppliers.domain.SupplierRegistration;
import com.killa.cocina.modules.suppliers.domain.SupplierService;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import com.killa.cocina.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * Compras, ajustes, kardex, lotes y alertas de punta a punta (INV-04..09, ECO-06).
 *
 * <p>Verifica PMP, FIFO, política de saldo, inmutabilidad del kardex, alertas y RBAC contra
 * PostgreSQL real.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockMovementsIntegrationTest extends AbstractIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long storekeeperId;

    private long categoryId;

    private long supplierId;

    private String storeToken;

    private String cookToken;

    @BeforeAll
    void prepararDatos() throws Exception {
        storekeeperId = users.create(establishment.id(), null,
                new UserRegistration("stock.store", "Almacenera", PASSWORD, Role.STOREKEEPER)).id();
        users.create(establishment.id(), null,
                new UserRegistration("stock.cook", "Cocinera", PASSWORD, Role.COOK));
        storeToken = loginToken("stock.store");
        cookToken = loginToken("stock.cook");
        categoryId = categories.create(establishment.id(), null, "Stock IT").id();
        supplierId = suppliers.create(establishment.id(), null,
                new SupplierRegistration("Proveedor stock IT", "compras@example.test")).id();
    }

    @Test
    @DisplayName("Dos compras recalculan el PMP y dejan el kardex con su saldo resultante")
    void lasComprasRecalcularElPmp() throws Exception {
        long productId = createProduct("Harina stock IT", false, BigDecimal.ZERO);

        String first = purchase(productId, supplierId, "1", "kg", "12.00", null, null);
        assertThat(decimal(first, "$.quantityBase")).isEqualByComparingTo("1000");
        assertThat(decimal(first, "$.unitCost")).isEqualByComparingTo("0.0120");
        assertThat(decimal(first, "$.currentStock")).isEqualByComparingTo("1000");
        assertThat(decimal(first, "$.currentAvgCost")).isEqualByComparingTo("0.0120");
        long firstMovement = id(first, "$.movementId");

        String second = purchase(productId, supplierId, "2", "kg", "18.00", null, null);
        assertThat(decimal(second, "$.currentStock")).isEqualByComparingTo("3000");
        assertThat(decimal(second, "$.currentAvgCost")).isEqualByComparingTo("0.0160");

        mvc.perform(get("/api/v1/products/" + productId + "/movements")
                        .header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[0].type").value("PURCHASE_IN"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(3000.000))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(1000.000));

        mvc.perform(get("/api/v1/products/" + productId + "/lots")
                        .header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_logs
                where action = 'STOCK_PURCHASE_RECORDED' and entity_type = 'stock_movement'
                  and entity_id = ? and actor_id = ?
                """, Integer.class, firstMovement, storekeeperId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_logs
                where action = 'STOCK_COST_CHANGED' and entity_type = 'product' and entity_id = ?
                """, Integer.class, productId)).isEqualTo(2);
    }

    @Test
    @DisplayName("Un ajuste de salida consume lotes FIFO por caducidad")
    void elAjusteDeSalidaConsumeLotesFifo() throws Exception {
        long productId = createProduct("Arroz stock IT", false, BigDecimal.ZERO);
        long firstLot = id(purchase(productId, supplierId, "1", "kg", "10.00", null, null),
                "$.lotId");
        long secondLot = id(purchase(productId, supplierId, "2", "kg", "20.00", null, null),
                "$.lotId");

        String adjustment = mvc.perform(post("/api/v1/products/" + productId + "/adjustments")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ADJUSTMENT_OUT","quantity":0.5,"unit":"kg",
                                 "reason":"Rotura de saco"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ADJUSTMENT_OUT"))
                .andExpect(jsonPath("$.reason").value("Rotura de saco"))
                .andReturn().getResponse().getContentAsString();

        assertThat(decimal(adjustment, "$.quantityBase")).isEqualByComparingTo("500");
        assertThat(decimal(adjustment, "$.currentStock")).isEqualByComparingTo("2500");
        assertThat(decimal(adjustment, "$.currentAvgCost")).isEqualByComparingTo("0.0167");

        Map<Long, BigDecimal> remaining = lotRemaining(productId);
        assertThat(remaining.get(firstLot)).isEqualByComparingTo("500");
        assertThat(remaining.get(secondLot)).isEqualByComparingTo("2000");

        mvc.perform(get("/api/v1/products/" + productId + "/movements")
                        .header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.content[0].type").value("ADJUSTMENT_OUT"));
    }

    @Test
    @DisplayName("Sin stock suficiente devuelve 409 y no deja rastro; el motivo es obligatorio")
    void elAjusteNoDejaSaldoNegativo() throws Exception {
        long productId = createProduct("Sal stock IT", false, BigDecimal.ZERO);
        purchase(productId, supplierId, "1", "kg", "1.00", null, null);

        mvc.perform(post("/api/v1/products/" + productId + "/adjustments")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ADJUSTMENT_OUT","quantity":2,"unit":"kg",
                                 "reason":"Merma de sal"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mvc.perform(get("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStock").value(1000.000));

        mvc.perform(get("/api/v1/products/" + productId + "/movements")
                        .header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mvc.perform(post("/api/v1/products/" + productId + "/adjustments")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADJUSTMENT_OUT\",\"quantity\":1,\"unit\":\"kg\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(post("/api/v1/products/" + productId + "/adjustments")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ADJUSTMENT_OUT","quantity":1,"unit":"kg","reason":"abc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(post("/api/v1/products/" + productId + "/adjustments")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PURCHASE_IN","quantity":1,"unit":"kg","reason":"Motivo válido"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(post("/api/v1/products/" + productId + "/adjustments")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ADJUSTMENT_OUT","quantity":0.5,"unit":"l",
                                 "reason":"Unidad equivocada"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNIT_INCOMPATIBLE"));
    }

    @Test
    @DisplayName("Un producto que admite negativo puede quedar bajo cero y la compra vuelve a fijar el PMP")
    void elProductoConNegativoPermitidoSeRegulariza() throws Exception {
        long productId = createProduct("Aceite stock IT", true, BigDecimal.ZERO);
        purchase(productId, supplierId, "1", "kg", "5.00", null, null);

        mvc.perform(post("/api/v1/products/" + productId + "/adjustments")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ADJUSTMENT_OUT","quantity":1.5,"unit":"kg",
                                 "reason":"Corrección de inventario"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentStock").value(-500.000))
                .andExpect(jsonPath("$.currentAvgCost").value(0.0050));

        String purchase = purchase(productId, supplierId, "2", "kg", "4.00", null, null);
        assertThat(decimal(purchase, "$.currentStock")).isEqualByComparingTo("1500");
        assertThat(decimal(purchase, "$.currentAvgCost")).isEqualByComparingTo("0.0040");
    }

    @Test
    @DisplayName("Las alertas muestran el mínimo y la caducidad próxima con valor en riesgo")
    void lasAlertasMuestranMinimoYCaducidad() throws Exception {
        long lowStockProduct = createProduct("Producto mínimo IT", false, new BigDecimal("1000"));
        long expiringProduct = createProduct("Yogur stock IT", false, BigDecimal.ZERO);
        Instant expiresAt = Instant.now().plus(Duration.ofDays(2)).plus(Duration.ofHours(12));
        long lotId = id(purchase(expiringProduct, supplierId, "1", "kg", "2.00", null, expiresAt),
                "$.lotId");

        String body = mvc.perform(get("/api/v1/inventory/alerts")
                        .header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockTotal").isNumber())
                .andExpect(jsonPath("$.expiringTotal").isNumber())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> lowStock = JsonPath.read(body, "$.lowStock");
        assertThat(lowStock)
                .anySatisfy(alert -> assertThat(((Number) alert.get("productId")).longValue())
                        .isEqualTo(lowStockProduct));

        List<Map<String, Object>> expiring = JsonPath.read(body, "$.expiring");
        assertThat(expiring)
                .anySatisfy(alert -> {
                    assertThat(((Number) alert.get("lotId")).longValue()).isEqualTo(lotId);
                    assertThat(((Number) alert.get("daysRemaining")).longValue()).isEqualTo(2);
                    assertThat(new BigDecimal(alert.get("value").toString()))
                            .isEqualByComparingTo("2.0000");
                });
    }

    @Test
    @DisplayName("El kardex no se puede editar ni borrar: ni por API ni por SQL")
    void elKardexEsInmutable() throws Exception {
        long productId = createProduct("Kardex stock IT", false, BigDecimal.ZERO);
        long movementId = id(purchase(productId, supplierId, "1", "kg", "3.00", null, null),
                "$.movementId");

        mvc.perform(put("/api/v1/products/" + productId + "/movements")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(delete("/api/v1/products/" + productId + "/movements")
                        .header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isMethodNotAllowed());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update stock_movements set reason = 'Cambio indebido' where id = ?", movementId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from stock_movements where id = ?", movementId))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from stock_movements where id = ?", Integer.class, movementId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Un COOK consulta kardex y alertas, pero no registra movimientos")
    void elCocineroNoRegistraMovimientos() throws Exception {
        long productId = createProduct("Permisos stock IT", false, BigDecimal.ZERO);

        mvc.perform(get("/api/v1/products/" + productId + "/movements")
                        .header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/inventory/alerts")
                        .header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(supplierId, "1", "kg", "10.00", null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(post("/api/v1/products/" + productId + "/adjustments")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ADJUSTMENT_IN","quantity":1,"unit":"kg",
                                 "reason":"Intento de cocina"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("La compra valida proveedor activo, producto activo y fechas coherentes")
    void laCompraValidaSusDatos() throws Exception {
        long productId = createProduct("Validación stock IT", false, BigDecimal.ZERO);
        long inactiveSupplier = suppliers.create(establishment.id(), null,
                new SupplierRegistration("Proveedor inactivo IT", null)).id();
        suppliers.update(establishment.id(), null, inactiveSupplier,
                new SupplierModification(null, null, false));

        mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(999_999, "1", "kg", "10.00", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(inactiveSupplier, "1", "kg", "10.00", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(supplierId, "1", "ml", "10.00", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNIT_INCOMPATIBLE"));

        mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(supplierId, "1", "kg", "10.00",
                                Instant.now().plus(Duration.ofHours(1)), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Instant received = Instant.now().minus(Duration.ofHours(1));
        mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(supplierId, "1", "kg", "10.00", received,
                                received.minus(Duration.ofHours(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(patch("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(supplierId, "1", "kg", "10.00", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private long createProduct(String name, boolean allowNegative, BigDecimal minStock) {
        return products.create(establishment.id(), null,
                new ProductRegistration(name, categoryId, BaseUnit.G, minStock, allowNegative,
                        List.of(new UnitConversion("kg", new BigDecimal("1000"))))).id();
    }

    private String purchase(long productId, long supplierId, String quantity, String unit,
            String unitPrice, Instant receivedAt, Instant expiresAt) throws Exception {
        return mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + storeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(supplierId, quantity, unit, unitPrice, receivedAt,
                                expiresAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String purchaseBody(long supplierId, String quantity, String unit,
            String unitPrice, Instant receivedAt, Instant expiresAt) {
        return """
                {"supplierId":%d,"quantity":%s,"unit":"%s","unitPrice":%s%s%s}
                """.formatted(supplierId, quantity, unit, unitPrice,
                receivedAt == null ? "" : ",\"receivedAt\":\"" + receivedAt + "\"",
                expiresAt == null ? "" : ",\"expiresAt\":\"" + expiresAt + "\"");
    }

    private Map<Long, BigDecimal> lotRemaining(long productId) throws Exception {
        String body = mvc.perform(get("/api/v1/products/" + productId + "/lots")
                        .header("Authorization", "Bearer " + storeToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> lots = JsonPath.read(body, "$.content");
        return lots.stream().collect(java.util.stream.Collectors.toMap(
                lot -> ((Number) lot.get("id")).longValue(),
                lot -> new BigDecimal(lot.get("remainingQty").toString())));
    }

    private String loginToken(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private static BigDecimal decimal(String body, String path) {
        Object value = JsonPath.read(body, path);
        return new BigDecimal(value.toString());
    }
}
