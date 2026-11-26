package com.ordertoaudit.masters.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A unit of measure: kg, g, L, ml, pc, pkt, ctn. */
@Entity
@Table(name = "uom")
@Getter
@Setter
@NoArgsConstructor
public class Uom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    private UomDimension dimension;

    /** Factor to the dimension's base unit (kg, L, pc). NULL when it depends on the item (carton). */
    @Column(name = "factor_to_base")
    private BigDecimal factorToBase;
}
