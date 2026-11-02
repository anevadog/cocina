package com.killa.cocina.modules.reporting.domain;

import com.killa.cocina.modules.inventory.domain.Quantities;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Informe de costes por versión publicada (ECO-01).
 *
 * <p>Agrupa el histórico por receta y calcula la evolución del coste por ración entre versiones
 * consecutivas. Los importes salen ya redondeados por {@link Quantities}; aquí solo se decide el
 * redondeo del porcentaje, a dos decimales.</p>
 */
public record RecipeCostReport(int recipeCount, int versionCount, List<RecipeCostLine> versions) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final int PCT_SCALE = 2;

    public RecipeCostReport {
        versions = List.copyOf(versions);
    }

    /** Construye el informe a partir del histórico publicado, sin recalcular ningún coste. */
    public static RecipeCostReport from(List<RecipeCostSnapshot> snapshots) {
        List<RecipeCostSnapshot> ordered = snapshots.stream()
                .sorted(Comparator.comparing(RecipeCostSnapshot::recipeName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(RecipeCostSnapshot::versionNumber))
                .toList();

        Map<Long, BigDecimal> previousByRecipe = new HashMap<>();
        Set<Long> recipes = new TreeSet<>();
        List<RecipeCostLine> lines = new ArrayList<>(ordered.size());

        for (RecipeCostSnapshot snapshot : ordered) {
            BigDecimal previous = previousByRecipe.get(snapshot.recipeId());
            lines.add(new RecipeCostLine(
                    snapshot.recipeId(),
                    snapshot.recipeName(),
                    snapshot.versionId(),
                    snapshot.versionNumber(),
                    snapshot.baseRations(),
                    snapshot.totalCost(),
                    snapshot.costPerRation(),
                    snapshot.publishedAt(),
                    previous,
                    variation(previous, snapshot.costPerRation()),
                    variationPct(previous, snapshot.costPerRation())));
            if (snapshot.costPerRation() != null) {
                previousByRecipe.put(snapshot.recipeId(), snapshot.costPerRation());
            }
            recipes.add(snapshot.recipeId());
        }

        return new RecipeCostReport(recipes.size(), lines.size(), lines);
    }

    private static BigDecimal variation(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null) {
            return null;
        }
        return Quantities.cost(current.subtract(previous));
    }

    private static BigDecimal variationPct(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(HUNDRED).divide(previous, PCT_SCALE, RoundingMode.HALF_UP);
    }
}
