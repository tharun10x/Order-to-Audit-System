package com.ordertoaudit.billing.entity;

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

/** One product on an invoice, with its own copy of the name, HSN, rate and tax (R10.5). qty is in packets. */
@Entity
@Table(name = "invoice_line")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    /** Used to move stock at dispatch. The printed name is itemName. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "hsn_code")
    private String hsnCode;

    private BigDecimal qty;

    private BigDecimal rate;

    @Column(name = "taxable_value")
    private BigDecimal taxableValue;

    @Column(name = "gst_rate")
    private BigDecimal gstRate;

    private BigDecimal cgst;

    private BigDecimal sgst;

    private BigDecimal igst;
}
