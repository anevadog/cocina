package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.quality.domain.WasteRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Mapeo JPA de {@code waste_records}. */
@Entity
@Table(name = "waste_records")
public class WasteRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "waste_records_seq")
    @SequenceGenerator(name = "waste_records_seq", sequenceName = "waste_records_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "quantity_base", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityBase;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal cost;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private long actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WasteRecordEntity() {}

    private WasteRecordEntity(WasteRecord record) {
        apply(record);
    }

    static WasteRecordEntity from(WasteRecord record) {
        return new WasteRecordEntity(record);
    }

    void apply(WasteRecord record) {
        this.id = record.id();
        this.establishmentId = record.establishmentId();
        this.productId = record.productId();
        this.lotId = record.lotId();
        this.quantityBase = record.quantityBase();
        this.unitCost = record.unitCost();
        this.cost = record.cost();
        this.reason = record.reason();
        this.actorId = record.actorId();
        this.occurredAt = record.occurredAt();
        this.createdAt = record.createdAt();
    }

    WasteRecord toDomain() {
        return new WasteRecord(
                id,
                establishmentId,
                productId,
                lotId,
                quantityBase,
                unitCost,
                cost,
                reason,
                actorId,
                occurredAt,
                createdAt);
    }
}
