package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.inventory.domain.CategoryRepository;
import com.killa.cocina.modules.inventory.domain.Conversions;
import com.killa.cocina.modules.inventory.domain.Product;
import com.killa.cocina.modules.inventory.domain.ProductFilter;
import com.killa.cocina.modules.inventory.domain.ProductModification;
import com.killa.cocina.modules.inventory.domain.ProductRegistration;
import com.killa.cocina.modules.inventory.domain.ProductRepository;
import com.killa.cocina.modules.inventory.domain.ProductService;
import com.killa.cocina.modules.inventory.domain.ProductView;
import com.killa.cocina.modules.inventory.domain.Quantities;
import com.killa.cocina.modules.inventory.domain.UnitConversion;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.pagination.PageResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Casos de uso del catálogo de productos (INV-01).
 *
 * <p>Vive en {@code infrastructure} porque necesita transacción y serialización; el API solo ve
 * {@link ProductService} (ADR-020).</p>
 */
@Service
@Transactional
public class ProductApplicationService implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository products;

    private final CategoryRepository categories;

    private final AuditRecorder audit;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    public ProductApplicationService(ProductRepository products, CategoryRepository categories,
            AuditRecorder audit, ObjectMapper objectMapper, Clock clock) {
        this.products = products;
        this.categories = categories;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ProductView create(long establishmentId, Long actorId, ProductRegistration registration) {
        requireCategory(establishmentId, registration.categoryId());
        if (products.existsByName(establishmentId, registration.name())) {
            throw duplicateName(registration.name());
        }
        Instant now = Instant.now(clock);
        Product product = new Product(null, establishmentId, registration.categoryId(),
                registration.name(), registration.baseUnit(), registration.minStock(),
                Quantities.stock(BigDecimal.ZERO), Quantities.cost(BigDecimal.ZERO),
                registration.allowNegative(), true, now, now);

        Product saved = products.save(product);
        products.replaceConversions(establishmentId, saved.id(), registration.conversions(), now);
        audit.record(establishmentId, actorId, AuditAction.PRODUCT_CREATED, "product", saved.id(),
                null, snapshot(saved, registration.conversions()));
        return ProductView.from(saved, registration.conversions());
    }

    @Override
    public ProductView update(long establishmentId, Long actorId, long productId,
            ProductModification modification) {
        Product current = requireProduct(establishmentId, productId);
        if (modification.isEmpty()) {
            return ProductView.from(current, products.conversions(productId));
        }
        Instant now = Instant.now(clock);
        Product updated = modification.applyTo(current, now);
        if (!updated.name().equals(current.name())
                && products.existsByNameExcluding(establishmentId, updated.name(), productId)) {
            throw duplicateName(updated.name());
        }
        if (current.active() && !updated.active() && !current.canBeArchived()) {
            throw new ApiException(ErrorCode.RESOURCE_IN_USE,
                    "No se puede archivar un producto con stock distinto de cero. Ajusta el stock primero.");
        }

        Product saved = products.save(updated);
        List<UnitConversion> conversions = products.conversions(productId);
        AuditAction action = current.active() && !saved.active()
                ? AuditAction.PRODUCT_ARCHIVED
                : AuditAction.PRODUCT_UPDATED;
        audit.record(establishmentId, actorId, action, "product", saved.id(),
                snapshot(current, conversions), snapshot(saved, conversions));
        return ProductView.from(saved, conversions);
    }

    @Override
    public ProductView replaceConversions(long establishmentId, Long actorId, long productId,
            List<UnitConversion> conversions) {
        Product product = requireProduct(establishmentId, productId);
        List<UnitConversion> validated = Conversions.validate(conversions, product.baseUnit());
        List<UnitConversion> before = products.conversions(productId);

        products.replaceConversions(establishmentId, productId, validated, Instant.now(clock));
        audit.record(establishmentId, actorId, AuditAction.PRODUCT_CONVERSIONS_UPDATED, "product",
                productId, snapshot(product, before), snapshot(product, validated));
        return ProductView.from(product, validated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitConversion> conversions(long establishmentId, long productId) {
        requireProduct(establishmentId, productId);
        return products.conversions(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductView find(long establishmentId, long productId) {
        Product product = requireProduct(establishmentId, productId);
        return ProductView.from(product, products.conversions(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductView> list(long establishmentId, ProductFilter filter, int page,
            int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // El catálogo es pequeño (decenas o cientos de productos) y las conversiones se piden por
        // producto; si algún día crece, se resuelve con una consulta única y agrupación en memoria.
        List<ProductView> items = products.search(establishmentId, filter, safePage, safeSize).stream()
                .map(product -> ProductView.from(product, products.conversions(product.id())))
                .toList();
        return new PageResult<>(items, products.count(establishmentId, filter), safePage, safeSize);
    }

    private Product requireProduct(long establishmentId, long productId) {
        return products.findById(establishmentId, productId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "El producto " + productId + " no existe."));
    }

    private void requireCategory(long establishmentId, long categoryId) {
        categories.findById(establishmentId, categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "La petición contiene datos no válidos.",
                        List.of(new com.killa.cocina.shared.error.ApiError.FieldViolation(
                                "categoryId", "la categoría " + categoryId + " no existe"))));
    }

    private static ApiException duplicateName(String name) {
        return new ApiException(ErrorCode.DUPLICATE_NAME,
                "Ya existe un producto con el nombre '" + name + "'.");
    }

    private String snapshot(Product product, List<UnitConversion> conversions) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", product.name());
        values.put("categoryId", product.categoryId());
        values.put("baseUnit", product.baseUnit().name());
        values.put("minStock", product.minStock().toPlainString());
        values.put("allowNegative", product.allowNegative());
        values.put("active", product.active());
        values.put("conversions", conversions.stream()
                .map(conversion -> Map.of("unit", conversion.unit(),
                        "factorToBase", conversion.factorToBase().stripTrailingZeros().toPlainString()))
                .toList());
        return objectMapper.writeValueAsString(values);
    }
}
