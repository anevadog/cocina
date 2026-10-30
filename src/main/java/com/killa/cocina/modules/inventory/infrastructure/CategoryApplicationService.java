package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.inventory.domain.Category;
import com.killa.cocina.modules.inventory.domain.CategoryRepository;
import com.killa.cocina.modules.inventory.domain.CategoryService;
import com.killa.cocina.modules.inventory.domain.CategoryView;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.pagination.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Casos de uso de categorías de producto (INV-01).
 *
 * <p>Vive en {@code infrastructure} porque necesita transacción y serialización; el API solo ve
 * {@link CategoryService} (ADR-020).</p>
 */
@Service
@Transactional
public class CategoryApplicationService implements CategoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CategoryRepository categories;

    private final AuditRecorder audit;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    public CategoryApplicationService(CategoryRepository categories, AuditRecorder audit,
            ObjectMapper objectMapper, Clock clock) {
        this.categories = categories;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public CategoryView create(long establishmentId, Long actorId, String name) {
        Instant now = Instant.now(clock);
        Category created = Category.create(establishmentId, name, now);
        if (categories.existsByName(establishmentId, created.name())) {
            throw duplicateName(created.name());
        }
        Category saved = categories.save(created);
        audit.record(establishmentId, actorId, AuditAction.CATEGORY_CREATED, "product_category",
                saved.id(), null, snapshot(saved));
        return CategoryView.from(saved);
    }

    @Override
    public CategoryView rename(long establishmentId, Long actorId, long categoryId, String name) {
        Category current = requireCategory(establishmentId, categoryId);
        Category renamed = current.rename(name, Instant.now(clock));
        if (renamed.name().equals(current.name())) {
            return CategoryView.from(current);
        }
        if (categories.existsByNameExcluding(establishmentId, renamed.name(), categoryId)) {
            throw duplicateName(renamed.name());
        }
        Category saved = categories.save(renamed);
        audit.record(establishmentId, actorId, AuditAction.CATEGORY_UPDATED, "product_category",
                saved.id(), snapshot(current), snapshot(saved));
        return CategoryView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryView find(long establishmentId, long categoryId) {
        return CategoryView.from(requireCategory(establishmentId, categoryId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CategoryView> list(long establishmentId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<CategoryView> items = categories.findAll(establishmentId, safePage, safeSize).stream()
                .map(CategoryView::from)
                .toList();
        return new PageResult<>(items, categories.count(establishmentId), safePage, safeSize);
    }

    private Category requireCategory(long establishmentId, long categoryId) {
        return categories.findById(establishmentId, categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "La categoría " + categoryId + " no existe."));
    }

    private static ApiException duplicateName(String name) {
        return new ApiException(ErrorCode.DUPLICATE_NAME,
                "Ya existe una categoría con el nombre '" + name + "'.");
    }

    private String snapshot(Category category) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", category.name());
        return objectMapper.writeValueAsString(values);
    }
}
