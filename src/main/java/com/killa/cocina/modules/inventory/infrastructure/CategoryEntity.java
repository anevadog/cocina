package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** Mapeo JPA de {@code product_categories}. */
@Entity
@Table(name = "product_categories")
public class CategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_categories_seq")
    @SequenceGenerator(name = "product_categories_seq", sequenceName = "product_categories_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CategoryEntity() {}

    private CategoryEntity(Category category) {
        apply(category);
        this.createdAt = category.createdAt();
    }

    static CategoryEntity from(Category category) {
        return new CategoryEntity(category);
    }

    void apply(Category category) {
        this.id = category.id();
        this.establishmentId = category.establishmentId();
        this.name = category.name();
        this.updatedAt = category.updatedAt();
    }

    Category toDomain() {
        return new Category(id, establishmentId, name, createdAt, updatedAt);
    }
}
