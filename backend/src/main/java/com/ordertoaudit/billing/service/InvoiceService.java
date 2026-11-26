package com.ordertoaudit.billing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.audit.entity.AuditAction;
import com.ordertoaudit.audit.service.AuditService;
import com.ordertoaudit.billing.entity.CreditNote;
import com.ordertoaudit.billing.entity.Dispatch;
import com.ordertoaudit.billing.entity.Invoice;
import com.ordertoaudit.billing.entity.InvoiceLine;
import com.ordertoaudit.billing.entity.InvoiceStatus;
import com.ordertoaudit.billing.repository.CreditNoteRepository;
import com.ordertoaudit.billing.repository.DispatchRepository;
import com.ordertoaudit.billing.repository.InvoiceRepository;
import com.ordertoaudit.billing.web.InvoiceResponse;
import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.common.numbering.DocType;
import com.ordertoaudit.common.numbering.DocumentNumberService;
import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;
import com.ordertoaudit.inventory.entity.StockMovement;
import com.ordertoaudit.inventory.repository.StockMovementRepository;
import com.ordertoaudit.inventory.service.InventoryService;
import com.ordertoaudit.inventory.service.MovementRequest;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Company;
import com.ordertoaudit.masters.entity.Customer;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.UserRole;
import com.ordertoaudit.masters.repository.CompanyRepository;
import com.ordertoaudit.payments.entity.InvoicePaymentStatus;
import com.ordertoaudit.payments.repository.InvoiceOutstandingRepository;
import com.ordertoaudit.payments.repository.PaidSnapshot;
import com.ordertoaudit.payments.repository.PaymentRepository;
import com.ordertoaudit.sales.entity.OrderStatus;
import com.ordertoaudit.sales.entity.SalesOrder;
import com.ordertoaudit.sales.entity.SalesOrderLine;
import com.ordertoaudit.sales.repository.SalesOrderRepository;

/**
 * GST invoices (R10). Raising an invoice never touches stock: only dispatch does (R11.2).
 * A posted invoice is never edited or deleted. It is cancelled with a credit note (R10.6).
 */
@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final DispatchRepository dispatchRepository;
    private final SalesOrderRepository orderRepository;
    private final CompanyRepository companyRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceOutstandingRepository outstandingRepository;
    private final StockMovementRepository movementRepository;
    private final InventoryService inventoryService;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;
    private final Clock businessClock;

    public InvoiceService(InvoiceRepository invoiceRepository, CreditNoteRepository creditNoteRepository,
                          DispatchRepository dispatchRepository, SalesOrderRepository orderRepository,
                          CompanyRepository companyRepository, PaymentRepository paymentRepository,
                          InvoiceOutstandingRepository outstandingRepository,
                          StockMovementRepository movementRepository, InventoryService inventoryService,
                          DocumentNumberService documentNumberService, AuditService auditService,
                          Clock businessClock) {
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.dispatchRepository = dispatchRepository;
        this.orderRepository = orderRepository;
        this.companyRepository = companyRepository;
        this.paymentRepository = paymentRepository;
        this.outstandingRepository = outstandingRepository;
        this.movementRepository = movementRepository;
        this.inventoryService = inventoryService;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
        this.businessClock = businessClock;
    }

    /** R10.1 to R10.5: one invoice for the whole APPROVED order, for the approved quantities. */
    @Transactional
    public InvoiceResponse createFromOrder(Long orderId, LocalDate invoiceDate, AppUser user) {
        SalesOrder order = orderRepository.lockById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
        if (order.getStatus() != OrderStatus.APPROVED) {
            throw new ConflictException("Order " + order.getOrderNo() + " is " + order.getStatus()
                    + " and cannot be invoiced (only APPROVED orders can)");
        }
        Map<String, Object> orderBefore = statusData(order.getOrderNo(), order.getStatus());

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setFinancialYear(DocumentNumberService.financialYear(invoiceDate));
        invoice.setStatus(InvoiceStatus.POSTED);
        invoice.setCreatedBy(user);
        copyParties(invoice, currentCompany(), order.getCustomer());

        List<GstCalculator.Tax> lineTaxes = new ArrayList<>();
        for (SalesOrderLine orderLine : order.getLines()) {
            lineTaxes.add(addLine(invoice, orderLine));
        }
        GstCalculator.Tax totals = GstCalculator.sum(lineTaxes);
        invoice.setTaxableValue(totals.taxable());
        invoice.setCgst(totals.cgst());
        invoice.setSgst(totals.sgst());
        invoice.setIgst(totals.igst());
        invoice.setTotal(totals.total());

        // Taken last, so a rejected invoice never even locks the counter (R10.2)
        invoice.setInvoiceNo(documentNumberService.next(DocType.INVOICE, invoiceDate));
        invoiceRepository.save(invoice);
        order.setStatus(OrderStatus.INVOICED);

        auditService.log("INVOICE", invoice.getId(), AuditAction.POST, null, snapshot(invoice), user);
        auditService.log("SALES_ORDER", order.getId(), AuditAction.UPDATE, orderBefore,
                statusData(order.getOrderNo(), order.getStatus()), user);
        return toResponse(invoice);
    }

    /**
     * R10.6, R10.7: credit note, goods back into stock if they had left, invoice and order CANCELLED.
     * All in one transaction. noteDate may be null, which means today.
     */
    @Transactional
    public InvoiceResponse cancel(Long invoiceId, String reason, LocalDate noteDate, AppUser user) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Cancelling an invoice needs a reason");
        }
        // Order first, then invoice: the same lock order as dispatch
        Long orderId = invoiceRepository.findOrderId(invoiceId).orElseThrow(() -> notFound(invoiceId));
        SalesOrder order = orderRepository.lockById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " is missing"));
        Invoice invoice = invoiceRepository.lockById(invoiceId).orElseThrow(() -> notFound(invoiceId));

        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new ConflictException("Invoice " + invoice.getInvoiceNo() + " is already " + invoice.getStatus());
        }
        if (paymentRepository.hasPostedPaymentFor(invoiceId)) {
            throw new ConflictException("Invoice " + invoice.getInvoiceNo()
                    + " has payments. They must be reversed before it can be cancelled");
        }
        Dispatch dispatch = dispatchRepository.findByInvoiceId(invoiceId).orElse(null);
        LocalDate date = noteDate != null ? noteDate : LocalDate.now(businessClock);
        requireNotBefore(date, invoice.getInvoiceDate(), "the invoice date");
        if (dispatch != null) {
            requireNotBefore(date, dispatch.getDispatchDate(), "the dispatch date");
        }

        Map<String, Object> invoiceBefore = snapshot(invoice);
        Map<String, Object> orderBefore = statusData(order.getOrderNo(), order.getStatus());

        CreditNote note = creditNoteFor(invoice, date, reason.strip(), user);
        creditNoteRepository.save(note);
        if (dispatch != null) {
            returnGoods(dispatch, note, user);
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);

        auditService.log("INVOICE", invoice.getId(), AuditAction.CANCEL, invoiceBefore, snapshot(invoice), user);
        auditService.log("CREDIT_NOTE", note.getId(), AuditAction.CREATE, null, snapshot(note), user);
        auditService.log("SALES_ORDER", order.getId(), AuditAction.CANCEL, orderBefore,
                statusData(order.getOrderNo(), order.getStatus()), user);
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long invoiceId, AppUser user) {
        return toResponse(invoiceRepository.findById(invoiceId)
                .filter(invoice -> invoice.isVisibleTo(user))
                .orElseThrow(() -> notFound(invoiceId)));
    }

    /** R2.2: a salesman sees the invoices of their own customers. */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(AppUser user) {
        List<Invoice> invoices = user.getRole() == UserRole.SALESMAN
                ? invoiceRepository.findByCustomerSalesmanIdOrderByIdDesc(user.getId())
                : invoiceRepository.findAllByOrderByIdDesc();
        return invoices.stream().map(this::toResponse).toList();
    }

    /** R10.5: copy what is printed, so editing a master record later never changes this invoice. */
    private void copyParties(Invoice invoice, Company company, Customer customer) {
        invoice.setSellerName(company.getName());
        invoice.setSellerGstin(company.getGstin());
        invoice.setSellerStateCode(company.getStateCode());
        invoice.setSellerAddress(company.getAddress());

        invoice.setCustomer(customer);
        invoice.setBuyerName(customer.getName());
        invoice.setBuyerGstin(customer.getGstin());
        invoice.setBuyerAddress(customer.getAddress());
        invoice.setPlaceOfSupply(customer.getStateCode());
        invoice.setSupplyType(GstCalculator.supplyType(company.getStateCode(), customer.getStateCode()));
    }

    private GstCalculator.Tax addLine(Invoice invoice, SalesOrderLine orderLine) {
        Item item = orderLine.getItem();
        if (item.getHsnCode() == null || item.getGstRate() == null) {
            throw new BadRequestException("Product " + item.getCode() + " needs an HSN code and a GST rate");
        }
        GstCalculator.Tax tax = GstCalculator.line(orderLine.getApprovedQty(), orderLine.getRate(),
                item.getGstRate(), invoice.getSupplyType());

        InvoiceLine line = new InvoiceLine();
        line.setItem(item);
        line.setItemName(item.getName());
        line.setHsnCode(item.getHsnCode());
        line.setQty(orderLine.getApprovedQty());
        line.setRate(orderLine.getRate());
        line.setGstRate(item.getGstRate());
        line.setTaxableValue(tax.taxable());
        line.setCgst(tax.cgst());
        line.setSgst(tax.sgst());
        line.setIgst(tax.igst());
        invoice.addLine(line);
        return tax;
    }

    private Company currentCompany() {
        return companyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("The company row is missing"));
    }

    private CreditNote creditNoteFor(Invoice invoice, LocalDate date, String reason, AppUser user) {
        CreditNote note = new CreditNote();
        note.setNoteNo(documentNumberService.next(DocType.CREDIT_NOTE, date));
        note.setInvoice(invoice);
        note.setNoteDate(date);
        note.setReason(reason);
        note.setSupplyType(invoice.getSupplyType());
        note.setTaxableValue(invoice.getTaxableValue());
        note.setCgst(invoice.getCgst());
        note.setSgst(invoice.getSgst());
        note.setIgst(invoice.getIgst());
        note.setTotal(invoice.getTotal());
        note.setCreatedBy(user);
        return note;
    }

    /**
     * R10.7: one RETURN per SALE movement of the dispatch, for the same quantity and exactly the same value.
     * Returns are always full, so the dispatch and its return add up to zero, to the paisa. (Recomputing
     * qty x the 4-decimal unit cost could differ by a few paise.)
     */
    private void returnGoods(Dispatch dispatch, CreditNote note, AppUser user) {
        List<StockMovement> sales = movementRepository.findBySourceTypeAndSourceIdAndMovementTypeOrderByIdAsc(
                SourceType.DISPATCH, dispatch.getId(), MovementType.SALE);
        List<MovementRequest> returns = sales.stream()
                .map(sale -> MovementRequest.inbound(sale.getItem().getId(), MovementType.RETURN,
                                sale.getQty().negate(), sale.getValueAmount().negate(),
                                SourceType.CREDIT_NOTE, note.getId(),
                                note.getNoteDate().atStartOfDay(Money.ZONE).toInstant(), user)
                        .withRemarks("Returned: " + note.getNoteNo()))
                .toList();
        inventoryService.post(returns);
    }

    private void requireNotBefore(LocalDate date, LocalDate earliest, String what) {
        if (date.isBefore(earliest)) {
            throw new BadRequestException("The credit note date " + date + " is before " + what + " " + earliest);
        }
    }

    private NotFoundException notFound(Long invoiceId) {
        return new NotFoundException("Invoice " + invoiceId + " not found");
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        BigDecimal paid = null;
        BigDecimal outstanding = null;
        InvoicePaymentStatus paymentStatus = null;
        // The status is checked here because the view cannot see a cancellation that is not flushed yet
        if (invoice.getStatus() == InvoiceStatus.POSTED) {
            PaidSnapshot snapshot = outstandingRepository.snapshot(invoice.getId())
                    .orElseThrow(() -> new IllegalStateException("No invoice_outstanding row for " + invoice.getInvoiceNo()));
            paid = Money.money(snapshot.getPaid());
            outstanding = Money.money(snapshot.getOutstanding());
            paymentStatus = InvoicePaymentStatus.of(paid, outstanding);
        }
        List<InvoiceResponse.Line> lines = invoice.getLines().stream()
                .map(line -> new InvoiceResponse.Line(line.getItem().getCode(), line.getItemName(), line.getHsnCode(),
                        line.getQty(), line.getRate(), line.getTaxableValue(), line.getGstRate(),
                        line.getCgst(), line.getSgst(), line.getIgst()))
                .toList();
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNo(), invoice.getInvoiceDate(),
                invoice.getOrder().getId(), invoice.getOrder().getOrderNo(), invoice.getCustomer().getId(),
                new InvoiceResponse.Party(invoice.getSellerName(), invoice.getSellerGstin(),
                        invoice.getSellerStateCode(), invoice.getSellerAddress()),
                new InvoiceResponse.Party(invoice.getBuyerName(), invoice.getBuyerGstin(),
                        invoice.getPlaceOfSupply(), invoice.getBuyerAddress()),
                invoice.getPlaceOfSupply(), invoice.getSupplyType(), invoice.getTaxableValue(),
                invoice.getCgst(), invoice.getSgst(), invoice.getIgst(), invoice.getTotal(), invoice.getStatus(),
                creditNoteRepository.findByInvoiceId(invoice.getId()).map(CreditNote::getNoteNo).orElse(null),
                dispatchRepository.findByInvoiceId(invoice.getId()).map(Dispatch::getDispatchNo).orElse(null),
                paid, outstanding, paymentStatus, lines);
    }

    private Map<String, Object> statusData(String orderNo, OrderStatus status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("status", status.name());
        return data;
    }

    private Map<String, Object> snapshot(Invoice invoice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceNo", invoice.getInvoiceNo());
        data.put("status", invoice.getStatus().name());
        data.put("invoiceDate", invoice.getInvoiceDate().toString());
        data.put("buyerName", invoice.getBuyerName());
        data.put("supplyType", invoice.getSupplyType().name());
        data.put("taxableValue", invoice.getTaxableValue());
        data.put("cgst", invoice.getCgst());
        data.put("sgst", invoice.getSgst());
        data.put("igst", invoice.getIgst());
        data.put("total", invoice.getTotal());
        return data;
    }

    private Map<String, Object> snapshot(CreditNote note) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noteNo", note.getNoteNo());
        data.put("invoiceNo", note.getInvoice().getInvoiceNo());
        data.put("noteDate", note.getNoteDate().toString());
        data.put("reason", note.getReason());
        data.put("total", note.getTotal());
        return data;
    }
}
