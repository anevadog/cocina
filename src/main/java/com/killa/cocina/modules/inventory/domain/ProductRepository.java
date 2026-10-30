package com.killa.cocina.modules.inventory.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de productos y sus conversiones.
 *
 * <p>Las conversiones se leen y se reemplazan como conjunto: un producto tiene pocas y siempre se
 * consultan juntas, así que no se expone un CRUD por conversión.</p>
 */
public interface ProductRepository {

    Optional<Product> findById(long establishmentId, long productId);

    boolean existsByName(long establishmentId, String name);

    /** Comprueba el nombre excluyendo un producto, para renombrar sin chocar consigo mismo. */
    boolean existsByNameExcluding(long establishmentId, String name, long excludedProductId);

    Product save(Product product);

    List<Product> search(long establishmentId, ProductFilter filter, int page, int size);

    long count(long establishmentId, ProductFilter filter);

    List<UnitConversion> conversions(long productId);

    /** Sustituye todas las conversiones del producto; la lista vacía las elimina. */
    void replaceConversions(long establishmentId, long productId, List<UnitConversion> conversions,
            java.time.Instant now);
}
