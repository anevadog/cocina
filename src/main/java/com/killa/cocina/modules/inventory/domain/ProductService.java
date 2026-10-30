package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.pagination.PageResult;
import java.util.List;

/**
 * Casos de uso del catálogo de productos (INV-01, INV-08).
 *
 * <p>La implementación transaccional vive en {@code infrastructure}; el API solo ve esta interfaz
 * (ADR-020).</p>
 */
public interface ProductService {

    /** Alta de producto con sus conversiones. La operación se audita. */
    ProductView create(long establishmentId, Long actorId, ProductRegistration registration);

    /** Edición parcial. Archivar exige stock cero. */
    ProductView update(long establishmentId, Long actorId, long productId,
            ProductModification modification);

    /** Reemplaza el conjunto completo de conversiones del producto. */
    ProductView replaceConversions(long establishmentId, Long actorId, long productId,
            List<UnitConversion> conversions);

    /** Conversiones vigentes del producto. */
    List<UnitConversion> conversions(long establishmentId, long productId);

    ProductView find(long establishmentId, long productId);

    PageResult<ProductView> list(long establishmentId, ProductFilter filter, int page, int size);
}
