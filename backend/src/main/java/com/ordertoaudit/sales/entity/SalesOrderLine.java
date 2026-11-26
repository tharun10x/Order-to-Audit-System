package com.ordertoaudit.sales.entity;

import java.math.BigDecimal;

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

/** One product on an order. qty is in packets, rate is per packet before GST (R9.2). */
@Entity
@Table(name = "sales_order_line")
@Getter
@Setter
@NoArgsConstructor
public class SalesOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private SalesOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    private BigDecimal qty;

    private BigDecimal rate;

    /** Set at approval. May be lower than qty (partial approval, R9.5). */
    @Column(name = "approved_qty")
    private BigDecimal approvedQty;
}
