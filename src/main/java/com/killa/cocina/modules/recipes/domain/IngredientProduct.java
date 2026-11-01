package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;

/**
 * Producto visto desde recetas.
 *
 * <p>Es una proyección mínima y deliberadamente ajena a los tipos de inventario: el módulo de
 * recetas no importa sus entidades ni su modelo de dominio (AGENTS.md §6).</p>
 *
 * @param baseUnit etiqueta de la unidad base ({@code g}, {@code ml}, {@code ud})
 * @param currentCost PMP vigente por unidad base
 */
public record IngredientProduct(Long id, String name, String baseUnit, BigDecimal currentCost, boolean active) {}
