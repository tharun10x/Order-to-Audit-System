package com.ordertoaudit.purchase.entity;

import java.math.BigDecimal;

import com.ordertoaudit.masters.entity.Item;

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

/**
 * One item on a bill. qty is in the item's BASE unit and rate is per base unit.
 * amount is what the supplier bill says (entered qty x entered rate, rounded to 2), so the stock value
 * created by the RECEIPT always equals the bill amount.
 */
@Entity
@Table(name = "purchase_line")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    private BigDecimal qty;

    private BigDecimal rate;

    private BigDecimal amount;
}
