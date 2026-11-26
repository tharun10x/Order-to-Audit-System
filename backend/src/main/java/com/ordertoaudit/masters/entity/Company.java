package com.ordertoaudit.masters.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The factory itself. One row. */
@Entity
@Table(name = "company")
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String gstin;

    /** '33' = Tamil Nadu. Decides CGST+SGST (same state) or IGST (other state). */
    @Column(name = "state_code")
    private String stateCode;

    private String address;

    /** R8.9: variances beyond +/- this percentage are flagged. */
    @Column(name = "variance_tolerance_pct")
    private BigDecimal varianceTolerancePct;
}
