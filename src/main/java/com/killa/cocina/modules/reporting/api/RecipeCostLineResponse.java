package com.killa.cocina.modules.reporting.api;

import com.killa.cocina.modules.reporting.domain.RecipeCostLine;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Versión publicada dentro del informe de costes (ECO-01).
 *
 * @param totalCost coste congelado del lote base al publicar
 * @param costPerRation coste congelado por ración al publicar
 * @param previousCostPerRation coste por ración de la versión anterior; {@code null} en la primera
 * @param variationAmount diferencia con la anterior; {@code null} en la primera
 * @param variationPct variación porcentual; {@code null} en la primera o si el coste anterior era cero
 */
public record RecipeCostLineResponse(
        Long recipeId,
        String recipeName,
        Long versionId,
        int versionNumber,
        int baseRations,
        BigDecimal totalCost,
        BigDecimal costPerRation,
        Instant publishedAt,
        BigDecimal previousCostPerRation,
        BigDecimal variationAmount,
        BigDecimal variationPct) {

    public static RecipeCostLineResponse from(RecipeCostLine line) {
        return new RecipeCostLineResponse(
                line.recipeId(),
                line.recipeName(),
                line.versionId(),
                line.versionNumber(),
                line.baseRations(),
                line.totalCost(),
                line.costPerRation(),
                line.publishedAt(),
                line.previousCostPerRation(),
                line.variationAmount(),
                line.variationPct());
    }
}
