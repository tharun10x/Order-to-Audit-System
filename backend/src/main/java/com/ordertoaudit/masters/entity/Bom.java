package com.ordertoaudit.masters.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A versioned recipe for one finished good: standard quantities per batch and the expected yield (R5.6, R5.8). */
@Entity
@Table(name = "bom")
@Getter
@Setter
@NoArgsConstructor
public class Bom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Item product;

    private int version;

    /** Expected packets per batch (200 for Murukku 200 g). */
    @Column(name = "output_qty")
    private BigDecimal outputQty;

    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "bom")
    @OrderBy("id")
    private List<BomLine> lines = new ArrayList<>();
}
