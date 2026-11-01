package com.killa.cocina.modules.recipes.infrastructure;

import com.killa.cocina.modules.recipes.domain.RecipeItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Mapeo JPA de {@code recipe_items}. */
@Entity
@Table(name = "recipe_items")
public class RecipeItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "recipe_items_seq")
    @SequenceGenerator(name = "recipe_items_seq", sequenceName = "recipe_items_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "version_id", nullable = false)
    private long versionId;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(name = "quantity_base", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityBase;

    @Column(name = "waste_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal wastePct;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected RecipeItemEntity() {}

    private RecipeItemEntity(RecipeItem item) {
        this.id = item.id();
        this.establishmentId = item.establishmentId();
        this.versionId = item.versionId();
        this.productId = item.productId();
        this.quantityBase = item.quantityBase();
        this.wastePct = item.wastePct();
        this.sortOrder = item.sortOrder();
    }

    static RecipeItemEntity from(RecipeItem item) {
        return new RecipeItemEntity(item);
    }

    RecipeItem toDomain() {
        return new RecipeItem(id, establishmentId, versionId, productId, quantityBase, wastePct, sortOrder);
    }
}
