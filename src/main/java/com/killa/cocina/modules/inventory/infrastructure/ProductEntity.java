package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.BaseUnit;
import com.killa.cocina.modules.inventory.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Mapeo JPA de {@code products}. */
@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "products_seq")
    @SequenceGenerator(name = "products_seq", sequenceName = "products_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "category_id", nullable = false)
    private long categoryId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit", nullable = false, length = 10)
    private BaseUnit baseUnit;

    @Column(name = "min_stock", nullable = false, precision = 12, scale = 3)
    private BigDecimal minStock;

    @Column(name = "current_stock", nullable = false, precision = 12, scale = 3)
    private BigDecimal currentStock;

    @Column(name = "current_avg_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal currentAvgCost;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductEntity() {}

    private ProductEntity(Product product) {
        apply(product);
        this.createdAt = product.createdAt();
    }

    static ProductEntity from(Product product) {
        return new ProductEntity(product);
    }

    /** Copia el estado del dominio; no toca {@code createdAt}. */
    void apply(Product product) {
        this.id = product.id();
        this.establishmentId = product.establishmentId();
        this.categoryId = product.categoryId();
        this.name = product.name();
        this.baseUnit = product.baseUnit();
        this.minStock = product.minStock();
        this.currentStock = product.currentStock();
        this.currentAvgCost = product.currentAvgCost();
        this.allowNegative = product.allowNegative();
        this.active = product.active();
        this.updatedAt = product.updatedAt();
    }

    Product toDomain() {
        return new Product(
                id,
                establishmentId,
                categoryId,
                name,
                baseUnit,
                minStock,
                currentStock,
                currentAvgCost,
                allowNegative,
                active,
                createdAt,
                updatedAt);
    }

    Long getId() {
        return id;
    }
}
