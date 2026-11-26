package com.ordertoaudit.billing.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Customer;
import com.ordertoaudit.masters.entity.UserRole;
import com.ordertoaudit.sales.entity.SalesOrder;

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

/**
 * A GST tax invoice. Everything printed on it is copied in when it is raised (R10.5): the seller and buyer
 * fields below and the item name, HSN, rate and tax on each line. Only status ever changes afterwards.
 */
@Entity
@Table(name = "invoice")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "financial_year")
    private String financialYear;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private SalesOrder order;

    /** The link used for receivables and access checks. The printed details are the buyer* copies. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "seller_name")
    private String sellerName;

    @Column(name = "seller_gstin")
    private String sellerGstin;

    @Column(name = "seller_state_code")
    private String sellerStateCode;

    @Column(name = "seller_address")
    private String sellerAddress;

    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "buyer_gstin")
    private String buyerGstin;

    @Column(name = "buyer_address")
    private String buyerAddress;

    /** The buyer's state code at invoicing time. */
    @Column(name = "place_of_supply")
    private String placeOfSupply;

    @Enumerated(EnumType.STRING)
    @Column(name = "supply_type")
    private SupplyType supplyType;

    @Column(name = "taxable_value")
    private BigDecimal taxableValue;

    private BigDecimal cgst;

    private BigDecimal sgst;

    private BigDecimal igst;

    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<InvoiceLine> lines = new ArrayList<>();

    /** R2.2: a salesman may only see the invoices of customers assigned to them. */
    public boolean isVisibleTo(AppUser user) {
        return user.getRole() != UserRole.SALESMAN || customer.isAssignedTo(user);
    }

    public void addLine(InvoiceLine line) {
        line.setInvoice(this);
        lines.add(line);
    }
}
