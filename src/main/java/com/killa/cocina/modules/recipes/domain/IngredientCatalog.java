package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto publicado por recetas hacia el catálogo de ingredientes.
 *
 * <p>Lo implementa {@code recipes/infrastructure} sobre la API de aplicación de inventario: recetas
 * nunca toca sus entidades JPA. {@link #findForUpdate} existe para congelar el coste de publicación
 * con un corte consistente del PMP (ADR-035).</p>
 */
public interface IngredientCatalog {

    Optional<IngredientProduct> find(long establishmentId, long productId);

    /** Bloquea los productos indicados en orden de {@code productId} para evitar interbloqueos. */
    List<IngredientProduct> findForUpdate(long establishmentId, Collection<Long> productIds);

    /** Convierte una cantidad a la unidad base del producto. */
    BigDecimal toBase(long establishmentId, long productId, BigDecimal quantity, String unit);
}
