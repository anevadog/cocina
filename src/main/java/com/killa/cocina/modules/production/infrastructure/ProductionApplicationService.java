package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.production.domain.ProductCatalog;
import com.killa.cocina.modules.production.domain.ProductInfo;
import com.killa.cocina.modules.production.domain.ProductionBatch;
import com.killa.cocina.modules.production.domain.ProductionBatchRepository;
import com.killa.cocina.modules.production.domain.ProductionBatchView;
import com.killa.cocina.modules.production.domain.ProductionComposition;
import com.killa.cocina.modules.production.domain.ProductionExpansion;
import com.killa.cocina.modules.production.domain.ProductionItemRegistration;
import com.killa.cocina.modules.production.domain.ProductionItemView;
import com.killa.cocina.modules.production.domain.ProductionPlan;
import com.killa.cocina.modules.production.domain.ProductionPlanItem;
import com.killa.cocina.modules.production.domain.ProductionPlanItemRepository;
import com.killa.cocina.modules.production.domain.ProductionPlanRegistration;
import com.killa.cocina.modules.production.domain.ProductionPlanRepository;
import com.killa.cocina.modules.production.domain.ProductionPlanView;
import com.killa.cocina.modules.production.domain.ProductionRequirements;
import com.killa.cocina.modules.production.domain.ProductionService;
import com.killa.cocina.modules.production.domain.ProductionStatus;
import com.killa.cocina.modules.production.domain.RecipeCompositionCatalog;
import com.killa.cocina.modules.production.domain.RequirementLine;
import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.pagination.PageResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Casos de uso de producción (PRD-01, PRD-02).
 *
 * <p>El plan fija la versión publicada de cada receta; cambiar de versión es una decisión explícita
 * y no se hereda de la receta (ADR-036). Las necesidades se agregan con las cantidades exactas y un
 * único redondeo por producto, para que el total no dependa de cómo se reparta el plan.</p>
 */
@Service
@Transactional
public class ProductionApplicationService implements ProductionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductionPlanRepository plans;

    private final ProductionPlanItemRepository planItems;

    private final ProductionBatchRepository batches;

    private final RecipeCompositionCatalog compositions;

    private final ProductCatalog products;

    private final AuditRecorder audit;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    public ProductionApplicationService(
            ProductionPlanRepository plans,
            ProductionPlanItemRepository planItems,
            ProductionBatchRepository batches,
            RecipeCompositionCatalog compositions,
            ProductCatalog products,
            AuditRecorder audit,
            ObjectMapper objectMapper,
            Clock clock) {
        this.plans = plans;
        this.planItems = planItems;
        this.batches = batches;
        this.compositions = compositions;
        this.products = products;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ProductionPlanView create(long establishmentId, Long actorId, ProductionPlanRegistration registration) {
        long actor = requireActor(actorId);
        if (plans.findByService(establishmentId, registration.serviceDate(), registration.serviceType())
                .isPresent()) {
            throw new ApiException(
                    ErrorCode.RESOURCE_IN_USE,
                    "Ya existe un plan para el servicio "
                            + registration.serviceType().name().toLowerCase()
                            + " del " + registration.serviceDate() + ".");
        }
        ProductionPlan saved =
                plans.save(ProductionPlan.create(establishmentId, registration, actor, Instant.now(clock)));
        audit.record(
                establishmentId,
                actor,
                AuditAction.PRODUCTION_PLAN_CREATED,
                "production_plan",
                saved.id(),
                null,
                planSnapshot(saved, List.of()));
        return viewOf(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductionPlanView find(long establishmentId, long planId) {
        return viewOf(requirePlan(establishmentId, planId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductionPlanView> list(long establishmentId, LocalDate serviceDate, int page, int size) {
        if (serviceDate == null) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "La petición contiene datos no válidos.",
                    List.of(new ApiError.FieldViolation("serviceDate", "es obligatoria")));
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<ProductionPlanView> items = plans.findByDate(establishmentId, serviceDate, safePage, safeSize).stream()
                .map(this::viewOf)
                .toList();
        return new PageResult<>(items, plans.countByDate(establishmentId, serviceDate), safePage, safeSize);
    }

    @Override
    public ProductionPlanView cancel(long establishmentId, Long actorId, long planId) {
        long actor = requireActor(actorId);
        ProductionPlan plan = requirePlan(establishmentId, planId);
        List<ProductionPlanItem> items = planItems.findByPlan(plan.id());
        requireNotStarted(plan, items, "cancelar");
        ProductionPlan cancelled = plans.save(plan.withStatus(ProductionStatus.CANCELLED, Instant.now(clock)));
        audit.record(
                establishmentId,
                actor,
                AuditAction.PRODUCTION_PLAN_CANCELLED,
                "production_plan",
                plan.id(),
                planSnapshot(plan, items),
                planSnapshot(cancelled, items));
        return viewOf(cancelled);
    }

    @Override
    public void delete(long establishmentId, Long actorId, long planId) {
        long actor = requireActor(actorId);
        ProductionPlan plan = requirePlan(establishmentId, planId);
        List<ProductionPlanItem> items = planItems.findByPlan(plan.id());
        requireNotStarted(plan, items, "borrar");
        planItems.replaceForPlan(plan.id(), List.of());
        plans.delete(plan.id());
        audit.record(
                establishmentId,
                actor,
                AuditAction.PRODUCTION_PLAN_DELETED,
                "production_plan",
                plan.id(),
                planSnapshot(plan, items),
                null);
    }

    @Override
    public ProductionPlanView replaceItems(
            long establishmentId, Long actorId, long planId, List<ProductionItemRegistration> items) {
        long actor = requireActor(actorId);
        ProductionPlan plan = requirePlan(establishmentId, planId);
        List<ProductionPlanItem> current = planItems.findByPlan(plan.id());
        requireNotStarted(plan, current, "modificar");

        List<ProductionPlanItem> replacements =
                resolveItems(establishmentId, plan.id(), items == null ? List.of() : items);
        planItems.replaceForPlan(plan.id(), replacements);
        ProductionPlan touched = plans.save(plan.touched(Instant.now(clock)));
        List<ProductionPlanItem> savedItems = planItems.findByPlan(plan.id());
        audit.record(
                establishmentId,
                actor,
                AuditAction.PRODUCTION_PLAN_UPDATED,
                "production_plan",
                plan.id(),
                planSnapshot(plan, current),
                planSnapshot(touched, savedItems));
        return viewOf(touched);
    }

    @Override
    public ProductionPlanView deleteItem(long establishmentId, Long actorId, long planId, long itemId) {
        long actor = requireActor(actorId);
        ProductionPlan plan = requirePlan(establishmentId, planId);
        List<ProductionPlanItem> current = planItems.findByPlan(plan.id());
        requireNotStarted(plan, current, "modificar");
        planItems
                .findById(establishmentId, plan.id(), itemId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND, "El ítem " + itemId + " no existe en el plan " + planId + "."));
        if (batches.existsByPlanItem(itemId)) {
            throw new ApiException(
                    ErrorCode.RESOURCE_IN_USE,
                    "El ítem " + itemId + " ya tiene producción registrada y no se puede quitar.");
        }
        planItems.delete(itemId);
        ProductionPlan touched = plans.save(plan.touched(Instant.now(clock)));
        List<ProductionPlanItem> savedItems = planItems.findByPlan(plan.id());
        audit.record(
                establishmentId,
                actor,
                AuditAction.PRODUCTION_PLAN_UPDATED,
                "production_plan",
                plan.id(),
                planSnapshot(plan, current),
                planSnapshot(touched, savedItems));
        return viewOf(touched);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductionRequirements requirements(long establishmentId, long planId) {
        ProductionPlan plan = requirePlan(establishmentId, planId);
        List<ProductionPlanItem> items = planItems.findByPlan(plan.id());
        Map<Long, ProductionComposition> cache = new HashMap<>();
        List<ProductionExpansion.PlanLine> lines = new ArrayList<>(items.size());
        for (ProductionPlanItem item : items) {
            ProductionComposition composition = compositionOf(establishmentId, item.recipeVersionId(), cache);
            lines.add(new ProductionExpansion.PlanLine(composition.items(), item.plannedRations()));
        }

        Map<Long, BigDecimal> totals = ProductionExpansion.consumptionByProduct(lines);
        List<RequirementLine> result = totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> requirementLine(establishmentId, entry.getKey(), entry.getValue()))
                .toList();
        return new ProductionRequirements(plan.id(), result);
    }

    private RequirementLine requirementLine(long establishmentId, long productId, BigDecimal quantity) {
        ProductInfo product = products.find(establishmentId, productId).orElse(null);
        return new RequirementLine(
                productId,
                product == null ? "Producto " + productId : product.name(),
                product == null ? "?" : product.unit(),
                quantity);
    }

    /**
     * Resuelve las versiones del plan: todas publicadas, sin repetir versión ni receta.
     *
     * <p>Dos versiones de la misma receta en el mismo plan serían dos verdades sobre las mismas
     * raciones; se rechazan para que la agregación no dependa del orden.</p>
     */
    private List<ProductionPlanItem> resolveItems(
            long establishmentId, long planId, List<ProductionItemRegistration> requested) {
        Set<Long> seenVersions = new HashSet<>();
        Set<Long> seenRecipes = new HashSet<>();
        List<ProductionPlanItem> resolved = new ArrayList<>(requested.size());
        for (ProductionItemRegistration registration : requested) {
            if (!seenVersions.add(registration.recipeVersionId())) {
                throw repeatedItem(
                        "recipeVersionId",
                        "la versión " + registration.recipeVersionId() + " está repetida en el plan");
            }
            ProductionComposition composition = compositions
                    .findPublished(establishmentId, registration.recipeVersionId())
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.RESOURCE_IN_USE,
                            "La versión " + registration.recipeVersionId()
                                    + " no existe o no está publicada; solo se planifica una versión publicada."));
            if (!composition.recipeActive()) {
                throw new ApiException(
                        ErrorCode.RESOURCE_IN_USE,
                        "La receta '" + composition.recipeName() + "' está archivada; actívala antes de planificarla.");
            }
            if (!seenRecipes.add(composition.recipeId())) {
                throw repeatedItem(
                        "recipeVersionId", "la receta '" + composition.recipeName() + "' está repetida en el plan");
            }
            resolved.add(ProductionPlanItem.create(
                    establishmentId, planId, registration.recipeVersionId(), registration.plannedRations()));
        }
        return resolved;
    }

    private ProductionPlanView viewOf(ProductionPlan plan) {
        List<ProductionPlanItem> items = planItems.findByPlan(plan.id());
        Map<Long, ProductionBatch> batchByItem =
                batches
                        .findByPlanItems(
                                items.stream().map(ProductionPlanItem::id).toList())
                        .stream()
                        .collect(Collectors.toMap(ProductionBatch::planItemId, Function.identity()));
        Map<Long, ProductionComposition> cache = new HashMap<>();
        ProductionStatus status = effectiveStatus(plan, List.copyOf(batchByItem.values()));
        List<ProductionItemView> itemViews = items.stream()
                .map(item -> itemView(plan.establishmentId(), status, item, batchByItem.get(item.id()), cache))
                .toList();
        return new ProductionPlanView(
                plan.id(),
                plan.serviceDate(),
                plan.serviceType(),
                status,
                plan.createdAt(),
                plan.updatedAt(),
                itemViews);
    }

    private ProductionItemView itemView(
            long establishmentId,
            ProductionStatus planStatus,
            ProductionPlanItem item,
            ProductionBatch batch,
            Map<Long, ProductionComposition> cache) {
        ProductionComposition composition = cache.computeIfAbsent(
                item.recipeVersionId(),
                versionId ->
                        compositions.findPublished(establishmentId, versionId).orElse(null));
        ProductionStatus fallback =
                planStatus == ProductionStatus.CANCELLED ? ProductionStatus.CANCELLED : ProductionStatus.PENDING;
        return new ProductionItemView(
                item.id(),
                composition == null ? null : composition.recipeId(),
                composition == null ? "Receta " + item.recipeVersionId() : composition.recipeName(),
                item.recipeVersionId(),
                composition == null ? 0 : composition.versionNumber(),
                item.plannedRations(),
                batch == null ? fallback : batch.status(),
                batch == null ? null : ProductionBatchView.from(batch));
    }

    private ProductionComposition compositionOf(
            long establishmentId, long versionId, Map<Long, ProductionComposition> cache) {
        return cache.computeIfAbsent(
                versionId,
                key -> compositions
                        .findPublished(establishmentId, key)
                        .orElseThrow(() -> new ApiException(
                                ErrorCode.RESOURCE_IN_USE,
                                "La versión " + key + " ya no está publicada; el plan no se puede expandir.")));
    }

    /**
     * Estado visible del plan a partir de sus batches (PRD-04).
     *
     * <p>La cancelación manual de un plan sin ejecución se conserva; en cuanto hay batches manda lo
     * que dicen ellos, porque el estado guardado es solo una ayuda de filtrado.</p>
     */
    private static ProductionStatus effectiveStatus(ProductionPlan plan, List<ProductionBatch> planBatches) {
        if (planBatches.isEmpty()) {
            return plan.status() == ProductionStatus.CANCELLED ? ProductionStatus.CANCELLED : ProductionStatus.PENDING;
        }
        if (planBatches.stream().anyMatch(batch -> batch.status() == ProductionStatus.IN_PROGRESS)) {
            return ProductionStatus.IN_PROGRESS;
        }
        if (planBatches.stream().anyMatch(batch -> batch.status() == ProductionStatus.READY)) {
            return ProductionStatus.READY;
        }
        List<ProductionBatch> active = planBatches.stream()
                .filter(batch -> batch.status() != ProductionStatus.CANCELLED)
                .toList();
        if (!active.isEmpty() && active.stream().allMatch(batch -> batch.status() == ProductionStatus.SERVED)) {
            return ProductionStatus.SERVED;
        }
        return ProductionStatus.CANCELLED;
    }

    private void requireNotStarted(ProductionPlan plan, List<ProductionPlanItem> items, String action) {
        boolean hasBatch = items.stream().anyMatch(item -> batches.existsByPlanItem(item.id()));
        if (!plan.isPending() || hasBatch) {
            throw new ApiException(
                    ErrorCode.RESOURCE_IN_USE, "Solo se puede " + action + " un plan que aún no ha empezado.");
        }
    }

    private ProductionPlan requirePlan(long establishmentId, long planId) {
        return plans.findById(establishmentId, planId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND, "El plan de producción " + planId + " no existe."));
    }

    private static long requireActor(Long actorId) {
        if (actorId == null) {
            throw new ApiException(
                    ErrorCode.UNAUTHORIZED, "Las operaciones sobre producción exigen un usuario autenticado.");
        }
        return actorId;
    }

    private static ApiException repeatedItem(String field, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }

    private String planSnapshot(ProductionPlan plan, List<ProductionPlanItem> items) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("serviceDate", plan.serviceDate().toString());
        values.put("serviceType", plan.serviceType().name());
        values.put("status", plan.status().name());
        values.put(
                "items",
                items.stream()
                        .map(item -> {
                            Map<String, Object> line = new LinkedHashMap<>();
                            line.put("id", item.id());
                            line.put("recipeVersionId", item.recipeVersionId());
                            line.put("plannedRations", item.plannedRations());
                            return line;
                        })
                        .toList());
        return objectMapper.writeValueAsString(values);
    }
}
