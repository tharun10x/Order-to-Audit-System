package com.ordertoaudit.billing.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.audit.entity.AuditAction;
import com.ordertoaudit.audit.service.AuditService;
import com.ordertoaudit.billing.entity.Dispatch;
import com.ordertoaudit.billing.entity.DispatchLine;
import com.ordertoaudit.billing.entity.Invoice;
import com.ordertoaudit.billing.entity.InvoiceLine;
import com.ordertoaudit.billing.entity.InvoiceStatus;
import com.ordertoaudit.billing.repository.DispatchRepository;
import com.ordertoaudit.billing.repository.InvoiceRepository;
import com.ordertoaudit.billing.web.DispatchRequest;
import com.ordertoaudit.billing.web.DispatchResponse;
import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.common.numbering.DocType;
import com.ordertoaudit.common.numbering.DocumentNumberService;
import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;
import com.ordertoaudit.inventory.service.InventoryService;
import com.ordertoaudit.inventory.service.MovementRequest;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.sales.entity.OrderStatus;
import com.ordertoaudit.sales.entity.SalesOrder;
import com.ordertoaudit.sales.repository.SalesOrderRepository;

/** Dispatch: the only sales step that reduces finished-goods stock (R11). */
@Service
public class DispatchService {

    private final DispatchRepository dispatchRepository;
    private final InvoiceRepository invoiceRepository;
    private final SalesOrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;

    public DispatchService(DispatchRepository dispatchRepository, InvoiceRepository invoiceRepository,
                           SalesOrderRepository orderRepository, InventoryService inventoryService,
                           DocumentNumberService documentNumberService, AuditService auditService) {
        this.dispatchRepository = dispatchRepository;
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
    }

    /**
     * The order lock means a second dispatch waits and then finds the order DISPATCHED (409). InventoryService
     * locks the items and re-checks the stock (R11.3). If it is short, nothing at all is written.
     */
    @Transactional
    public DispatchResponse dispatch(DispatchRequest request, AppUser user) {
        Long invoiceId = request.invoiceId();
        Long orderId = invoiceRepository.findOrderId(invoiceId).orElseThrow(() -> notFound(invoiceId));
        SalesOrder order = orderRepository.lockById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " is missing"));
        Invoice invoice = invoiceRepository.lockById(invoiceId).orElseThrow(() -> notFound(invoiceId));

        if (invoice.getStatus() != InvoiceStatus.POSTED || order.getStatus() != OrderStatus.INVOICED) {
            throw new ConflictException("Invoice " + invoice.getInvoiceNo() + " cannot be dispatched: invoice is "
                    + invoice.getStatus() + " and order " + order.getOrderNo() + " is " + order.getStatus());
        }
        LocalDate date = request.dispatchDate();
        if (date.isBefore(invoice.getInvoiceDate())) {
            throw new BadRequestException("The dispatch date " + date + " is before the invoice date "
                    + invoice.getInvoiceDate());
        }

        Dispatch dispatch = new Dispatch();
        dispatch.setDispatchNo(documentNumberService.next(DocType.DISPATCH, date));   // R11.5
        dispatch.setInvoice(invoice);
        dispatch.setDispatchDate(date);
        dispatch.setVehicleNo(request.vehicleNo());
        dispatch.setCreatedBy(user);
        for (InvoiceLine invoiceLine : invoice.getLines()) {
            DispatchLine line = new DispatchLine();
            line.setItem(invoiceLine.getItem());
            line.setQty(invoiceLine.getQty());
            dispatch.addLine(line);
        }
        dispatchRepository.save(dispatch);

        Instant movementDate = date.atStartOfDay(Money.ZONE).toInstant();
        List<MovementRequest> sales = new ArrayList<>();
        for (DispatchLine line : dispatch.getLines()) {
            sales.add(MovementRequest.outbound(line.getItem().getId(), MovementType.SALE, line.getQty(),
                    SourceType.DISPATCH, dispatch.getId(), movementDate, user));
        }
        inventoryService.post(sales);
        order.setStatus(OrderStatus.DISPATCHED);

        DispatchResponse response = toResponse(dispatch, order);
        auditService.log("DISPATCH", dispatch.getId(), AuditAction.DISPATCH, null, snapshot(response), user);
        auditService.log("SALES_ORDER", order.getId(), AuditAction.UPDATE,
                Map.of("orderNo", order.getOrderNo(), "status", OrderStatus.INVOICED.name()),
                Map.of("orderNo", order.getOrderNo(), "status", order.getStatus().name()), user);
        return response;
    }

    private NotFoundException notFound(Long invoiceId) {
        return new NotFoundException("Invoice " + invoiceId + " not found");
    }

    private DispatchResponse toResponse(Dispatch dispatch, SalesOrder order) {
        List<DispatchResponse.Line> lines = dispatch.getLines().stream()
                .map(line -> new DispatchResponse.Line(line.getItem().getCode(), line.getQty()))
                .toList();
        return new DispatchResponse(dispatch.getId(), dispatch.getDispatchNo(), dispatch.getInvoice().getId(),
                dispatch.getInvoice().getInvoiceNo(), order.getId(), dispatch.getDispatchDate(),
                dispatch.getVehicleNo(), lines);
    }

    private Map<String, Object> snapshot(DispatchResponse dispatch) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dispatchNo", dispatch.dispatchNo());
        data.put("invoiceNo", dispatch.invoiceNo());
        data.put("dispatchDate", dispatch.dispatchDate().toString());
        data.put("vehicleNo", dispatch.vehicleNo());
        data.put("lines", dispatch.lines().stream()
                .map(line -> Map.<String, Object>of("itemCode", line.itemCode(), "qty", line.qty()))
                .toList());
        return data;
    }
}
