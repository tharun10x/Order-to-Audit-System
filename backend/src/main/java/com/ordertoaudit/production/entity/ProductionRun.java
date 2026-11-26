package com.ordertoaudit.production.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Bom;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One production run of one product. Its standard quantities are copied from the BOM, never read from it later (R8.5). */
@Entity
@Table(name = "production_run")
@Getter
@Setter
@NoArgsConstructor
public class ProductionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_no")
    private String runNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_id")
    private Bom bom;

    @Column(name = "run_date")
    private LocalDate runDate;

    private BigDecimal batches;

    /** BOM output_qty x batches: the expected packets. */
    @Column(name = "planned_output")
    private BigDecimal plannedOutput;

    /** Good packets. Set when the actuals are entered. */
    @Column(name = "actual_output")
    private BigDecimal actualOutput;

    @Enumerated(EnumType.STRING)
    private RunStatus status;

    @Column(name = "posted_at")
    private Instant postedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by")
    private AppUser postedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_by")
    private AppUser reversedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<ProductionConsumption> consumptions = new ArrayList<>();

    public void addConsumption(ProductionConsumption consumption) {
        consumption.setRun(this);
        consumptions.add(consumption);
    }
}
