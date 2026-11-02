package com.killa.cocina.modules.production.infrastructure;

import com.killa.cocina.modules.inventory.domain.ProductRepository;
import com.killa.cocina.modules.production.domain.ProductCatalog;
import com.killa.cocina.modules.production.domain.ProductInfo;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Adaptador del puerto {@link ProductCatalog} sobre el catálogo de inventario (ADR-037). */
@Component
class ProductCatalogAdapter implements ProductCatalog {

    private final ProductRepository products;

    ProductCatalogAdapter(ProductRepository products) {
        this.products = products;
    }

    @Override
    public Optional<ProductInfo> find(long establishmentId, long productId) {
        return products.findById(establishmentId, productId)
                .map(product -> new ProductInfo(
                        product.id(), product.name(), product.baseUnit().label()));
    }
}
