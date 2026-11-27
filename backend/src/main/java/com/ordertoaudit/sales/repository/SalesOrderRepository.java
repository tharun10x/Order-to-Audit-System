package com.ordertoaudit.sales.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.sales.entity.OrderStatus;
import com.ordertoaudit.sales.entity.SalesOrder;

import jakarta.persistence.LockModeType;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    /** SELECT ... FOR UPDATE: only one transaction at a time can change an order's status. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> lockById(@Param("id") Long id);

    Optional<SalesOrder> findByClientUuid(UUID clientUuid);

    /** An order's invoice (also once cancelled): its id and number. */
    interface OrderInvoice {
        Long getOrderId();

        Long getInvoiceId();

        String getInvoiceNo();
    }

    /** SQL, so the sales package needs no billing classes. */
    @Query(value = "SELECT order_id AS orderId, id AS invoiceId, invoice_no AS invoiceNo FROM invoice WHERE order_id = :orderId",
            nativeQuery = true)
    Optional<OrderInvoice> findInvoice(@Param("orderId") Long orderId);

    @Query(value = "SELECT order_id AS orderId, id AS invoiceId, invoice_no AS invoiceNo FROM invoice WHERE order_id IN (:orderIds)",
            nativeQuery = true)
    List<OrderInvoice> findInvoices(@Param("orderIds") Collection<Long> orderIds);

    List<SalesOrder> findAllByOrderByIdDesc();

    List<SalesOrder> findByStatusOrderByIdDesc(OrderStatus status);

    List<SalesOrder> findBySalesmanIdOrderByIdDesc(Long salesmanId);

    List<SalesOrder> findBySalesmanIdAndStatusOrderByIdDesc(Long salesmanId, OrderStatus status);

    /**
     * R9.4, R9.6: packets promised to other orders that are approved but not yet dispatched.
     * excludeOrderId stops an order from counting against itself.
     */
    @Query("""
            select coalesce(sum(l.approvedQty), 0) from SalesOrderLine l
            where l.item.id = :itemId and l.order.id <> :excludeOrderId
              and l.order.status in (com.ordertoaudit.sales.entity.OrderStatus.APPROVED,
                                     com.ordertoaudit.sales.entity.OrderStatus.INVOICED)""")
    BigDecimal reservedQty(@Param("itemId") Long itemId, @Param("excludeOrderId") Long excludeOrderId);
}
