package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.inventory.domain.Quantities;
import com.killa.cocina.modules.inventory.domain.StockService;
import com.killa.cocina.modules.production.domain.ConsumptionReport;
import com.killa.cocina.modules.production.domain.DeviationReport;
import com.killa.cocina.modules.production.domain.ProductCatalog;
import com.killa.cocina.modules.production.domain.ProductInfo;
import com.killa.cocina.modules.production.domain.ProductionBatch;
import com.killa.cocina.modules.production.domain.ProductionBatchRepository;
import com.killa.cocina.modules.production.domain.ProductionComposition;
import com.killa.cocina.modules.production.domain.ProductionExpansion;
import com.killa.cocina.modules.production.domain.ProductionPeriod;
import com.killa.cocina.modules.production.domain.ProductionPlan;
import com.killa.cocina.modules.production.domain.ProductionPlanItem;
import com.killa.cocina.modules.production.domain.ProductionPlanItemRepository;
import com.killa.cocina.modules.production.domain.ProductionPlanRepository;
import com.killa.cocina.modules.production.domain.ProductionReportService;
import com.killa.cocina.modules.production.domain.RecipeCompositionCatalog;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Informes de producción sobre el histórico de ejecuciones (PRD-05, ECO-03).
 *
 * <p>El periodo se agrupa por la fecha de fin de cada batch, que es cuando el stock se movió, y los
 * ajustes por su {@code occurred_at}. Así el consumo real y los ajustes que lo corrigen caen en la
 * misma ventana temporal y la desviación cuadra: {@code real − teórico = merma + ajustes}.</p>
 */
@Service
@Transactional(readOnly = true)
public class ProductionReportApplicationService implements ProductionReportService {

    private static final BigDecimal ZERO = Quantities.stock(BigDecimal.ZERO);

    private final ProductionPlanRepository plans;

    private final ProductionPlanItemRepository planItems;

    private final ProductionBatchRepository batches;

    private final RecipeCompositionCatalog compositions;

    private final ProductCatalog products;

    private final StockService stock;

    public ProductionReportApplicationService(
            ProductionPlanRepository plans,
            ProductionPlanItemRepository planItems,
            ProductionBatchRepository batches,
            RecipeCompositionCatalog compositions,
            ProductCatalog products,
            StockService stock) {
        this.plans = plans;
        this.planItems = planItems;
        this.batches = batches;
        this.compositions = compositions;
        this.products = products;
        this.stock = stock;
    }

    @Override
    public DeviationReport deviations(long establishmentId, ProductionPeriod period) {
        List<ProductionBatch> finished = finishedIn(establishmentId, period);
        Map<Long, ProductionPlanItem> itemCache = new HashMap<>();
        Map<Long, ProductionPlan> planCache = new HashMap<>();
        Map<Long, ProductionComposition> compositionCache = new HashMap<>();

        List<DeviationReport.DeviationLine> lines = finished.stream()
                .filter(batch -> batch.deviation() != 0)
                .map(batch -> deviationLine(establishmentId, batch, itemCache, planCache, compositionCache))
                .toList();
        return new DeviationReport(period.from(), period.to(), lines);
    }

    @Override
    public ConsumptionReport consumption(long establishmentId, ProductionPeriod period) {
        List<ProductionBatch> finished = finishedIn(establishmentId, period);
        Map<Long, ProductionComposition> compositionCache = new HashMap<>();
        List<ProductionExpansion.PlanLine> theoreticalLines = new ArrayList<>();
        List<Long> batchIds = new ArrayList<>();

        for (ProductionBatch batch : finished) {
            ProductionComposition composition =
                    compositionOf(establishmentId, batch.recipeVersionId(), compositionCache);
            theoreticalLines.add(new ProductionExpansion.PlanLine(composition.items(), batch.rations()));
            batchIds.add(batch.id());
        }

        Map<Long, BigDecimal> theoretical = ProductionExpansion.theoreticalByProduct(theoreticalLines);
        Map<Long, BigDecimal> production = stock.productionConsumption(establishmentId, batchIds);
        Map<Long, BigDecimal> adjustments =
                stock.netAdjustmentOutflow(establishmentId, period.startInclusive(), period.endExclusive());

        TreeSet<Long> productIds = new TreeSet<>();
        productIds.addAll(theoretical.keySet());
        productIds.addAll(production.keySet());
        productIds.addAll(adjustments.keySet());

        List<ConsumptionReport.ProductConsumption> lines = productIds.stream()
                .map(productId -> productConsumption(
                        establishmentId,
                        productId,
                        theoretical.getOrDefault(productId, ZERO),
                        production.getOrDefault(productId, ZERO),
                        adjustments.getOrDefault(productId, ZERO)))
                .filter(line -> line.theoreticalQuantity().signum() != 0
                        || line.productionQuantity().signum() != 0
                        || line.adjustmentQuantity().signum() != 0)
                .toList();
        return new ConsumptionReport(period.from(), period.to(), lines);
    }

    private List<ProductionBatch> finishedIn(long establishmentId, ProductionPeriod period) {
        return batches.findFinishedBetween(establishmentId, period.startInclusive(), period.endExclusive());
    }

    private DeviationReport.DeviationLine deviationLine(
            long establishmentId,
            ProductionBatch batch,
            Map<Long, ProductionPlanItem> itemCache,
            Map<Long, ProductionPlan> planCache,
            Map<Long, ProductionComposition> compositionCache) {
        ProductionPlanItem item = itemOf(establishmentId, batch.planItemId(), itemCache);
        ProductionPlan plan = planCache.computeIfAbsent(
                item.planId(),
                planId -> plans.findById(establishmentId, planId)
                        .orElseThrow(() -> new ApiException(
                                ErrorCode.RESOURCE_IN_USE,
                                "El plan " + planId + " de un batch del informe ya no existe.")));
        ProductionComposition composition = compositionCache.computeIfAbsent(
                batch.recipeVersionId(),
                versionId ->
                        compositions.findPublished(establishmentId, versionId).orElse(null));

        return new DeviationReport.DeviationLine(
                plan.id(),
                plan.serviceDate(),
                plan.serviceType(),
                item.id(),
                batch.id(),
                composition == null ? null : composition.recipeId(),
                composition == null ? "Receta " + batch.recipeVersionId() : composition.recipeName(),
                composition == null ? 0 : composition.versionNumber(),
                batch.plannedRations(),
                batch.rations(),
                batch.deviation(),
                batch.deviationReason(),
                batch.finishedAt(),
                batch.finishedBy());
    }

    private ProductionPlanItem itemOf(long establishmentId, long itemId, Map<Long, ProductionPlanItem> itemCache) {
        return itemCache.computeIfAbsent(
                itemId,
                id -> planItems
                        .findById(establishmentId, id)
                        .orElseThrow(() -> new ApiException(
                                ErrorCode.RESOURCE_IN_USE,
                                "El ítem de plan " + id + " de un batch del informe ya no existe.")));
    }

    private ProductionComposition compositionOf(
            long establishmentId, long versionId, Map<Long, ProductionComposition> compositionCache) {
        return compositionCache.computeIfAbsent(
                versionId,
                key -> compositions
                        .findPublished(establishmentId, key)
                        .orElseThrow(() -> new ApiException(
                                ErrorCode.RESOURCE_IN_USE,
                                "La versión " + key + " de una producción del informe ya no está publicada.")));
    }

    private ConsumptionReport.ProductConsumption productConsumption(
            long establishmentId,
            long productId,
            BigDecimal theoreticalQuantity,
            BigDecimal productionQuantity,
            BigDecimal adjustmentQuantity) {
        BigDecimal waste = Quantities.stock(productionQuantity.subtract(theoreticalQuantity));
        BigDecimal real = Quantities.stock(productionQuantity.add(adjustmentQuantity));
        BigDecimal deviation = Quantities.stock(real.subtract(theoreticalQuantity));
        ProductInfo info = products.find(establishmentId, productId).orElse(null);

        return new ConsumptionReport.ProductConsumption(
                productId,
                info == null ? "Producto " + productId : info.name(),
                info == null ? "" : info.unit(),
                Quantities.stock(theoreticalQuantity),
                Quantities.stock(productionQuantity),
                waste,
                Quantities.stock(adjustmentQuantity),
                real,
                deviation);
    }
}
