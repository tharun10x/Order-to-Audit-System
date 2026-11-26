package com.ordertoaudit.inventory.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** A manual stock-count correction with a mandatory reason (R6.9). The posting service comes in a later step. */
@Entity
@Table(name = "stock_adjustment")
@Getter
@Setter
@NoArgsConstructor
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "adjustment_date")
    private LocalDate adjustmentDate;

    /** Signed: positive = found extra, negative = lost or damaged. */
    private BigDecimal qty;

    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
