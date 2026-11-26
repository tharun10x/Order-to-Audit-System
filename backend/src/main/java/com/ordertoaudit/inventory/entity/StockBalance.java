package com.ordertoaudit.inventory.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import com.ordertoaudit.masters.entity.ItemKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read-only mapping of the stock_balance view (R6.5): quantity and value per item, summed from the ledger.
 * Nothing is ever saved through this class.
 */
@Entity
@Immutable
@Table(name = "stock_balance")
@Getter
@NoArgsConstructor
public class StockBalance {

    @Id
    @Column(name = "item_id")
    private Long itemId;

    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    private ItemKind kind;

    @Column(name = "qty_on_hand")
    private BigDecimal qtyOnHand;

    @Column(name = "stock_value")
    private BigDecimal stockValue;
}
