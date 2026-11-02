package com.killa.cocina.modules.production.domain;

import java.util.Optional;

/** Puerto publicado por producción hacia el catálogo de productos (ADR-037). */
public interface ProductCatalog {

    Optional<ProductInfo> find(long establishmentId, long productId);
}
