package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.Product;
import com.killa.cocina.modules.inventory.domain.ProductFilter;
import com.killa.cocina.modules.inventory.domain.ProductRepository;
import com.killa.cocina.modules.inventory.domain.UnitConversion;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link ProductRepository}. */
@Repository
class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository products;

    private final ProductUnitConversionJpaRepository conversions;

    ProductRepositoryAdapter(ProductJpaRepository products,
            ProductUnitConversionJpaRepository conversions) {
        this.products = products;
        this.conversions = conversions;
    }

    @Override
    public Optional<Product> findById(long establishmentId, long productId) {
        return products.findByIdAndEstablishmentId(productId, establishmentId)
                .map(ProductEntity::toDomain);
    }

    @Override
    public boolean existsByName(long establishmentId, String name) {
        return products.existsByEstablishmentIdAndName(establishmentId, name);
    }

    @Override
    public boolean existsByNameExcluding(long establishmentId, String name, long excludedProductId) {
        return products.existsByEstablishmentIdAndNameAndIdNot(establishmentId, name,
                excludedProductId);
    }

    @Override
    public Optional<Product> findForUpdate(long establishmentId, long productId) {
        return products.findForUpdate(productId, establishmentId).map(ProductEntity::toDomain);
    }

    @Override
    public List<Product> lowStock(long establishmentId, int limit) {
        return products.findLowStock(establishmentId, PageRequest.of(0, limit)).stream()
                .map(ProductEntity::toDomain)
                .toList();
    }

    @Override
    public long countLowStock(long establishmentId) {
        return products.countLowStock(establishmentId);
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = product.id() == null
                ? ProductEntity.from(product)
                : products.findById(product.id()).orElseGet(() -> ProductEntity.from(product));
        if (product.id() != null) {
            entity.apply(product);
        }
        return products.save(entity).toDomain();
    }

    @Override
    public List<Product> search(long establishmentId, ProductFilter filter, int page, int size) {
        return products.findAll(specification(establishmentId, filter),
                        PageRequest.of(page, size, Sort.by("name").ascending()))
                .stream()
                .map(ProductEntity::toDomain)
                .toList();
    }

    @Override
    public long count(long establishmentId, ProductFilter filter) {
        return products.count(specification(establishmentId, filter));
    }

    @Override
    public List<UnitConversion> conversions(long productId) {
        return conversions.findByProductIdOrderByUnitAsc(productId).stream()
                .map(ProductUnitConversionEntity::toDomain)
                .toList();
    }

    @Override
    public void replaceConversions(long establishmentId, long productId,
            List<UnitConversion> newConversions, Instant now) {
        products.deleteConversions(productId);
        if (!newConversions.isEmpty()) {
            conversions.saveAll(newConversions.stream()
                    .map(conversion -> ProductUnitConversionEntity.from(establishmentId, productId,
                            conversion, now))
                    .toList());
        }
    }

    private static Specification<ProductEntity> specification(long establishmentId,
            ProductFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("establishmentId"), establishmentId));
            if (filter != null && filter.active() != null) {
                predicates.add(builder.equal(root.get("active"), filter.active()));
            }
            if (filter != null && filter.categoryId() != null) {
                predicates.add(builder.equal(root.get("categoryId"), filter.categoryId()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
