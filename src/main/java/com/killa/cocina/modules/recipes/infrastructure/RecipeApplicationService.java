package com.killa.cocina.modules.recipes.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.recipes.domain.IngredientCatalog;
import com.killa.cocina.modules.recipes.domain.IngredientProduct;
import com.killa.cocina.modules.recipes.domain.Recipe;
import com.killa.cocina.modules.recipes.domain.RecipeCost;
import com.killa.cocina.modules.recipes.domain.RecipeExpansion;
import com.killa.cocina.modules.recipes.domain.RecipeFilter;
import com.killa.cocina.modules.recipes.domain.RecipeItem;
import com.killa.cocina.modules.recipes.domain.RecipeItemRegistration;
import com.killa.cocina.modules.recipes.domain.RecipeItemRepository;
import com.killa.cocina.modules.recipes.domain.RecipeItemView;
import com.killa.cocina.modules.recipes.domain.RecipeModification;
import com.killa.cocina.modules.recipes.domain.RecipeRegistration;
import com.killa.cocina.modules.recipes.domain.RecipeRepository;
import com.killa.cocina.modules.recipes.domain.RecipeService;
import com.killa.cocina.modules.recipes.domain.RecipeVersion;
import com.killa.cocina.modules.recipes.domain.RecipeVersionModification;
import com.killa.cocina.modules.recipes.domain.RecipeVersionRepository;
import com.killa.cocina.modules.recipes.domain.RecipeVersionSummary;
import com.killa.cocina.modules.recipes.domain.RecipeVersionView;
import com.killa.cocina.modules.recipes.domain.RecipeView;
import com.killa.cocina.modules.recipes.domain.ScaledIngredient;
import com.killa.cocina.modules.recipes.domain.ScaledRecipe;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.pagination.PageResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Casos de uso de recetas (REC-01..05, ECO-01).
 *
 * <p>Un borrador se edita en sitio y se publica en F4-C; una versión publicada nunca se modifica
 * (ADR-034). La auditoría se escribe en la misma transacción que el cambio.</p>
 */
@Service
@Transactional
public class RecipeApplicationService implements RecipeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RecipeRepository recipes;

    private final RecipeVersionRepository versions;

    private final RecipeItemRepository recipeItems;

    private final IngredientCatalog ingredients;

    private final AuditRecorder audit;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    public RecipeApplicationService(
            RecipeRepository recipes,
            RecipeVersionRepository versions,
            RecipeItemRepository recipeItems,
            IngredientCatalog ingredients,
            AuditRecorder audit,
            ObjectMapper objectMapper,
            Clock clock) {
        this.recipes = recipes;
        this.versions = versions;
        this.recipeItems = recipeItems;
        this.ingredients = ingredients;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public RecipeView create(long establishmentId, Long actorId, RecipeRegistration registration) {
        long actor = requireActor(actorId);
        if (recipes.existsByName(establishmentId, registration.name())) {
            throw duplicateName(registration.name());
        }
        Instant now = Instant.now(clock);
        Recipe recipe = recipes.save(Recipe.create(establishmentId, registration, now));
        RecipeVersion draft =
                versions.save(RecipeVersion.draft(establishmentId, recipe.id(), 1, registration, actor, now));

        audit.record(
                establishmentId,
                actor,
                AuditAction.RECIPE_CREATED,
                "recipe",
                recipe.id(),
                null,
                recipeSnapshot(recipe, draft));
        return new RecipeView(
                recipe.id(),
                recipe.name(),
                recipe.category(),
                recipe.active(),
                null,
                RecipeVersionSummary.from(draft),
                recipe.createdAt(),
                recipe.updatedAt());
    }

    @Override
    public RecipeView update(long establishmentId, Long actorId, long recipeId, RecipeModification modification) {
        long actor = requireActor(actorId);
        Recipe recipe = requireRecipe(establishmentId, recipeId);
        if (modification.isEmpty()) {
            return viewOf(recipe);
        }
        Recipe updated = modification.applyTo(recipe, Instant.now(clock));
        if (!updated.name().equals(recipe.name())
                && recipes.existsByNameExcluding(establishmentId, updated.name(), recipeId)) {
            throw duplicateName(updated.name());
        }
        Recipe saved = recipes.save(updated);
        AuditAction action =
                recipe.active() && !saved.active() ? AuditAction.RECIPE_ARCHIVED : AuditAction.RECIPE_UPDATED;
        audit.record(
                establishmentId,
                actor,
                action,
                "recipe",
                saved.id(),
                recipeSnapshot(recipe, null),
                recipeSnapshot(saved, null));
        return viewOf(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeView find(long establishmentId, long recipeId) {
        return viewOf(requireRecipe(establishmentId, recipeId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipeView> list(long establishmentId, RecipeFilter filter, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<RecipeView> items = recipes.search(establishmentId, filter, safePage, safeSize).stream()
                .map(this::viewOf)
                .toList();
        return new PageResult<>(items, recipes.count(establishmentId, filter), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeVersionSummary> versions(long establishmentId, long recipeId) {
        requireRecipe(establishmentId, recipeId);
        return versions.findByRecipe(establishmentId, recipeId).stream()
                .map(RecipeVersionSummary::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeVersionView version(long establishmentId, long recipeId, long versionId) {
        requireRecipe(establishmentId, recipeId);
        return versionView(establishmentId, requireVersion(establishmentId, recipeId, versionId));
    }

    @Override
    public RecipeVersionView createVersion(long establishmentId, Long actorId, long recipeId) {
        long actor = requireActor(actorId);
        Recipe recipe = requireRecipe(establishmentId, recipeId);
        if (!recipe.active()) {
            throw new ApiException(
                    ErrorCode.RESOURCE_IN_USE,
                    "La receta '" + recipe.name() + "' está archivada: actívala antes de versionarla.");
        }
        if (versions.findDraft(establishmentId, recipeId).isPresent()) {
            throw new ApiException(
                    ErrorCode.RESOURCE_IN_USE,
                    "Ya hay un borrador abierto de '" + recipe.name() + "'. Publícalo o descártalo antes.");
        }
        RecipeVersion latest = versions.latest(establishmentId, recipeId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INTERNAL_ERROR, "La receta " + recipeId + " no tiene ninguna versión."));
        RecipeVersion draft = versions.save(latest.cloneAsDraft(latest.versionNumber() + 1, actor, Instant.now(clock)));
        recipeItems.replaceForVersion(
                draft.id(),
                recipeItems.findByVersion(latest.id()).stream()
                        .map(item -> item.copyTo(draft.id()))
                        .toList());

        audit.record(
                establishmentId,
                actor,
                AuditAction.RECIPE_VERSION_CREATED,
                "recipe_version",
                draft.id(),
                versionSnapshot(latest),
                versionSnapshot(draft));
        return versionView(establishmentId, draft);
    }

    @Override
    public RecipeVersionView updateVersion(
            long establishmentId, Long actorId, long recipeId, long versionId, RecipeVersionModification modification) {
        requireActor(actorId);
        requireRecipe(establishmentId, recipeId);
        RecipeVersion version = requireDraft(establishmentId, recipeId, versionId);
        if (modification.isEmpty()) {
            return versionView(establishmentId, version);
        }
        RecipeVersion updated = versions.save(modification.applyTo(version, Instant.now(clock)));
        return versionView(establishmentId, updated);
    }

    @Override
    public RecipeVersionView replaceItems(
            long establishmentId, Long actorId, long recipeId, long versionId, List<RecipeItemRegistration> items) {
        requireActor(actorId);
        requireRecipe(establishmentId, recipeId);
        RecipeVersion version = requireDraft(establishmentId, recipeId, versionId);
        List<RecipeItemRegistration> requested = items == null ? List.of() : items;

        Set<Long> seen = new HashSet<>();
        List<RecipeItem> newItems = new ArrayList<>(requested.size());
        for (int order = 0; order < requested.size(); order++) {
            RecipeItemRegistration registration = requested.get(order);
            if (!seen.add(registration.productId())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_ERROR,
                        "La petición contiene datos no válidos.",
                        List.of(new com.killa.cocina.shared.error.ApiError.FieldViolation(
                                "items", "el producto " + registration.productId() + " está repetido en la receta")));
            }
            requireIngredient(establishmentId, registration.productId());
            newItems.add(RecipeItem.create(
                    establishmentId,
                    version.id(),
                    registration.productId(),
                    ingredients.toBase(
                            establishmentId, registration.productId(), registration.quantity(), registration.unit()),
                    registration.wastePct(),
                    order));
        }
        recipeItems.replaceForVersion(version.id(), newItems);
        return versionView(establishmentId, version);
    }

    @Override
    public void deleteDraft(long establishmentId, Long actorId, long recipeId, long versionId) {
        long actor = requireActor(actorId);
        requireRecipe(establishmentId, recipeId);
        RecipeVersion version = requireDraft(establishmentId, recipeId, versionId);
        if (versions.countByRecipe(establishmentId, recipeId) <= 1) {
            throw new ApiException(
                    ErrorCode.RESOURCE_IN_USE,
                    "No se puede descartar la única versión de la receta. Archívala si ya no la usas.");
        }
        recipeItems.deleteByVersion(version.id());
        versions.delete(version.id());
        audit.record(
                establishmentId,
                actor,
                AuditAction.RECIPE_VERSION_DELETED,
                "recipe_version",
                version.id(),
                versionSnapshot(version),
                null);
    }

    @Override
    @Transactional(readOnly = true)
    public ScaledRecipe scale(long establishmentId, long recipeId, long versionId, int rations) {
        int requested = RecipeExpansion.rations(rations);
        requireRecipe(establishmentId, recipeId);
        RecipeVersion version = requireVersion(establishmentId, recipeId, versionId);

        Map<Long, IngredientProduct> products = new HashMap<>();
        List<RecipeItem> items = recipeItems.findByVersion(version.id());
        Map<Long, BigDecimal> costs = unitCosts(establishmentId, items, products);
        RecipeCost cost = RecipeExpansion.costPerRation(items, costs);

        List<ScaledIngredient> expanded = new ArrayList<>(items.size());
        for (RecipeItem item : items) {
            IngredientProduct product = productOf(establishmentId, item.productId(), products);
            BigDecimal unitCost = costs.get(item.productId());
            BigDecimal quantity = RecipeExpansion.consumption(item.quantityBase(), item.wastePct(), requested);
            expanded.add(new ScaledIngredient(
                    item.productId(),
                    product == null ? "Producto " + item.productId() : product.name(),
                    product == null ? "?" : product.baseUnit(),
                    quantity,
                    item.wastePct(),
                    unitCost,
                    RecipeExpansion.lineCost(quantity, unitCost)));
        }
        return new ScaledRecipe(
                recipeId,
                version.id(),
                version.versionNumber(),
                requested,
                cost.perRation(),
                RecipeExpansion.batchCost(cost.perRation(), requested),
                cost.missingProducts(),
                expanded);
    }

    private RecipeView viewOf(Recipe recipe) {
        RecipeVersion current = recipe.currentVersionId() == null
                ? null
                : versions.findById(recipe.establishmentId(), recipe.id(), recipe.currentVersionId())
                        .orElse(null);
        RecipeVersion draft =
                versions.findDraft(recipe.establishmentId(), recipe.id()).orElse(null);
        return new RecipeView(
                recipe.id(),
                recipe.name(),
                recipe.category(),
                recipe.active(),
                current == null ? null : RecipeVersionSummary.from(current),
                draft == null ? null : RecipeVersionSummary.from(draft),
                recipe.createdAt(),
                recipe.updatedAt());
    }

    private RecipeVersionView versionView(long establishmentId, RecipeVersion version) {
        Map<Long, IngredientProduct> products = new HashMap<>();
        List<RecipeItem> items = recipeItems.findByVersion(version.id());
        List<RecipeItemView> itemViews = items.stream()
                .map(item -> itemView(item, productOf(establishmentId, item.productId(), products)))
                .toList();
        RecipeCost current = RecipeExpansion.costPerRation(items, unitCosts(establishmentId, items, products));
        return new RecipeVersionView(
                version.id(),
                version.recipeId(),
                version.versionNumber(),
                version.status(),
                version.baseRations(),
                version.yieldPct(),
                version.prepTimeMinutes(),
                version.instructions(),
                version.publishedAt(),
                version.publishedBy(),
                version.createdBy(),
                version.createdAt(),
                version.updatedAt(),
                version.totalCost(),
                version.costPerRation(),
                RecipeExpansion.batchCost(current.perRation(), version.baseRations()),
                current.perRation(),
                itemViews);
    }

    private RecipeItemView itemView(RecipeItem item, IngredientProduct product) {
        return new RecipeItemView(
                item.id(),
                item.productId(),
                product == null ? "Producto " + item.productId() : product.name(),
                product == null ? "?" : product.baseUnit(),
                item.quantityBase(),
                item.wastePct(),
                item.sortOrder());
    }

    private IngredientProduct productOf(long establishmentId, long productId, Map<Long, IngredientProduct> cache) {
        return cache.computeIfAbsent(
                productId, key -> ingredients.find(establishmentId, key).orElse(null));
    }

    private Map<Long, BigDecimal> unitCosts(
            long establishmentId, List<RecipeItem> items, Map<Long, IngredientProduct> cache) {
        Map<Long, BigDecimal> costs = new HashMap<>();
        for (RecipeItem item : items) {
            IngredientProduct product = productOf(establishmentId, item.productId(), cache);
            if (product != null && product.currentCost() != null) {
                costs.put(item.productId(), product.currentCost());
            }
        }
        return costs;
    }

    private Recipe requireRecipe(long establishmentId, long recipeId) {
        return recipes.findById(establishmentId, recipeId)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "La receta " + recipeId + " no existe."));
    }

    private RecipeVersion requireVersion(long establishmentId, long recipeId, long versionId) {
        return versions.findById(establishmentId, recipeId, versionId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "La versión " + versionId + " no existe en la receta " + recipeId + "."));
    }

    private RecipeVersion requireDraft(long establishmentId, long recipeId, long versionId) {
        RecipeVersion version = requireVersion(establishmentId, recipeId, versionId);
        if (!version.isDraft()) {
            throw new ApiException(
                    ErrorCode.RESOURCE_IMMUTABLE,
                    "La versión " + version.versionNumber()
                            + " está publicada y no se puede modificar. Crea una versión nueva.");
        }
        return version;
    }

    private void requireIngredient(long establishmentId, long productId) {
        if (ingredients.find(establishmentId, productId).isEmpty()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "La petición contiene datos no válidos.",
                    List.of(new com.killa.cocina.shared.error.ApiError.FieldViolation(
                            "productId", "el producto " + productId + " no existe")));
        }
    }

    private static long requireActor(Long actorId) {
        if (actorId == null) {
            throw new ApiException(
                    ErrorCode.UNAUTHORIZED, "Las operaciones sobre recetas exigen un usuario autenticado.");
        }
        return actorId;
    }

    private static ApiException duplicateName(String name) {
        return new ApiException(ErrorCode.DUPLICATE_NAME, "Ya existe una receta con el nombre '" + name + "'.");
    }

    private String recipeSnapshot(Recipe recipe, RecipeVersion version) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", recipe.name());
        values.put("category", recipe.category());
        values.put("active", recipe.active());
        if (version != null) {
            values.put("versionNumber", version.versionNumber());
            values.put("baseRations", version.baseRations());
            values.put("yieldPct", version.yieldPct().toPlainString());
        }
        return objectMapper.writeValueAsString(values);
    }

    private String versionSnapshot(RecipeVersion version) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("versionNumber", version.versionNumber());
        values.put("status", version.status().name());
        values.put("baseRations", version.baseRations());
        values.put("yieldPct", version.yieldPct().toPlainString());
        values.put("prepTimeMinutes", version.prepTimeMinutes());
        if (version.totalCost() != null) {
            values.put("totalCost", version.totalCost().toPlainString());
        }
        if (version.costPerRation() != null) {
            values.put("costPerRation", version.costPerRation().toPlainString());
        }
        return objectMapper.writeValueAsString(values);
    }
}
