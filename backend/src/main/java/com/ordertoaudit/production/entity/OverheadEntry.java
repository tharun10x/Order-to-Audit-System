package com.ordertoaudit.production.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.ordertoaudit.masters.entity.AppUser;

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

/** Labour, power or other cost charged to one run (R13.3). Monthly overheads (periodMonth) come in a later step. */
@Entity
@Table(name = "overhead_entry")
@Getter
@Setter
@NoArgsConstructor
public class OverheadEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private ProductionRun run;

    @Column(name = "period_month")
    private LocalDate periodMonth;

    @Enumerated(EnumType.STRING)
    private OverheadType type;

    private BigDecimal amount;

    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
