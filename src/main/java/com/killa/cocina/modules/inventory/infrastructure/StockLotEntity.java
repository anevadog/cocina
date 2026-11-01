package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.StockLot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Mapeo JPA de {@code stock_lots}. */
@Entity
@Table(name = "stock_lots")
public class StockLotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_lots_seq")
    @SequenceGenerator(name = "stock_lots_seq", sequenceName = "stock_lots_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "initial_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal initialQty;

    @Column(name = "remaining_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal remainingQty;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockLotEntity() {}

    private StockLotEntity(StockLot lot) {
        apply(lot);
    }

    static StockLotEntity from(StockLot lot) {
        return new StockLotEntity(lot);
    }

    void apply(StockLot lot) {
        this.id = lot.id();
        this.establishmentId = lot.establishmentId();
        this.productId = lot.productId();
        this.supplierId = lot.supplierId();
        this.receivedAt = lot.receivedAt();
        this.expiresAt = lot.expiresAt();
        this.unitCost = lot.unitCost();
        this.initialQty = lot.initialQty();
        this.remainingQty = lot.remainingQty();
        this.createdAt = lot.createdAt();
    }

    StockLot toDomain() {
        return new StockLot(
                id,
                establishmentId,
                productId,
                supplierId,
                receivedAt,
                expiresAt,
                unitCost,
                initialQty,
                remainingQty,
                createdAt);
    }
}
