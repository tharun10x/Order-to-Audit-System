package com.ordertoaudit.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ordertoaudit.AbstractSalesTest;
import com.ordertoaudit.billing.entity.SupplyType;
import com.ordertoaudit.billing.web.InvoiceResponse;
import com.ordertoaudit.common.error.OrderShortageException;
import com.ordertoaudit.payments.entity.InvoicePaymentStatus;
import com.ordertoaudit.payments.web.CustomerOutstandingResponse;
import com.ordertoaudit.sales.entity.OrderStatus;
import com.ordertoaudit.sales.web.OrderResponse;
import com.ordertoaudit.sales.web.StockCheckResponse;

/**
 * Problem statement section 5, steps 4 to 7, against the expected results in business-rules.md section 16.
 * Every test starts from the production benchmark state: flour bought and the run posted, 238 packets in stock.
 */
class SalesBenchmarkTest extends AbstractSalesTest {

    @BeforeEach
    void productionBenchmarkState() {
        produceBenchmarkStock();
        assertThat(qtyOnHand(PRODUCT).toPlainString()).isEqualTo("238.0000");
    }

    private InvoiceResponse dispatchedRetailerInvoice() {
        InvoiceResponse invoice = retailerInvoice();
        dispatch(invoice.id());
        return invoice;
    }

    @Test
    void retailerAIsInvoicedWithCgstAndSgst() {
        InvoiceResponse invoice = retailerInvoice();

        assertThat(invoice.invoiceNo()).isEqualTo("INV/2026-27/0001");
        assertThat(invoice.supplyType()).isEqualTo(SupplyType.INTRA);
        assertThat(invoice.placeOfSupply()).isEqualTo("33");
        assertThat(invoice.taxableValue().toPlainString()).isEqualTo("7500.00");
        assertThat(invoice.cgst().toPlainString()).isEqualTo("187.50");
        assertThat(invoice.sgst().toPlainString()).isEqualTo("187.50");
        assertThat(invoice.igst().toPlainString()).isEqualTo("0.00");
        assertThat(invoice.total().toPlainString()).isEqualTo("7875.00");
        assertThat(orderStatus(invoice.orderId())).isEqualTo("INVOICED");
        assertThat(qtyOnHand(PRODUCT).toPlainString()).isEqualTo("238.0000");   // R11.2: not reduced yet
    }

    @Test
    void dispatchingToRetailerALeaves88Packets() {
        InvoiceResponse invoice = dispatchedRetailerInvoice();

        assertThat(qtyOnHand(PRODUCT).toPlainString()).isEqualTo("88.0000");
        // 150 of 238 packets worth 5555.00: 5555.00 x 150 / 238 = 3501.05
        BigDecimal dispatchValue = jdbc.queryForObject(
                "SELECT -value FROM stock_movement WHERE movement_type = 'SALE'", BigDecimal.class);
        assertThat(dispatchValue.toPlainString()).isEqualTo("3501.05");
        assertThat(orderStatus(invoice.orderId())).isEqualTo("DISPATCHED");
    }

    @Test
    void distributorBIsShort32PacketsAndCannotBeApproved() {
        dispatchedRetailerInvoice();
        OrderResponse order = order("salesman_b", "DIST-B", "120");
        int movementsBefore = movementCount();

        StockCheckResponse check = orderService.stockCheck(order.id(), admin());
        assertThat(check.canApprove()).isFalse();
        StockCheckResponse.Line line = check.lines().get(0);
        assertThat(line.ordered().toPlainString()).isEqualTo("120.0000");
        assertThat(line.available().toPlainString()).isEqualTo("88.0000");
        assertThat(line.shortage().toPlainString()).isEqualTo("32.0000");
        assertThat(line.batchesNeeded()).isEqualTo(1);

        OrderShortageException refused = catchThrowableOfType(OrderShortageException.class,
                () -> orderService.approve(order.id(), null, admin()));
        assertThat(refused.getDetails())
                .containsExactly("MURUKKU200: ordered 120, available 88, short by 32, batches needed 1");

        assertThat(orderStatus(order.id())).isEqualTo("SUBMITTED");
        assertThat(movementCount()).isEqualTo(movementsBefore);
    }

    @Test
    void distributorBCanBeApprovedForTheAvailable88AndIsBilledWithIgst() {
        dispatchedRetailerInvoice();
        OrderResponse order = order("salesman_b", "DIST-B", "120");

        OrderResponse approved = orderService.approve(order.id(), approving("88"), admin());

        assertThat(approved.status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(approved.lines().get(0).qty().toPlainString()).isEqualTo("120.0000");
        assertThat(approved.lines().get(0).approvedQty().toPlainString()).isEqualTo("88.0000");

        InvoiceResponse invoice = invoice(approved.id());   // "if invoiced", section 16
        assertThat(invoice.supplyType()).isEqualTo(SupplyType.INTER);
        assertThat(invoice.taxableValue().toPlainString()).isEqualTo("4400.00");
        assertThat(invoice.igst().toPlainString()).isEqualTo("220.00");
        assertThat(invoice.cgst().toPlainString()).isEqualTo("0.00");
        assertThat(invoice.total().toPlainString()).isEqualTo("4620.00");
    }

    @Test
    void aPaymentOf4000LeavesRetailerAOwing3875() {
        InvoiceResponse invoice = dispatchedRetailerInvoice();

        pay("RET-A", invoice.id(), "4000.00");

        CustomerOutstandingResponse outstanding = paymentService.customerOutstanding(customerId("RET-A"), admin());
        assertThat(outstanding.totalOutstanding().toPlainString()).isEqualTo("3875.00");
        assertThat(outstanding.invoices()).singleElement().satisfies(row -> {
            assertThat(row.invoiceNo()).isEqualTo("INV/2026-27/0001");
            assertThat(row.paid().toPlainString()).isEqualTo("4000.00");
            assertThat(row.paymentStatus()).isEqualTo(InvoicePaymentStatus.PARTIALLY_PAID);
        });
    }
}
