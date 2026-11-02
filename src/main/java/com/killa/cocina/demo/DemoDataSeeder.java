package com.killa.cocina.demo;

import com.killa.cocina.config.EstablishmentProperties;
import com.killa.cocina.modules.inventory.domain.BaseUnit;
import com.killa.cocina.modules.inventory.domain.CategoryService;
import com.killa.cocina.modules.inventory.domain.ProductRegistration;
import com.killa.cocina.modules.inventory.domain.ProductService;
import com.killa.cocina.modules.inventory.domain.ProductView;
import com.killa.cocina.modules.inventory.domain.PurchaseRegistration;
import com.killa.cocina.modules.inventory.domain.StockService;
import com.killa.cocina.modules.inventory.domain.UnitConversion;
import com.killa.cocina.modules.production.domain.ProductionItemRegistration;
import com.killa.cocina.modules.production.domain.ProductionPlanRegistration;
import com.killa.cocina.modules.production.domain.ProductionPlanView;
import com.killa.cocina.modules.production.domain.ProductionService;
import com.killa.cocina.modules.production.domain.ServiceType;
import com.killa.cocina.modules.quality.domain.IncidentRegistration;
import com.killa.cocina.modules.quality.domain.IncidentService;
import com.killa.cocina.modules.quality.domain.IncidentSeverity;
import com.killa.cocina.modules.quality.domain.TemperatureKind;
import com.killa.cocina.modules.quality.domain.TemperatureLogRegistration;
import com.killa.cocina.modules.quality.domain.TemperatureService;
import com.killa.cocina.modules.quality.domain.WasteRegistration;
import com.killa.cocina.modules.quality.domain.WasteService;
import com.killa.cocina.modules.recipes.domain.RecipeItemRegistration;
import com.killa.cocina.modules.recipes.domain.RecipeRegistration;
import com.killa.cocina.modules.recipes.domain.RecipeService;
import com.killa.cocina.modules.recipes.domain.RecipeVersionView;
import com.killa.cocina.modules.recipes.domain.RecipeView;
import com.killa.cocina.modules.suppliers.domain.SupplierRegistration;
import com.killa.cocina.modules.suppliers.domain.SupplierService;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos de demostración para una base vacía (PLA-05).
 *
 * <p>Solo se activa con el perfil {@code demo} y se salta si ya hay productos, para que arrancar dos
 * veces no duplique nada. Usa los mismos servicios que la API, así que respeta las reglas de dominio
 * (PMP, versiones publicadas, auditoría) en lugar de insertar filas a mano. Se ejecuta después de
 * {@code AdminBootstrap} (orden 0): el establecimiento tiene que existir antes de la primera alta.</p>
 */
@Component
@Profile("demo")
@Order(100)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final EstablishmentProperties establishment;

    private final UserService users;

    private final CategoryService categories;

    private final SupplierService suppliers;

    private final ProductService products;

    private final StockService stock;

    private final RecipeService recipes;

    private final ProductionService production;

    private final TemperatureService temperatures;

    private final WasteService waste;

    private final IncidentService incidents;

    private final JdbcTemplate jdbc;

    @Value("${cocina.demo.password:Demo2026!segura}")
    private String password;

    public DemoDataSeeder(
            EstablishmentProperties establishment,
            UserService users,
            CategoryService categories,
            SupplierService suppliers,
            ProductService products,
            StockService stock,
            RecipeService recipes,
            ProductionService production,
            TemperatureService temperatures,
            WasteService waste,
            IncidentService incidents,
            JdbcTemplate jdbc) {
        this.establishment = establishment;
        this.users = users;
        this.categories = categories;
        this.suppliers = suppliers;
        this.products = products;
        this.stock = stock;
        this.recipes = recipes;
        this.production = production;
        this.temperatures = temperatures;
        this.waste = waste;
        this.incidents = incidents;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long existing = jdbc.queryForObject("select count(*) from products", Long.class);
        if (existing != null && existing > 0) {
            log.info("Datos de demostración ya presentes ({} productos); no se vuelve a sembrar.", existing);
            return;
        }

        long establishmentId = establishment.id();
        long adminId = seedUsers(establishmentId);
        Map<String, Long> categoryIds = seedCategories(establishmentId, adminId);
        long supplierId = seedSuppliers(establishmentId, adminId);
        Map<String, Long> productIds = seedProducts(establishmentId, adminId, categoryIds);
        seedPurchases(establishmentId, adminId, supplierId, productIds);
        Map<String, Long> versions = seedRecipes(establishmentId, adminId, productIds);
        seedProduction(establishmentId, adminId, versions);
        seedQuality(establishmentId, adminId, productIds);

        log.info(
                "Datos de demostración creados: {} usuarios, {} productos, {} recetas, un plan y una incidencia. "
                        + "Acceso: demo.admin (contraseña en cocina.demo.password).",
                users.list(establishmentId, 0, 100).total(),
                productIds.size(),
                versions.size());
    }

    private long seedUsers(long establishmentId) {
        long admin = users.create(
                        establishmentId,
                        null,
                        new UserRegistration("demo.admin", "Administradora demo", password, Role.ADMIN))
                .id();
        users.create(
                establishmentId,
                admin,
                new UserRegistration("demo.manager", "Jefa de cocina demo", password, Role.KITCHEN_MANAGER));
        users.create(establishmentId, admin, new UserRegistration("demo.cook", "Cocinero demo", password, Role.COOK));
        users.create(
                establishmentId, admin, new UserRegistration("demo.store", "Almacén demo", password, Role.STOREKEEPER));
        return admin;
    }

    private Map<String, Long> seedCategories(long establishmentId, long actorId) {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (String name : List.of("Verduras", "Carnes y pescados", "Secos", "Lácteos")) {
            ids.put(name, categories.create(establishmentId, actorId, name).id());
        }
        return ids;
    }

    private long seedSuppliers(long establishmentId, long actorId) {
        return suppliers
                .create(
                        establishmentId,
                        actorId,
                        new SupplierRegistration("Mercado Central", "pedidos@mercadocentral.example"))
                .id();
    }

    private Map<String, Long> seedProducts(long establishmentId, long actorId, Map<String, Long> categoryIds) {
        Map<String, Long> ids = new LinkedHashMap<>();
        ids.put(
                "patata",
                createProduct(
                                establishmentId,
                                actorId,
                                "Patata",
                                categoryIds.get("Verduras"),
                                BaseUnit.G,
                                "5000",
                                List.of(new UnitConversion("kg", new BigDecimal("1000"))))
                        .id());
        ids.put(
                "cebolla",
                createProduct(
                                establishmentId,
                                actorId,
                                "Cebolla",
                                categoryIds.get("Verduras"),
                                BaseUnit.G,
                                "2000",
                                List.of(new UnitConversion("kg", new BigDecimal("1000"))))
                        .id());
        ids.put(
                "tomate",
                createProduct(
                                establishmentId,
                                actorId,
                                "Tomate",
                                categoryIds.get("Verduras"),
                                BaseUnit.G,
                                "3000",
                                List.of(new UnitConversion("kg", new BigDecimal("1000"))))
                        .id());
        ids.put(
                "harina",
                createProduct(
                                establishmentId,
                                actorId,
                                "Harina",
                                categoryIds.get("Secos"),
                                BaseUnit.G,
                                "5000",
                                List.of(new UnitConversion("kg", new BigDecimal("1000"))))
                        .id());
        ids.put(
                "arroz",
                createProduct(
                                establishmentId,
                                actorId,
                                "Arroz",
                                categoryIds.get("Secos"),
                                BaseUnit.G,
                                "3000",
                                List.of(new UnitConversion("kg", new BigDecimal("1000"))))
                        .id());
        ids.put(
                "sal",
                createProduct(
                                establishmentId,
                                actorId,
                                "Sal",
                                categoryIds.get("Secos"),
                                BaseUnit.G,
                                "1000",
                                List.of(new UnitConversion("kg", new BigDecimal("1000"))))
                        .id());
        ids.put(
                "pollo",
                createProduct(
                                establishmentId,
                                actorId,
                                "Pollo",
                                categoryIds.get("Carnes y pescados"),
                                BaseUnit.G,
                                "4000",
                                List.of(new UnitConversion("kg", new BigDecimal("1000"))))
                        .id());
        ids.put(
                "aceite",
                createProduct(
                                establishmentId,
                                actorId,
                                "Aceite de oliva",
                                categoryIds.get("Secos"),
                                BaseUnit.ML,
                                "2000",
                                List.of(new UnitConversion("l", new BigDecimal("1000"))))
                        .id());
        ids.put(
                "nata",
                createProduct(
                                establishmentId,
                                actorId,
                                "Nata",
                                categoryIds.get("Lácteos"),
                                BaseUnit.ML,
                                "1000",
                                List.of(new UnitConversion("l", new BigDecimal("1000"))))
                        .id());
        ids.put(
                "huevo",
                createProduct(
                                establishmentId,
                                actorId,
                                "Huevo",
                                categoryIds.get("Lácteos"),
                                BaseUnit.UNIT,
                                "12",
                                List.of(new UnitConversion("docena", new BigDecimal("12"))))
                        .id());
        return ids;
    }

    private ProductView createProduct(
            long establishmentId,
            long actorId,
            String name,
            long categoryId,
            BaseUnit unit,
            String minStock,
            List<UnitConversion> conversions) {
        return products.create(
                establishmentId,
                actorId,
                new ProductRegistration(name, categoryId, unit, new BigDecimal(minStock), false, conversions));
    }

    private void seedPurchases(long establishmentId, long actorId, long supplierId, Map<String, Long> productIds) {
        Instant now = Instant.now();
        purchase(establishmentId, actorId, supplierId, productIds.get("patata"), "20", "kg", "1.20", now, 10);
        purchase(establishmentId, actorId, supplierId, productIds.get("cebolla"), "10", "kg", "0.90", now, 15);
        purchase(establishmentId, actorId, supplierId, productIds.get("tomate"), "15", "kg", "2.10", now, 4);
        purchase(establishmentId, actorId, supplierId, productIds.get("harina"), "25", "kg", "0.80", now, null);
        purchase(establishmentId, actorId, supplierId, productIds.get("arroz"), "20", "kg", "1.50", now, null);
        purchase(establishmentId, actorId, supplierId, productIds.get("sal"), "5", "kg", "0.60", now, null);
        purchase(establishmentId, actorId, supplierId, productIds.get("pollo"), "15", "kg", "6.50", now, 2);
        purchase(establishmentId, actorId, supplierId, productIds.get("aceite"), "10", "l", "7.50", now, null);
        purchase(establishmentId, actorId, supplierId, productIds.get("nata"), "5", "l", "3.20", now, 5);
        purchase(establishmentId, actorId, supplierId, productIds.get("huevo"), "10", "docena", "2.40", now, 20);
    }

    private void purchase(
            long establishmentId,
            long actorId,
            long supplierId,
            long productId,
            String quantity,
            String unit,
            String unitPrice,
            Instant now,
            Integer expiresInDays) {
        stock.purchase(
                establishmentId,
                actorId,
                productId,
                new PurchaseRegistration(
                        supplierId,
                        new BigDecimal(quantity),
                        unit,
                        new BigDecimal(unitPrice),
                        now.minus(Duration.ofHours(2)),
                        expiresInDays == null ? null : now.plus(Duration.ofDays(expiresInDays))));
    }

    private Map<String, Long> seedRecipes(long establishmentId, long actorId, Map<String, Long> productIds) {
        Map<String, Long> versions = new LinkedHashMap<>();
        versions.put(
                "tortilla",
                publishRecipe(
                        establishmentId,
                        actorId,
                        new RecipeRegistration(
                                "Tortilla de patata",
                                "Huevos",
                                4,
                                new BigDecimal("95"),
                                25,
                                "Pochar la patata y cuajar."),
                        List.of(
                                item(productIds.get("patata"), "250", "g", "0"),
                                item(productIds.get("cebolla"), "50", "g", "0"),
                                item(productIds.get("huevo"), "2", "ud", "0"),
                                item(productIds.get("aceite"), "10", "ml", "0"),
                                item(productIds.get("sal"), "2", "g", "0"))));
        versions.put(
                "arroz",
                publishRecipe(
                        establishmentId,
                        actorId,
                        new RecipeRegistration(
                                "Arroz con pollo",
                                "Arroces",
                                4,
                                new BigDecimal("90"),
                                35,
                                "Sofreír y cocer 18 minutos."),
                        List.of(
                                item(productIds.get("arroz"), "100", "g", "0"),
                                item(productIds.get("pollo"), "150", "g", "5"),
                                item(productIds.get("aceite"), "10", "ml", "0"),
                                item(productIds.get("sal"), "3", "g", "0"))));
        versions.put(
                "salsa",
                publishRecipe(
                        establishmentId,
                        actorId,
                        new RecipeRegistration(
                                "Salsa de tomate", "Salsas", 8, new BigDecimal("80"), 40, "Cocer a fuego lento."),
                        List.of(
                                item(productIds.get("tomate"), "500", "g", "0"),
                                item(productIds.get("cebolla"), "100", "g", "0"),
                                item(productIds.get("aceite"), "20", "ml", "0"),
                                item(productIds.get("sal"), "5", "g", "0"))));
        return versions;
    }

    private RecipeItemRegistration item(long productId, String quantity, String unit, String wastePct) {
        return new RecipeItemRegistration(productId, new BigDecimal(quantity), unit, new BigDecimal(wastePct));
    }

    private long publishRecipe(
            long establishmentId, long actorId, RecipeRegistration registration, List<RecipeItemRegistration> items) {
        RecipeView recipe = recipes.create(establishmentId, actorId, registration);
        recipes.replaceItems(
                establishmentId, actorId, recipe.id(), recipe.draft().id(), items);
        RecipeVersionView published = recipes.publish(
                establishmentId, actorId, recipe.id(), recipe.draft().id());
        return published.id();
    }

    private void seedProduction(long establishmentId, long actorId, Map<String, Long> versions) {
        ZoneId business = ZoneId.of(establishment.timezone());
        LocalDate serviceDate = LocalDate.now(business).plusDays(1);
        ProductionPlanView plan = production.create(
                establishmentId, actorId, new ProductionPlanRegistration(serviceDate, ServiceType.LUNCH));
        production.replaceItems(
                establishmentId,
                actorId,
                plan.id(),
                List.of(
                        new ProductionItemRegistration(versions.get("tortilla"), 8),
                        new ProductionItemRegistration(versions.get("arroz"), 10),
                        new ProductionItemRegistration(versions.get("salsa"), 12)));
    }

    private void seedQuality(long establishmentId, long actorId, Map<String, Long> productIds) {
        temperatures.register(
                establishmentId,
                actorId,
                new TemperatureLogRegistration(
                        TemperatureKind.EQUIPMENT,
                        "Cámara 1",
                        new BigDecimal("3.4"),
                        BigDecimal.ZERO,
                        new BigDecimal("5"),
                        null));
        waste.register(
                establishmentId,
                actorId,
                new WasteRegistration(
                        productIds.get("patata"), null, new BigDecimal("300"), "Patata con golpe en la recepción"));
        incidents.register(
                establishmentId,
                actorId,
                new IncidentRegistration("La cámara 2 amaneció con hielo en el evaporador.", IncidentSeverity.MEDIUM));
    }
}
