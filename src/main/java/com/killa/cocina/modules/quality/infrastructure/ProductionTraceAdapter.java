package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.inventory.domain.MovementType;
import com.killa.cocina.modules.inventory.domain.StockMovement;
import com.killa.cocina.modules.inventory.domain.StockMovementRepository;
import com.killa.cocina.modules.production.domain.ProductionBatch;
import com.killa.cocina.modules.production.domain.ProductionBatchRepository;
import com.killa.cocina.modules.production.domain.ProductionComposition;
import com.killa.cocina.modules.production.domain.ProductionPlan;
import com.killa.cocina.modules.production.domain.ProductionPlanItem;
import com.killa.cocina.modules.production.domain.ProductionPlanItemRepository;
import com.killa.cocina.modules.production.domain.ProductionPlanRepository;
import com.killa.cocina.modules.production.domain.RecipeCompositionCatalog;
import com.killa.cocina.modules.quality.domain.ProductionConsumptionTrace;
import com.killa.cocina.modules.quality.domain.ProductionTraceCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Adaptador del puerto {@link ProductionTraceCatalog} sobre el kardex y los puertos publicados de
 * producción (ADR-040).
 *
 * <p>Calidad nunca toca entidades JPA de producción: recorre los movimientos
 * {@code PRODUCTION_OUT} del lote y resuelve batch → ítem de plan → plan y versión de receta con los
 * mismos puertos que usa el informe de producción.</p>
 */
@Component
class ProductionTraceAdapter implements ProductionTraceCatalog {

    private static final String PRODUCTION_REFERENCE = "production_batch";

    private final StockMovementRepository movements;

    private final ProductionBatchRepository batches;

    private final ProductionPlanItemRepository planItems;

    private final ProductionPlanRepository plans;

    private final RecipeCompositionCatalog compositions;

    ProductionTraceAdapter(
            StockMovementRepository movements,
            ProductionBatchRepository batches,
            ProductionPlanItemRepository planItems,
            ProductionPlanRepository plans,
            RecipeCompositionCatalog compositions) {
        this.movements = movements;
        this.batches = batches;
        this.planItems = planItems;
        this.plans = plans;
        this.compositions = compositions;
    }

    @Override
    public List<ProductionConsumptionTrace> consumptionOfLot(long establishmentId, long lotId) {
        List<ProductionConsumptionTrace> traces = new ArrayList<>();
        for (StockMovement movement : movements.findByLot(establishmentId, lotId, MovementType.PRODUCTION_OUT)) {
            if (!PRODUCTION_REFERENCE.equals(movement.referenceType()) || movement.referenceId() == null) {
                continue;
            }
            resolve(establishmentId, movement).ifPresent(traces::add);
        }
        return List.copyOf(traces);
    }

    private Optional<ProductionConsumptionTrace> resolve(long establishmentId, StockMovement movement) {
        Optional<ProductionBatch> batch = batches.findById(establishmentId, movement.referenceId());
        if (batch.isEmpty()) {
            return Optional.empty();
        }
        Optional<ProductionPlanItem> planItem =
                planItems.findById(establishmentId, batch.get().planItemId());
        if (planItem.isEmpty()) {
            return Optional.empty();
        }
        Optional<ProductionPlan> plan =
                plans.findById(establishmentId, planItem.get().planId());
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        Optional<ProductionComposition> composition =
                compositions.findPublished(establishmentId, batch.get().recipeVersionId());

        return Optional.of(new ProductionConsumptionTrace(
                batch.get().id(),
                plan.get().id(),
                plan.get().serviceDate(),
                plan.get().serviceType().name(),
                composition.map(ProductionComposition::recipeId).orElse(null),
                composition.map(ProductionComposition::recipeName).orElse(null),
                composition.map(ProductionComposition::versionNumber).orElse(0),
                movement.quantityBase(),
                movement.occurredAt()));
    }
}
