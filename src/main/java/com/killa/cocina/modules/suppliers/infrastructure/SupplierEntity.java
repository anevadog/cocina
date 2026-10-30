package com.killa.cocina.modules.suppliers.infrastructure;

import com.killa.cocina.modules.suppliers.domain.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** Mapeo JPA de {@code suppliers}. */
@Entity
@Table(name = "suppliers")
public class SupplierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "suppliers_seq")
    @SequenceGenerator(name = "suppliers_seq", sequenceName = "suppliers_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 200)
    private String contact;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupplierEntity() {
    }

    private SupplierEntity(Supplier supplier) {
        apply(supplier);
        this.createdAt = supplier.createdAt();
    }

    static SupplierEntity from(Supplier supplier) {
        return new SupplierEntity(supplier);
    }

    void apply(Supplier supplier) {
        this.id = supplier.id();
        this.establishmentId = supplier.establishmentId();
        this.name = supplier.name();
        this.contact = supplier.contact();
        this.active = supplier.active();
        this.updatedAt = supplier.updatedAt();
    }

    Supplier toDomain() {
        return new Supplier(id, establishmentId, name, contact, active, createdAt, updatedAt);
    }
}
