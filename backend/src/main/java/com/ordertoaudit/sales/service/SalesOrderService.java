package com.ordertoaudit.sales.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.audit.entity.AuditAction;
import com.ordertoaudit.audit.service.AuditService;
import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.ForbiddenException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.common.error.OrderShortageException;
import com.ordertoaudit.common.numbering.DocType;
import com.ordertoaudit.common.numbering.DocumentNumberService;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Customer;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemKind;
import com.ordertoaudit.masters.entity.UserRole;
import com.ordertoaudit.masters.repository.CustomerRepository;
import com.ordertoaudit.masters.repository.ItemRepository;
import com.ordertoaudit.masters.service.UnitConversionService;
import com.ordertoaudit.sales.entity.OrderStatus;
import com.ordertoaudit.sales.entity.SalesOrder;
import com.ordertoaudit.sales.entity.SalesOrderLine;
import com.ordertoaudit.sales.repository.SalesOrderRepository;
import com.ordertoaudit.sales.web.ApproveOrderRequest;
import com.ordertoaudit.sales.web.CreateOrderRequest;
import com.ordertoaudit.sales.web.OrderResponse;
import com.ordertoaudit.sales.web.StockCheckResponse;
import com.ordertoaudit.sales.web.UpdateOrderLinesRequest;

/**
 * Sales orders (R9). Every method that changes an order locks the order row first, so two requests can never
 * move the same order twice. Approval then locks the finished-goods items (R6.7). Nothing here moves stock (R9.6).
 * Role checks (who may approve) arrive with JWT in step 6; the salesman ownership rule (R2.2) is enforced here.
 */
@Service
public class SalesOrderService {

    private static final String ENTITY = "SALES_ORDER";

    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final UnitConversionService unitConversionService;
    private final DocumentNumberService documentNumberService;
    private final StockCheckService stockCheckService;
    private final AuditService auditService;
    private final Clock businessClock;

    public SalesOrderService(SalesOrderRepository orderRepository, CustomerRepository customerRepository,
                             ItemRepository itemRepository, UnitConversionService unitConversionService,
                             DocumentNumberService documentNumberService, StockCheckService stockCheckService,
                             AuditService auditService, Clock businessClock) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.itemRepository = itemRepository;
        this.unitConversionService = unitConversionService;
        this.documentNumberService = documentNumberService;
        this.stockCheckService = stockCheckService;
        this.auditService = auditService;
        this.businessClock = businessClock;
    }

    /** R9.1, R9.2, R9.7. The customer row lock makes two simultaneous re-sends wait for each other. */
    @Transactional
    public CreateOrderResult create(CreateOrderRequest request, AppUser user) {
        Customer customer = customerRepository.lockById(request.customerId())
                .orElseThrow(() -> new BadRequestException("Unknown customer id " + request.customerId()));

        SalesOrder existing = orderRepository.findByClientUuid(request.clientUuid()).orElse(null);
        if (existing != null) {
            return new CreateOrderResult(resend(existing, customer, request.clientUuid(), user), false);
        }

        boolean bySalesman = user.getRole() == UserRole.SALESMAN;
        if (bySalesman && !customer.isAssignedTo(user)) {
            throw new ForbiddenException("Customer " + customer.getName() + " is not assigned to you");
        }
        if (!customer.isActive()) {
            throw new BadRequestException("Customer " + customer.getName() + " is inactive");
        }
        AppUser salesman = bySalesman ? user : customer.getSalesman();
        if (salesman == null) {
            throw new BadRequestException("Customer " + customer.getName() + " has no salesman assigned");
        }

        LocalDate today = LocalDate.now(businessClock);   // the business date (ClockConfig)
        SalesOrder order = new SalesOrder();
        order.setClientUuid(request.clientUuid());
        order.setCustomer(customer);
        order.setSalesman(salesman);
        order.setOrderDate(today);
        order.setStatus(OrderStatus.SUBMITTED);

        Map<String, Item> products = new HashMap<>();
        for (CreateOrderRequest.Line entered : request.lines()) {
            Item product = requireSellableProduct(entered.itemCode(), products);
            if (product.getSellingPrice().signum() <= 0) {
                throw new BadRequestException(product.getCode() + " has no selling price");
            }
            BigDecimal packets = unitConversionService.toBase(product.getId(), entered.qty(), entered.uomCode());
            requireWholePackets(product.getCode(), packets);

            SalesOrderLine line = new SalesOrderLine();
            line.setItem(product);
            line.setQty(packets);
            line.setRate(Money.money(product.getSellingPrice()));   // R9.2: price snapshot
            order.addLine(line);
        }
        order.setOrderNo(documentNumberService.next(DocType.SALES_ORDER, today));   // R9.8
        orderRepository.save(order);

        auditService.log(ENTITY, order.getId(), AuditAction.CREATE, null, snapshot(order), user);
        return new CreateOrderResult(OrderResponse.from(order), true);
    }

    /** R9.7: the same UUID returns the saved order. It must be visible to the caller and for the same customer. */
    private OrderResponse resend(SalesOrder existing, Customer customer, UUID clientUuid, AppUser user) {
        if (!canSee(existing, user) || !existing.getCustomer().getId().equals(customer.getId())) {
            throw new ConflictException("clientUuid " + clientUuid + " was already used for another order");
        }
        return OrderResponse.from(existing);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long orderId, AppUser user) {
        return OrderResponse.from(findVisible(orderId, user));
    }

    /** R2.2: a salesman sees only their own orders. status is an optional filter. */
    @Transactional(readOnly = true)
    public List<OrderResponse> list(OrderStatus status, AppUser user) {
        List<SalesOrder> orders;
        if (user.getRole() == UserRole.SALESMAN) {
            orders = status == null
                    ? orderRepository.findBySalesmanIdOrderByIdDesc(user.getId())
                    : orderRepository.findBySalesmanIdAndStatusOrderByIdDesc(user.getId(), status);
        } else {
            orders = status == null
                    ? orderRepository.findAllByOrderByIdDesc()
                    : orderRepository.findByStatusOrderByIdDesc(status);
        }
        return orders.stream().map(OrderResponse::from).toList();
    }

    /** R9.4: shown before approval. Only a preview: approve() checks again under the item locks. */
    @Transactional(readOnly = true)
    public StockCheckResponse stockCheck(Long orderId, AppUser user) {
        SalesOrder order = findVisible(orderId, user);
        List<StockCheckResponse.Line> lines = order.getLines().stream()
                .map(line -> stockCheckService.check(line.getItem(), line.getQty(), order.getId()))
                .toList();
        boolean canApprove = order.getStatus() == OrderStatus.SUBMITTED
                && lines.stream().allMatch(line -> line.shortage().signum() == 0);
        return new StockCheckResponse(order.getId(), order.getOrderNo(), canApprove, lines);
    }

    /**
     * R9.3: the admin replaces the lines before approval. Existing lines are changed in place, because deleting
     * and re-inserting the same item would hit the unique (order, item) key.
     */
    @Transactional
    public OrderResponse updateLines(Long orderId, UpdateOrderLinesRequest request, AppUser user) {
        SalesOrder order = lockOrder(orderId);
        requireStatus(order, "edited", OrderStatus.SUBMITTED);
        Map<String, Object> before = snapshot(order);

        Map<String, SalesOrderLine> oldLines = new HashMap<>();
        for (SalesOrderLine line : order.getLines()) {
            oldLines.put(line.getItem().getCode(), line);
        }
        Map<String, Item> newProducts = new HashMap<>();
        for (UpdateOrderLinesRequest.Line entered : request.lines()) {
            Item product = requireSellableProduct(entered.itemCode(), newProducts);
            BigDecimal packets = Money.qty(entered.qty());
            requireWholePackets(product.getCode(), packets);
            requireValidRate(product.getCode(), entered.rate());

            SalesOrderLine line = oldLines.get(product.getCode());
            if (line == null) {
                line = new SalesOrderLine();
                line.setItem(product);
                order.addLine(line);
            }
            line.setQty(packets);
            line.setRate(Money.money(entered.rate()));
        }
        order.getLines().removeIf(line -> !newProducts.containsKey(line.getItem().getCode()));
        orderRepository.flush();   // new lines get their ids before the response is built

        auditService.log(ENTITY, order.getId(), AuditAction.UPDATE, before, snapshot(order), user);
        return OrderResponse.from(order);
    }

    /**
     * R9.5, R9.6: locks the finished-goods items, THEN reads stock and reservations. A second approval of the same
     * product waits at the lock and then sees this order's reservation, so the same packets are never promised twice.
     */
    @Transactional
    public OrderResponse approve(Long orderId, ApproveOrderRequest request, AppUser user) {
        SalesOrder order = lockOrder(orderId);
        requireStatus(order, "approved", OrderStatus.SUBMITTED);
        Map<String, Object> before = snapshot(order);
        Map<String, BigDecimal> approved = approvedQuantities(order, request);

        // R6.7: the query locks in ascending id order
        itemRepository.lockAllByIdOrderById(order.getLines().stream().map(line -> line.getItem().getId()).toList());

        List<String> shortages = new ArrayList<>();
        for (SalesOrderLine line : order.getLines()) {
            BigDecimal wanted = approved.get(line.getItem().getCode());
            StockCheckResponse.Line check = stockCheckService.check(line.getItem(), wanted, order.getId());
            if (check.shortage().signum() > 0) {
                shortages.add(describeShortage(line, check));
            }
        }
        if (!shortages.isEmpty()) {
            throw new OrderShortageException(
                    "Order " + order.getOrderNo() + " cannot be approved: not enough finished-goods stock", shortages);
        }

        for (SalesOrderLine line : order.getLines()) {
            line.setApprovedQty(approved.get(line.getItem().getCode()));
        }
        order.setStatus(OrderStatus.APPROVED);
        order.setApprovedBy(user);
        order.setApprovedAt(Instant.now());

        auditService.log(ENTITY, order.getId(), AuditAction.APPROVE, before, snapshot(order), user);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse reject(Long orderId, String reason, AppUser user) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A rejection needs a reason");
        }
        SalesOrder order = lockOrder(orderId);
        requireStatus(order, "rejected", OrderStatus.SUBMITTED);
        Map<String, Object> before = snapshot(order);

        order.setStatus(OrderStatus.REJECTED);
        order.setRemarks(reason.strip());

        auditService.log(ENTITY, order.getId(), AuditAction.REJECT, before, snapshot(order), user);
        return OrderResponse.from(order);
    }

    /** R9.9: allowed before invoicing. Cancelling an APPROVED order releases its reservation. */
    @Transactional
    public OrderResponse cancel(Long orderId, AppUser user) {
        SalesOrder order = lockOrder(orderId);
        requireStatus(order, "cancelled", OrderStatus.SUBMITTED, OrderStatus.APPROVED);
        Map<String, Object> before = snapshot(order);

        order.setStatus(OrderStatus.CANCELLED);

        auditService.log(ENTITY, order.getId(), AuditAction.CANCEL, before, snapshot(order), user);
        return OrderResponse.from(order);
    }

    /** The quantity to approve per item code: what the admin entered, otherwise the full ordered quantity. */
    private Map<String, BigDecimal> approvedQuantities(SalesOrder order, ApproveOrderRequest request) {
        Map<String, BigDecimal> ordered = new LinkedHashMap<>();
        for (SalesOrderLine line : order.getLines()) {
            ordered.put(line.getItem().getCode(), line.getQty());
        }
        Map<String, BigDecimal> approved = new LinkedHashMap<>(ordered);
        if (request == null || request.lines() == null) {
            return approved;
        }
        Map<String, BigDecimal> entered = new HashMap<>();
        for (ApproveOrderRequest.Line line : request.lines()) {
            BigDecimal limit = ordered.get(line.itemCode());
            if (limit == null) {
                throw new BadRequestException("Item '" + line.itemCode() + "' is not on order " + order.getOrderNo());
            }
            BigDecimal qty = Money.qty(line.approvedQty());
            requireWholePackets(line.itemCode(), qty);
            if (qty.compareTo(limit) > 0) {
                throw new BadRequestException("Approved quantity of " + line.itemCode() + " (" + plain(qty)
                        + ") is more than ordered (" + plain(limit) + ")");
            }
            if (entered.put(line.itemCode(), qty) != null) {
                throw new BadRequestException("Item " + line.itemCode() + " appears twice");
            }
        }
        approved.putAll(entered);
        return approved;
    }

    private String describeShortage(SalesOrderLine line, StockCheckResponse.Line check) {
        // check.ordered() is the quantity being approved. It is only mentioned when it differs from the order
        String approving = check.ordered().compareTo(line.getQty()) == 0 ? "" : ", approving " + plain(check.ordered());
        String batches = check.batchesNeeded() == null ? "unknown (no active BOM)" : check.batchesNeeded().toString();
        return check.itemCode() + ": ordered " + plain(line.getQty()) + approving
                + ", available " + plain(check.available())
                + ", short by " + plain(check.shortage())
                + ", batches needed " + batches;
    }

    /** An active finished good, listed only once (each one found is remembered in seen). */
    private Item requireSellableProduct(String itemCode, Map<String, Item> seen) {
        Item product = itemRepository.findByCode(itemCode)
                .orElseThrow(() -> new BadRequestException("Unknown product '" + itemCode + "'"));
        if (product.getKind() != ItemKind.FG || !product.isActive()) {
            throw new BadRequestException("'" + itemCode + "' is not an active finished good");
        }
        if (seen.put(product.getCode(), product) != null) {
            throw new BadRequestException("Item " + itemCode + " appears on more than one line");
        }
        return product;
    }

    /** R9.1: packets cannot be split. */
    private void requireWholePackets(String itemCode, BigDecimal packets) {
        if (packets.signum() <= 0 || packets.stripTrailingZeros().scale() > 0) {
            throw new BadRequestException(
                    "Quantity of " + itemCode + " must be a whole number of packets, got " + plain(packets));
        }
    }

    /** A zero-value invoice could never be cancelled, because a credit note needs a total above 0 (R10.7). */
    private void requireValidRate(String itemCode, BigDecimal rate) {
        if (rate.signum() <= 0 || rate.stripTrailingZeros().scale() > Money.MONEY_SCALE) {
            throw new BadRequestException("Rate of " + itemCode + " must be above 0, with at most 2 decimals");
        }
    }

    private SalesOrder lockOrder(Long orderId) {
        return orderRepository.lockById(orderId).orElseThrow(() -> notFound(orderId));
    }

    /** R2.2: someone else's order is reported as not found, so its existence is not revealed. */
    private SalesOrder findVisible(Long orderId, AppUser user) {
        return orderRepository.findById(orderId)
                .filter(order -> canSee(order, user))
                .orElseThrow(() -> notFound(orderId));
    }

    private boolean canSee(SalesOrder order, AppUser user) {
        return user.getRole() != UserRole.SALESMAN || order.getSalesman().getId().equals(user.getId());
    }

    private NotFoundException notFound(Long orderId) {
        return new NotFoundException("Order " + orderId + " not found");
    }

    private void requireStatus(SalesOrder order, String action, OrderStatus... allowed) {
        if (!List.of(allowed).contains(order.getStatus())) {
            throw new ConflictException("Order " + order.getOrderNo() + " is " + order.getStatus()
                    + " and cannot be " + action);
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** A small JSON-friendly picture of the order for the audit log. */
    private Map<String, Object> snapshot(SalesOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("status", order.getStatus().name());
        data.put("remarks", order.getRemarks());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (SalesOrderLine line : order.getLines()) {
            Map<String, Object> lineData = new LinkedHashMap<>();
            lineData.put("itemCode", line.getItem().getCode());
            lineData.put("qty", line.getQty());
            lineData.put("rate", line.getRate());
            lineData.put("approvedQty", line.getApprovedQty());
            lines.add(lineData);
        }
        data.put("lines", lines);
        return data;
    }
}
