package com.killa.cocina.modules.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Mermas de punta a punta (CAL-03, CAL-04).
 *
 * <p>Comprueba contra PostgreSQL real que la merma consume lotes FIFO o uno concreto, que puede
 * retirar lotes caducados, que se valora al PMP vigente y que el descuento de stock, el kardex y la
 * auditoría son atómicos.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WasteRecordsIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

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

    private long cookId;

    private long categoryId;

    private long supplierId;

    private String cookToken;

    private String storekeeperToken;

    @BeforeAll
    void prepararDatos() throws Exception {
        cookId = users.create(
                        establishment.id(),
                        null,
                        new UserRegistration("waste.cook", "Cocinera de mermas", PASSWORD, Role.COOK))
                .id();
        users.create(
                establishment.id(),
                null,
                new UserRegistration("waste.store", "Almacenera de mermas", PASSWORD, Role.STOREKEEPER));
        cookToken = loginToken("waste.cook");
        storekeeperToken = loginToken("waste.store");
        categoryId = categories.create(establishment.id(), null, "Mermas IT").id();
        supplierId = suppliers
                .create(
                        establishment.id(),
                        null,
                        new SupplierRegistration("Proveedor mermas IT", "mermas@example.test"))
                .id();
    }

    @Test
    @DisplayName("La merma consume lotes FIFO y se valora al PMP vigente")
    void consumeLotesFifoYValoraAlPmp() throws Exception {
        long productId = createProduct("Merma FIFO", false, BigDecimal.ZERO);
        long firstLot = id(purchase(productId, "1", "kg", "10.00", null, null), "$.lotId");
        long secondLot = id(purchase(productId, "2", "kg", "20.00", null, null), "$.lotId");

        String body = register(productId, null, "1500", "Retirada de prueba FIFO", cookToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotId").value(nullValue()))
                .andExpect(jsonPath("$.actorId").value(cookId))
                .andExpect(jsonPath("$.reason").value("Retirada de prueba FIFO"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long wasteId = id(body, "$.id");
        assertThat(decimal(body, "$.quantityBase")).isEqualByComparingTo("1500");
        assertThat(decimal(body, "$.unitCost")).isEqualByComparingTo("0.0167");
        assertThat(decimal(body, "$.cost")).isEqualByComparingTo("25.0500");

        mvc.perform(get("/api/v1/products/" + productId).header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStock").value(1500.000))
                .andExpect(jsonPath("$.currentAvgCost").value(0.0167));

        Map<Long, BigDecimal> remaining = lotRemaining(productId);
        assertThat(remaining.get(firstLot)).isEqualByComparingTo("0");
        assertThat(remaining.get(secondLot)).isEqualByComparingTo("1500");

        List<Map<String, Object>> movements = jdbcTemplate.queryForList("""
                select lot_id, quantity_base, unit_cost, reference_type, reference_id
                from stock_movements
                where type = 'WASTE_OUT' and reference_type = 'waste_record' and reference_id = ?
                order by id
                """, wasteId);
        assertThat(movements).hasSize(2);
        assertThat(((Number) movements.get(0).get("lot_id")).longValue()).isEqualTo(firstLot);
        assertThat((BigDecimal) movements.get(0).get("quantity_base")).isEqualByComparingTo("1000");
        assertThat((BigDecimal) movements.get(0).get("unit_cost")).isEqualByComparingTo("0.0100");
        assertThat(((Number) movements.get(1).get("lot_id")).longValue()).isEqualTo(secondLot);
        assertThat((BigDecimal) movements.get(1).get("quantity_base")).isEqualByComparingTo("500");
        assertThat((BigDecimal) movements.get(1).get("unit_cost")).isEqualByComparingTo("0.0200");

        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select actor_id, before_json::text as before_json, after_json::text as after_json
                from audit_logs
                where action = 'WASTE_RECORDED' and entity_type = 'waste_record' and entity_id = ?
                """, wasteId);
        assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(cookId);
        assertThat(audit.get("before_json").toString())
                .contains("\"productId\": " + productId)
                .contains("\"currentStock\": \"3000.000\"");
        assertThat(audit.get("after_json").toString())
                .contains("\"wasteRecordId\": " + wasteId)
                .contains("\"cost\": \"25.0500\"")
                .contains("\"currentStock\": \"1500.000\"");
    }

    @Test
    @DisplayName("Una merma puede retirar un lote caducado")
    void retiraUnLoteCaducado() throws Exception {
        long productId = createProduct("Merma caducada", false, BigDecimal.ZERO);
        Instant expiredAt = Instant.now().minus(Duration.ofDays(1));
        long expiredLot =
                id(purchase(productId, "1", "kg", "5.00", expiredAt.minus(Duration.ofDays(9)), expiredAt), "$.lotId");
        assertThat(jdbcTemplate.queryForObject("""
                select expires_at < now() from stock_lots where id = ?
                """, Boolean.class, expiredLot)).isTrue();

        String body = register(productId, null, "1000", "Retirada de lote caducado", cookToken)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long wasteId = id(body, "$.id");
        assertThat(jdbcTemplate.queryForObject(
                        "select remaining_qty from stock_lots where id = ?", BigDecimal.class, expiredLot))
                .isEqualByComparingTo("0");
        assertThat(jdbcTemplate.queryForObject(
                        "select quantity_base from stock_movements where reference_type = 'waste_record' and reference_id = ?",
                        BigDecimal.class,
                        wasteId))
                .isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Una merma puede retirar un lote concreto sin tocar los demás")
    void retiraUnLoteConcreto() throws Exception {
        long productId = createProduct("Merma lote concreto", false, BigDecimal.ZERO);
        long firstLot = id(purchase(productId, "1", "kg", "10.00", null, null), "$.lotId");
        long secondLot = id(purchase(productId, "2", "kg", "20.00", null, null), "$.lotId");

        String body = register(productId, secondLot, "500", "Retirada del lote nuevo", cookToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotId").value(secondLot))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(decimal(body, "$.cost")).isEqualByComparingTo("8.3500");
        Map<Long, BigDecimal> remaining = lotRemaining(productId);
        assertThat(remaining.get(firstLot)).isEqualByComparingTo("1000");
        assertThat(remaining.get(secondLot)).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("Sin stock suficiente devuelve 409 y no deja cabecera, movimiento ni auditoría")
    void sinStockNoDejaRastro() throws Exception {
        long productId = createProduct("Merma sin stock", false, BigDecimal.ZERO);
        purchase(productId, "1", "kg", "10.00", null, null);

        register(productId, null, "1500", "Retirada sin existencias", cookToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mvc.perform(get("/api/v1/products/" + productId).header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStock").value(1000.000));
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from waste_records where product_id = ?", Integer.class, productId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from stock_movements where product_id = ? and type = 'WASTE_OUT'
                """, Integer.class, productId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_logs
                where action = 'WASTE_RECORDED' and (after_json ->> 'productId')::bigint = ?
                """, Integer.class, productId)).isZero();
    }

    @Test
    @DisplayName("Con saldo negativo permitido la merma deja el descuento sin lote visible en el kardex")
    void permiteSaldoNegativoSiElProductoLoAdmite() throws Exception {
        long productId = createProduct("Merma saldo negativo", true, BigDecimal.ZERO);
        long lotId = id(purchase(productId, "1", "kg", "10.00", null, null), "$.lotId");

        String body = register(productId, null, "1500", "Retirada con saldo negativo", cookToken)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long wasteId = id(body, "$.id");

        mvc.perform(get("/api/v1/products/" + productId).header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStock").value(-500.000));
        assertThat(jdbcTemplate.queryForObject(
                        "select remaining_qty from stock_lots where id = ?", BigDecimal.class, lotId))
                .isEqualByComparingTo("0");

        List<Map<String, Object>> movements = jdbcTemplate.queryForList("""
                select lot_id, quantity_base, balance_after
                from stock_movements
                where reference_type = 'waste_record' and reference_id = ?
                order by id
                """, wasteId);
        assertThat(movements).hasSize(2);
        assertThat(((Number) movements.get(0).get("lot_id")).longValue()).isEqualTo(lotId);
        assertThat(movements.get(1).get("lot_id")).isNull();
        assertThat((BigDecimal) movements.get(1).get("quantity_base")).isEqualByComparingTo("500");
        assertThat((BigDecimal) movements.get(1).get("balance_after")).isEqualByComparingTo("-500");
    }

    @Test
    @DisplayName("La petición, el producto y el lote se validan")
    void validaLaPeticion() throws Exception {
        long productId = createProduct("Merma validación", false, BigDecimal.ZERO);
        purchase(productId, "1", "kg", "10.00", null, null);
        long foreignProduct = createProduct("Merma producto ajeno", false, BigDecimal.ZERO);
        long foreignLot = id(purchase(foreignProduct, "1", "kg", "10.00", null, null), "$.lotId");

        register(productId, null, "0", "Motivo válido", cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("quantityBase"));

        register(productId, null, "100", "mal", cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("reason"));

        register(999_999L, null, "100", "Producto inexistente", cookToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        register(productId, 999_999L, "100", "Lote inexistente", cookToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        register(productId, foreignLot, "100", "Lote de otro producto", cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("lotId"));
    }

    @Test
    @DisplayName("La merma se consulta por id y se pagina; un id inexistente devuelve 404")
    void consultaYPagina() throws Exception {
        long productId = createProduct("Merma paginación", false, BigDecimal.ZERO);
        purchase(productId, "2", "kg", "10.00", null, null);
        long first = id(
                register(productId, null, "100", "Primera merma paginada", cookToken)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        long second = id(
                register(productId, null, "200", "Segunda merma paginada", cookToken)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        mvc.perform(get("/api/v1/waste-records/" + first).header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.reason").value("Primera merma paginada"));

        String page = mvc.perform(
                        get("/api/v1/waste-records?page=0&size=1").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Map<String, Object>> rows = JsonPath.read(page, "$.content");
        assertThat(((Number) rows.get(0).get("id")).longValue()).isEqualTo(second);

        mvc.perform(get("/api/v1/waste-records/999999").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Solo el cocinero registra mermas; el almacenero las consulta")
    void respetaLaMatrizDePermisos() throws Exception {
        long productId = createProduct("Merma permisos", false, BigDecimal.ZERO);
        purchase(productId, "1", "kg", "10.00", null, null);

        register(productId, null, "100", "Merma sin permiso", storekeeperToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/v1/waste-records").header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk());

        long wasteId = id(
                register(productId, null, "100", "Merma con permiso", cookToken)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        mvc.perform(get("/api/v1/waste-records/" + wasteId).header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk());
    }

    private ResultActions register(long productId, Long lotId, String quantity, String reason, String token)
            throws Exception {
        String body = """
                {"productId":%d,"quantityBase":%s,"reason":"%s"%s}
                """.formatted(productId, quantity, reason, lotId == null ? "" : ",\"lotId\":" + lotId);
        return mvc.perform(post("/api/v1/waste-records")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long createProduct(String name, boolean allowNegative, BigDecimal minStock) {
        String uniqueName = name + " " + SEQUENCE.incrementAndGet();
        return products.create(
                        establishment.id(),
                        null,
                        new ProductRegistration(
                                uniqueName,
                                categoryId,
                                BaseUnit.G,
                                minStock,
                                allowNegative,
                                List.of(new UnitConversion("kg", new BigDecimal("1000")))))
                .id();
    }

    private String purchase(
            long productId, String quantity, String unit, String unitPrice, Instant receivedAt, Instant expiresAt)
            throws Exception {
        String body = """
                {"supplierId":%d,"quantity":%s,"unit":"%s","unitPrice":%s%s%s}
                """.formatted(
                        supplierId,
                        quantity,
                        unit,
                        unitPrice,
                        receivedAt == null ? "" : ",\"receivedAt\":\"" + receivedAt + "\"",
                        expiresAt == null ? "" : ",\"expiresAt\":\"" + expiresAt + "\"");
        return mvc.perform(post("/api/v1/products/" + productId + "/purchases")
                        .header("Authorization", "Bearer " + storekeeperToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Map<Long, BigDecimal> lotRemaining(long productId) throws Exception {
        String body = mvc.perform(get("/api/v1/products/" + productId + "/lots")
                        .header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Map<String, Object>> lots = JsonPath.read(body, "$.content");
        return lots.stream()
                .collect(java.util.stream.Collectors.toMap(
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
                .andReturn()
                .getResponse()
                .getContentAsString();
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
