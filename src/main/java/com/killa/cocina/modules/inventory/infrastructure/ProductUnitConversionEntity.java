package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.UnitConversion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Mapeo JPA de {@code product_unit_conversions}. */
@Entity
@Table(name = "product_unit_conversions")
public class ProductUnitConversionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_unit_conversions_seq")
    @SequenceGenerator(
            name = "product_unit_conversions_seq",
            sequenceName = "product_unit_conversions_seq",
            allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "factor_to_base", nullable = false, precision = 18, scale = 6)
    private BigDecimal factorToBase;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductUnitConversionEntity() {}

    static ProductUnitConversionEntity from(
            long establishmentId, long productId, UnitConversion conversion, Instant now) {
        ProductUnitConversionEntity entity = new ProductUnitConversionEntity();
        entity.establishmentId = establishmentId;
        entity.productId = productId;
        entity.unit = conversion.unit();
        entity.factorToBase = conversion.factorToBase();
        entity.createdAt = now;
        return entity;
    }

    UnitConversion toDomain() {
        return new UnitConversion(unit, factorToBase);
    }
}
