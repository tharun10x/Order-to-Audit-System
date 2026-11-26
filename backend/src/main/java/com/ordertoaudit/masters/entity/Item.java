package com.ordertoaudit.masters.entity;

import java.math.BigDecimal;
import java.time.Instant;

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

/** Anything that is stocked: raw material, packaging or finished good. One ledger for all of them. */
@Entity
@Table(name = "item")
@Getter
@Setter
@NoArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    private ItemKind kind;

    /** LAZY, so locking an item (FOR UPDATE) locks only the item row and not its unit row. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_uom_id")
    private Uom baseUom;

    @Column(name = "hsn_code")
    private String hsnCode;

    @Column(name = "gst_rate")
    private BigDecimal gstRate;

    @Column(name = "selling_price")
    private BigDecimal sellingPrice;

    @Column(name = "pack_weight_kg")
    private BigDecimal packWeightKg;

    @Column(name = "reorder_level")
    private BigDecimal reorderLevel;

    private boolean active;

    /** Filled by the database default. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
