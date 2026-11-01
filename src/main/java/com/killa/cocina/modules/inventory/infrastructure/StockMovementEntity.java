package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.MovementType;
import com.killa.cocina.modules.inventory.domain.StockMovement;
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

/**
 * Mapeo JPA de {@code stock_movements}.
 *
 * <p>No hay {@code apply} ni setters de negocio: la entidad solo se inserta. La inmutabilidad real la
 * garantiza el trigger de la migración V4.</p>
 */
@Entity
@Table(name = "stock_movements")
public class StockMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_movements_seq")
    @SequenceGenerator(name = "stock_movements_seq", sequenceName = "stock_movements_seq", allocationSize = 1)
    private Long id;

    @Column(name = "establishment_id", nullable = false)
    private long establishmentId;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(name = "lot_id")
    private Long lotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MovementType type;

    @Column(name = "quantity_base", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityBase;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 3)
    private BigDecimal balanceAfter;

    @Column(length = 200)
    private String reason;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "actor_id", nullable = false)
    private long actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockMovementEntity() {}

    static StockMovementEntity from(StockMovement movement, Instant now) {
        StockMovementEntity entity = new StockMovementEntity();
        entity.establishmentId = movement.establishmentId();
        entity.productId = movement.productId();
        entity.lotId = movement.lotId();
        entity.type = movement.type();
        entity.quantityBase = movement.quantityBase();
        entity.unitCost = movement.unitCost();
        entity.balanceAfter = movement.balanceAfter();
        entity.reason = movement.reason();
        entity.referenceType = movement.referenceType();
        entity.referenceId = movement.referenceId();
        entity.actorId = movement.actorId();
        entity.occurredAt = movement.occurredAt();
        entity.createdAt = now;
        return entity;
    }

    StockMovement toDomain() {
        return new StockMovement(
                id,
                establishmentId,
                productId,
                lotId,
                type,
                quantityBase,
                unitCost,
                balanceAfter,
                reason,
                referenceType,
                referenceId,
                actorId,
                occurredAt);
    }
}
