package com.killa.cocina.modules.reporting.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una versión publicada dentro del informe de costes (ECO-01).
 *
 * <p>{@code totalCost} y {@code costPerRation} son el coste congelado al publicar: consultar una
 * versión antigua devuelve los precios de su momento. La variación se calcula frente a la versión
 * publicada inmediatamente anterior de la misma receta.</p>
 *
 * @param previousCostPerRation coste por ración de la versión anterior; {@code null} en la primera
 * @param variationAmount diferencia con la anterior, en la escala monetaria; {@code null} en la primera
 * @param variationPct variación porcentual con dos decimales; {@code null} en la primera o si el coste
 *        anterior era cero
 */
public record RecipeCostLine(
        long recipeId,
        String recipeName,
        long versionId,
        int versionNumber,
        int baseRations,
        BigDecimal totalCost,
        BigDecimal costPerRation,
        Instant publishedAt,
        BigDecimal previousCostPerRation,
        BigDecimal variationAmount,
        BigDecimal variationPct) {}
