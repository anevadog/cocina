package com.killa.cocina.modules.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.killa.cocina.config.EstablishmentProperties;
import com.killa.cocina.modules.inventory.domain.AdjustmentRegistration;
import com.killa.cocina.modules.inventory.domain.BaseUnit;
import com.killa.cocina.modules.inventory.domain.CategoryService;
import com.killa.cocina.modules.inventory.domain.MovementType;
import com.killa.cocina.modules.inventory.domain.ProductRegistration;
import com.killa.cocina.modules.inventory.domain.ProductService;
import com.killa.cocina.modules.inventory.domain.ProductView;
import com.killa.cocina.modules.inventory.domain.PurchaseRegistration;
import com.killa.cocina.modules.inventory.domain.PurchaseResult;
import com.killa.cocina.modules.inventory.domain.StockChangeResult;
import com.killa.cocina.modules.inventory.domain.StockService;
import com.killa.cocina.modules.inventory.domain.UnitConversion;
import com.killa.cocina.modules.suppliers.domain.SupplierRegistration;
import com.killa.cocina.modules.suppliers.domain.SupplierService;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.support.AbstractIntegrationTest;
import java.math.BigDecimal;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Concurrencia de movimientos de stock contra PostgreSQL real (NFR-INT-01, ADR-022).
 *
 * <p>Cada hilo abre su propia transacción al llamar al servicio, como en producción: el test no es
 * transaccional a propósito. Las aserciones son invariantes que se cumplen con cualquier
 * intercalado, no una secuencia concreta de ejecución, para que la prueba no dependa del planificador
 * ni del número de núcleos.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    private static final int TIMEOUT_SECONDS = 30;

    private static final String REASON = "Prueba de concurrencia";

    @Autowired
    private StockService stock;

    @Autowired
    private ProductService products;

    @Autowired
    private CategoryService categories;

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private UserService users;

    @Autowired
    private EstablishmentProperties establishment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long actorId;

    private long categoryId;

    private long supplierId;

    @BeforeAll
    void prepararDatos() {
        actorId = users.create(
                        establishment.id(),
                        null,
                        new UserRegistration("stock.concurrency", "Concurrencia", PASSWORD, Role.STOREKEEPER))
                .id();
        categoryId =
                categories.create(establishment.id(), null, "Concurrencia IT").id();
        supplierId = suppliers
                .create(establishment.id(), null, new SupplierRegistration("Proveedor concurrencia IT", null))
                .id();
    }

    @Test
    @DisplayName("Ocho movimientos simultáneos no pierden actualizaciones y dejan un kardex coherente")
    void losMovimientosSimultaneosNoPierdenActualizaciones() throws Exception {
        long productId = createProduct("Harina concurrencia IT");
        purchase(productId, "10", "10.00");

        List<Callable<StockChangeResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> adjust(productId, MovementType.ADJUSTMENT_IN, "0.25"));
        }
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> adjust(productId, MovementType.ADJUSTMENT_OUT, "0.1"));
        }

        assertThat(runConcurrently(tasks)).containsOnlyNulls();

        BigDecimal expected = new BigDecimal("10600.000");
        assertThat(currentStock(productId)).isEqualByComparingTo(expected);

        // El kardex ordenado por id es exactamente la secuencia de saldos aplicada: si dos hilos
        // hubieran leído el mismo saldo, la cadena se rompería o el último saldo no cuadraría.
        List<MovementRow> rows = kardex(productId);
        assertThat(rows).hasSize(9);
        BigDecimal running = BigDecimal.ZERO;
        for (MovementRow row : rows) {
            running = row.type().isInbound() ? running.add(row.quantity()) : running.subtract(row.quantity());
            assertThat(row.balanceAfter()).isEqualByComparingTo(running);
        }
        assertThat(rows.get(rows.size() - 1).balanceAfter()).isEqualByComparingTo(expected);

        // Ninguna salida consumió dos veces el mismo lote: el desglose físico cuadra con el stock.
        assertThat(lotRemaining(productId)).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("Dos salidas simultáneas no cruzan el cero: una gana y la otra recibe 409")
    void dosSalidasSimultaneasNoCruzanElCero() throws Exception {
        long productId = createProduct("Sal concurrencia IT");
        purchase(productId, "1", "1.00");

        List<Callable<StockChangeResult>> tasks = List.of(
                () -> adjust(productId, MovementType.ADJUSTMENT_OUT, "0.6"),
                () -> adjust(productId, MovementType.ADJUSTMENT_OUT, "0.6"));

        List<Throwable> errors = runConcurrently(tasks);
        assertThat(errors).filteredOn(Objects::isNull).hasSize(1);
        assertThat(errors)
                .filteredOn(Objects::nonNull)
                .hasSize(1)
                .allSatisfy(error -> assertThat(error)
                        .isInstanceOfSatisfying(
                                ApiException.class,
                                failure -> assertThat(failure.code()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK)));

        assertThat(currentStock(productId)).isEqualByComparingTo("400.000");
        assertThat(kardex(productId)).hasSize(2);
        assertThat(lotRemaining(productId)).isEqualByComparingTo("400.000");
    }

    @Test
    @DisplayName("Diez salidas simultáneas de 200 g sobre 1 000 g: exactamente cinco tienen éxito")
    void lasSalidasSimultaneasRespetanElSaldo() throws Exception {
        long productId = createProduct("Pimienta concurrencia IT");
        purchase(productId, "1", "1.00");

        List<Callable<StockChangeResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> adjust(productId, MovementType.ADJUSTMENT_OUT, "0.2"));
        }

        List<Throwable> errors = runConcurrently(tasks);
        assertThat(errors).filteredOn(Objects::isNull).hasSize(5);
        assertThat(errors).filteredOn(Objects::nonNull).hasSize(5);

        assertThat(currentStock(productId)).isEqualByComparingTo("0.000");
        assertThat(kardex(productId)).hasSize(6);
        assertThat(lotRemaining(productId)).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("Dos compras simultáneas dejan el PMP en la media ponderada y el saldo correcto")
    void lasComprasSimultaneasCalculanBienElPmp() throws Exception {
        long productId = createProduct("Arroz concurrencia IT");

        List<Callable<PurchaseResult>> tasks =
                List.of(() -> purchaseResult(productId, "1", "10.00"), () -> purchaseResult(productId, "1", "20.00"));

        assertThat(runConcurrently(tasks)).containsOnlyNulls();

        ProductView product = products.find(establishment.id(), productId);
        assertThat(product.currentStock()).isEqualByComparingTo("2000.000");
        assertThat(product.currentAvgCost()).isEqualByComparingTo("0.0150");
        assertThat(kardex(productId)).hasSize(2);
        assertThat(lotRemaining(productId)).isEqualByComparingTo("2000.000");
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

    private long purchase(long productId, String quantityKg, String unitPrice) {
        return purchaseResult(productId, quantityKg, unitPrice).movementId();
    }

    private PurchaseResult purchaseResult(long productId, String quantityKg, String unitPrice) {
        return stock.purchase(
                establishment.id(),
                actorId,
                productId,
                new PurchaseRegistration(
                        supplierId, new BigDecimal(quantityKg), "kg", new BigDecimal(unitPrice), null, null));
    }

    private StockChangeResult adjust(long productId, MovementType type, String quantityKg) {
        return stock.adjust(
                establishment.id(),
                actorId,
                productId,
                new AdjustmentRegistration(type, new BigDecimal(quantityKg), "kg", REASON));
    }

    private BigDecimal currentStock(long productId) {
        return products.find(establishment.id(), productId).currentStock();
    }

    private List<MovementRow> kardex(long productId) {
        return jdbcTemplate.query(
                """
                select type, quantity_base, balance_after
                from stock_movements
                where product_id = ?
                order by id
                """,
                (rs, rowNum) -> new MovementRow(
                        MovementType.valueOf(rs.getString("type")),
                        rs.getBigDecimal("quantity_base"),
                        rs.getBigDecimal("balance_after")),
                productId);
    }

    private BigDecimal lotRemaining(long productId) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(remaining_qty), 0)
                from stock_lots
                where product_id = ?
                """, BigDecimal.class, productId);
    }

    /**
     * Ejecuta todas las tareas a la vez tras una barrera común y devuelve el fallo de cada una
     * ({@code null} si terminó bien). Un tiempo límite evita que un bloqueo mal resuelto cuelgue la
     * suite en lugar de fallar.
     */
    private static List<Throwable> runConcurrently(List<? extends Callable<?>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size(), runnable -> {
            Thread thread = new Thread(runnable, "stock-concurrency");
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

    private record MovementRow(MovementType type, BigDecimal quantity, BigDecimal balanceAfter) {}
}
