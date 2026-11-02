package com.killa.cocina.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.killa.cocina.config.EstablishmentProperties;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * Medición de referencia de rendimiento (NFR-REN-01, NFR-REN-02).
 *
 * <p>No se ejecuta en {@code ./mvnw verify}: es una prueba opt-in y larga, pensada para revisar el
 * rendimiento a mano. Para ejecutarla:</p>
 *
 * <pre>{@code
 * ./mvnw -Dtest=PerformanceBaselineCheck -Dcocina.performance=true test
 * }</pre>
 *
 * <p>Levanta PostgreSQL 18 con Testcontainers, siembra 20 productos, 100 lotes, 100 000 movimientos,
 * 100 recetas publicadas con 5 ingredientes cada una y un plan con 500 raciones. Después lanza 10
 * clientes concurrentes contra la API real (Tomcat + seguridad + JPA) y comprueba el percentil 95.
 * Los umbrales son los del requisito, sin margen adicional.</p>
 */
@EnabledIfSystemProperty(named = "cocina.performance", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerformanceBaselineCheck {

    private static final int CLIENTS = 10;

    private static final int CYCLES_PER_CLIENT = 40;

    private static final int WARMUP_CYCLES = 5;

    private static final double MAX_P95_MILLIS = 300.0;

    private static final double MAX_REQUIREMENTS_MILLIS = 2000.0;

    private static final String PERFORMANCE_PASSWORD = "Performance2026!";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    static {
        if (Boolean.getBoolean("cocina.performance")) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserService users;

    @Autowired
    private EstablishmentProperties establishment;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private long establishmentId;

    private long productId;

    private long planId;

    private String bearerToken;

    @BeforeAll
    void seedDataset() throws Exception {
        establishmentId = establishment.id();
        long adminId = users.create(
                        establishmentId,
                        null,
                        new UserRegistration(
                                "perf.admin", "Administradora de rendimiento", PERFORMANCE_PASSWORD, Role.ADMIN))
                .id();
        bearerToken = login("perf.admin", PERFORMANCE_PASSWORD);

        jdbc.update(
                "insert into product_categories (establishment_id, name) values (?, 'Rendimiento')", establishmentId);
        Long categoryId = jdbc.queryForObject(
                "select id from product_categories where establishment_id = ? and name = 'Rendimiento'",
                Long.class,
                establishmentId);

        // 20 productos con stock de sobra: el listado y las alertas no se disparan por mínimo.
        jdbc.update("""
                insert into products (establishment_id, category_id, name, base_unit, min_stock,
                                      current_stock, current_avg_cost, allow_negative, active)
                select ?, ?, 'Perf product ' || g, 'G', 1000, 1000000, 0.0010, false, true
                from generate_series(1, 20) g
                """, establishmentId, categoryId);
        productId = jdbc.queryForObject(
                "select id from products where establishment_id = ? and name = 'Perf product 1'",
                Long.class,
                establishmentId);

        // 100 lotes y 100 000 movimientos: el kardex del producto es el caso más exigente.
        jdbc.update("""
                insert into stock_lots (establishment_id, product_id, received_at, unit_cost,
                                        initial_qty, remaining_qty)
                select ?, ?, now() - interval '1 hour', 0.0010, 1000000, 1000000
                from generate_series(1, 100)
                """, establishmentId, productId);
        Long lotId = jdbc.queryForObject(
                "select id from stock_lots where product_id = ? order by id limit 1", Long.class, productId);
        jdbc.update("""
                insert into stock_movements (establishment_id, product_id, lot_id, type, quantity_base,
                                             unit_cost, balance_after, actor_id, occurred_at)
                select ?, ?, ?, 'PURCHASE_IN', 0.001, 0.0010, 0.001 * g, ?, now() - (g || ' seconds')::interval
                from generate_series(1, 100000) g
                """, establishmentId, productId, lotId, adminId);

        seedRecipes(adminId);
        seedPlan(adminId);
    }

    /** 100 recetas publicadas con 5 ingredientes cada una; los ítems se insertan en borrador. */
    private void seedRecipes(long adminId) {
        jdbc.update("""
                insert into recipes (establishment_id, name, category, active)
                select ?, 'Perf recipe ' || g, 'Rendimiento', true from generate_series(1, 100) g
                """, establishmentId);
        jdbc.update("""
                insert into recipe_versions (establishment_id, recipe_id, version_number, status,
                                             base_rations, yield_pct, created_by)
                select establishment_id, id, 1, 'DRAFT', 1, 100, ?
                from recipes where establishment_id = ? and name like 'Perf recipe %'
                """, adminId, establishmentId);
        jdbc.update("""
                insert into recipe_items (establishment_id, version_id, product_id, quantity_base,
                                          waste_pct, sort_order)
                select v.establishment_id, v.id, p.id, 100, 0,
                       row_number() over (partition by v.id order by p.id) - 1
                from recipe_versions v
                cross join (select id from products where name like 'Perf product %' order by id limit 5) p
                where v.recipe_id in (select id from recipes where name like 'Perf recipe %')
                """);
        jdbc.update("""
                update recipe_versions set status = 'PUBLISHED', total_cost = 10, cost_per_ration = 10,
                       published_at = now(), published_by = ?
                where status = 'DRAFT'
                  and recipe_id in (select id from recipes where name like 'Perf recipe %')
                """, adminId);
        jdbc.update("""
                update recipes r set current_version_id = v.id
                from recipe_versions v
                where v.recipe_id = r.id and v.version_number = 1
                  and r.name like 'Perf recipe %'
                """);
    }

    /** Un plan con 100 recetas a 5 raciones: 500 raciones en total (NFR-REN-02). */
    private void seedPlan(long adminId) {
        jdbc.update("""
                insert into production_plans (establishment_id, service_date, service_type, status, created_by)
                values (?, current_date + 1, 'LUNCH', 'PENDING', ?)
                """, establishmentId, adminId);
        planId = jdbc.queryForObject(
                "select id from production_plans where establishment_id = ? order by id desc limit 1",
                Long.class,
                establishmentId);
        jdbc.update("""
                insert into production_plan_items (establishment_id, plan_id, recipe_version_id, planned_rations)
                select ?, ?, v.id, 5
                from recipe_versions v
                where v.recipe_id in (select id from recipes where name like 'Perf recipe %')
                  and v.status = 'PUBLISHED'
                """, establishmentId, planId);
    }

    @Test
    void crudP95StaysBelow300MillisWithTenClientsAnd100kMovements() throws Exception {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("products", "/api/v1/products?size=20");
        paths.put("movements", "/api/v1/products/" + productId + "/movements?size=20");
        paths.put("alerts", "/api/v1/inventory/alerts");
        paths.put("recipe-costs", "/api/v1/reports/recipe-costs");

        // Calentamiento: JIT, pool de conexiones y cachés de Hibernate.
        for (int cycle = 0; cycle < WARMUP_CYCLES; cycle++) {
            for (String path : paths.values()) {
                get(path);
            }
        }

        Map<String, List<Long>> samples = new LinkedHashMap<>();
        paths.keySet().forEach(name -> samples.put(name, Collections.synchronizedList(new ArrayList<>())));
        ExecutorService pool = Executors.newFixedThreadPool(CLIENTS);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int client = 0; client < CLIENTS; client++) {
                jobs.add(() -> {
                    for (int cycle = 0; cycle < CYCLES_PER_CLIENT; cycle++) {
                        for (Map.Entry<String, String> entry : paths.entrySet()) {
                            long start = System.nanoTime();
                            HttpResponse<String> response = get(entry.getValue());
                            long elapsed = (System.nanoTime() - start) / 1_000_000;
                            assertEquals(200, response.statusCode(), () -> entry.getKey() + " -> " + response.body());
                            samples.get(entry.getKey()).add(elapsed);
                        }
                    }
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(jobs, 5, TimeUnit.MINUTES)) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        System.out.println(
                "NFR-REN-01 · " + CLIENTS + " clientes × " + CYCLES_PER_CLIENT + " ciclos · 100 000 movimientos");
        for (Map.Entry<String, List<Long>> entry : samples.entrySet()) {
            Stats stats = Stats.of(entry.getValue());
            System.out.printf(
                    "  %-14s n=%d  p50=%d ms  p95=%d ms  p99=%d ms  max=%d ms%n",
                    entry.getKey(), stats.count(), stats.p50(), stats.p95(), stats.p99(), stats.max());
            assertTrue(
                    stats.p95() <= MAX_P95_MILLIS,
                    () -> entry.getKey() + ": p95 " + stats.p95() + " ms supera " + MAX_P95_MILLIS + " ms");
        }
    }

    @Test
    void writesP95StaysBelow300MillisWithTenClients() throws Exception {
        String path = "/api/v1/products/" + productId + "/adjustments";
        String body = "{\"type\":\"ADJUSTMENT_IN\",\"quantity\":1,\"unit\":\"g\",\"reason\":\"Ajuste de rendimiento\"}";

        for (int i = 0; i < WARMUP_CYCLES; i++) {
            assertEquals(201, post(path, body).statusCode());
        }

        List<Long> samples = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(CLIENTS);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int client = 0; client < CLIENTS; client++) {
                jobs.add(() -> {
                    for (int cycle = 0; cycle < CYCLES_PER_CLIENT; cycle++) {
                        long start = System.nanoTime();
                        HttpResponse<String> response = post(path, body);
                        samples.add((System.nanoTime() - start) / 1_000_000);
                        assertEquals(201, response.statusCode(), response::body);
                    }
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(jobs, 5, TimeUnit.MINUTES)) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        Stats stats = Stats.of(samples);
        System.out.printf(
                "NFR-REN-01 escritura · ajuste con 10 clientes  n=%d  p50=%d ms  p95=%d ms  "
                        + "p99=%d ms  max=%d ms%n",
                stats.count(), stats.p50(), stats.p95(), stats.p99(), stats.max());
        assertTrue(
                stats.p95() <= MAX_P95_MILLIS,
                () -> "ajuste: p95 " + stats.p95() + " ms supera " + MAX_P95_MILLIS + " ms");
    }

    @Test
    void requirementsFor500RationsStayBelowTwoSeconds() throws Exception {
        String path = "/api/v1/production-plans/" + planId + "/requirements";
        for (int i = 0; i < WARMUP_CYCLES; i++) {
            assertEquals(200, get(path).statusCode());
        }

        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            long start = System.nanoTime();
            HttpResponse<String> response = get(path);
            samples.add((System.nanoTime() - start) / 1_000_000);
            assertEquals(200, response.statusCode(), response::body);
        }

        Stats stats = Stats.of(samples);
        System.out.printf(
                "NFR-REN-02 · 100 recetas / 500 raciones  n=%d  p50=%d ms  p95=%d ms  max=%d ms%n",
                stats.count(), stats.p50(), stats.p95(), stats.max());
        assertTrue(
                stats.p95() <= MAX_REQUIREMENTS_MILLIS,
                () -> "p95 " + stats.p95() + " ms supera " + MAX_REQUIREMENTS_MILLIS + " ms");
    }

    private String login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response::body);
        return objectMapper.readTree(response.body()).get("accessToken").asString();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Percentiles con índice nearest-rank sobre la muestra ordenada. */
    private record Stats(int count, long p50, long p95, long p99, long max) {

        static Stats of(List<Long> values) {
            List<Long> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            return new Stats(
                    sorted.size(),
                    percentile(sorted, 50),
                    percentile(sorted, 95),
                    percentile(sorted, 99),
                    sorted.get(sorted.size() - 1));
        }

        private static long percentile(List<Long> sorted, int percentile) {
            int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }
    }
}
