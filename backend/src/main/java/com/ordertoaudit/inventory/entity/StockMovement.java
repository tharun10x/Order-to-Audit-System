package com.ordertoaudit.inventory.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row of the stock ledger (R6.1). Append-only: @Immutable stops Hibernate from ever updating it.
 * qty and valueAmount are signed: positive = stock in, negative = stock out.
 * Only InventoryService creates these.
 */
@Entity
@Immutable
@Table(name = "stock_movement")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "movement_date")
    private Instant movementDate;

    private BigDecimal qty;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    /** The "value" column. Named valueAmount because VALUE is a keyword in HQL. */
    @Column(name = "value")
    private BigDecimal valueAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type")
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private SourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "batch_no")
    private String batchNo;

    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
