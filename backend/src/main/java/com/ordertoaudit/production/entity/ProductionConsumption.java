package com.ordertoaudit.production.entity;

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

/**
 * One component of a run: the standard (planned), what was actually issued, and how much of that was scrap.
 * actualQty INCLUDES scrap: 194 pouches = 188 used + 6 damaged, scrapQty = 6.
 */
@Entity
@Table(name = "production_consumption")
@Getter
@Setter
@NoArgsConstructor
public class ProductionConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private ProductionRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "planned_qty")
    private BigDecimal plannedQty;

    /** NULL until the actuals are entered. */
    @Column(name = "actual_qty")
    private BigDecimal actualQty;

    @Column(name = "scrap_qty")
    private BigDecimal scrapQty;

    /** Weighted average cost of the ISSUE movement, set when the run is posted. */
    @Column(name = "unit_cost")
    private BigDecimal unitCost;
}
