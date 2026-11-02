package com.killa.cocina.modules.recipes.infrastructure;

import com.killa.cocina.modules.inventory.domain.Product;
import com.killa.cocina.modules.inventory.domain.ProductRepository;
import com.killa.cocina.modules.inventory.domain.Quantities;
import com.killa.cocina.modules.recipes.domain.IngredientCatalog;
import com.killa.cocina.modules.recipes.domain.IngredientProduct;
import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Catálogo de ingredientes sobre la API de aplicación de inventario.
 *
 * <p>Recetas no conoce las entidades JPA ni el modelo de dominio de inventario (`docs/arquitectura.md` §2.1): usa su
 * puerto publicado y reutiliza la misma aritmética de unidades, para que una receta no invente
 * conversiones distintas de las de una compra.</p>
 */
@Component
class InventoryIngredientCatalog implements IngredientCatalog {

    private final ProductRepository products;

    InventoryIngredientCatalog(ProductRepository products) {
        this.products = products;
    }

    @Override
    public Optional<IngredientProduct> find(long establishmentId, long productId) {
        return products.findById(establishmentId, productId).map(InventoryIngredientCatalog::toIngredient);
    }

    @Override
    public List<IngredientProduct> findForUpdate(long establishmentId, Collection<Long> productIds) {
        return productIds.stream()
                .distinct()
                .sorted()
                .map(productId -> products.findForUpdate(establishmentId, productId))
                .flatMap(Optional::stream)
                .map(InventoryIngredientCatalog::toIngredient)
                .toList();
    }

    @Override
    public BigDecimal toBase(long establishmentId, long productId, BigDecimal quantity, String unit) {
        Product product = products.findById(establishmentId, productId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.VALIDATION_ERROR,
                        "La petición contiene datos no válidos.",
                        List.of(new ApiError.FieldViolation("productId", "el producto " + productId + " no existe"))));
        return Quantities.toBase(product, products.conversions(productId), quantity, unit);
    }

    private static IngredientProduct toIngredient(Product product) {
        return new IngredientProduct(
                product.id(), product.name(), product.baseUnit().label(), product.currentAvgCost(), product.active());
    }
}
