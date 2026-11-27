package com.ordertoaudit.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ordertoaudit.AbstractSalesTest;
import com.ordertoaudit.billing.web.InvoiceResponse;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.payments.entity.InvoicePaymentStatus;
import com.ordertoaudit.payments.entity.PaymentMode;
import com.ordertoaudit.payments.web.PaymentRequest;
import com.ordertoaudit.payments.web.PaymentResponse;

/** R12: allocations, over-payment, and derived outstanding. */
class PaymentServiceTest extends AbstractSalesTest {

    private InvoiceResponse invoice;   // Retailer A, 7875.00

    @BeforeEach
    void anInvoice() {
        produceBenchmarkStock();
        invoice = retailerInvoice();
    }

    private String outstanding(String customerCode) {
        return paymentService.customerOutstanding(customerId(customerCode), admin()).totalOutstanding().toPlainString();
    }

    private void assertRejected(PaymentRequest request, String message) {
        assertThatThrownBy(() -> paymentService.record(request, admin()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining(message);
        assertThat(rowCount("payment")).isZero();
    }

    @Test
    void anInvoiceGoesFromUnpaidToPaid() {
        assertThat(invoice.paymentStatus()).isEqualTo(InvoicePaymentStatus.UNPAID);

        PaymentResponse part = pay("RET-A", invoice.id(), "4000.00");
        assertThat(part.allocations().get(0).outstanding().toPlainString()).isEqualTo("3875.00");
        assertThat(part.allocations().get(0).paymentStatus()).isEqualTo(InvoicePaymentStatus.PARTIALLY_PAID);

        PaymentResponse rest = pay("RET-A", invoice.id(), "3875.00");
        assertThat(rest.allocations().get(0).paymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(outstanding("RET-A")).isEqualTo("0.00");
        assertThat(paymentService.customerOutstanding(customerId("RET-A"), admin()).invoices()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'PAYMENT'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void onePaymentCanCoverTwoInvoices() {
        InvoiceResponse second = invoice(approvedOrder("salesman_a", "RET-A", "10").id());   // 525.00

        paymentService.record(paymentRequest("RET-A", "8000.00",
                allocation(second.id(), "125.00"), allocation(invoice.id(), "7875.00")), admin());

        assertThat(outstanding("RET-A")).isEqualTo("400.00");
    }

    @Test
    void payingMoreThanIsOutstandingIs400() {
        assertRejected(paymentRequest("RET-A", "7875.01", allocation(invoice.id(), "7875.01")), "more than the outstanding");

        pay("RET-A", invoice.id(), "7000.00");
        assertThatThrownBy(() -> pay("RET-A", invoice.id(), "875.01")).isInstanceOf(BadRequestException.class);
        assertThat(outstanding("RET-A")).isEqualTo("875.00");
    }

    @Test
    void allocationsMustAddUpToTheAmount() {
        assertRejected(paymentRequest("RET-A", "4000.00", allocation(invoice.id(), "3999.99")), "must be equal");
        assertRejected(paymentRequest("RET-A", "4000.00", allocation(invoice.id(), "4000.01")), "must be equal");
    }

    @Test
    void badAllocationsAreRejected() {
        assertRejected(paymentRequest("DIST-B", "100.00", allocation(invoice.id(), "100.00")), "does not belong");
        assertRejected(paymentRequest("RET-A", "200.00",
                allocation(invoice.id(), "100.00"), allocation(invoice.id(), "100.00")), "twice");
        assertRejected(paymentRequest("RET-A", "100.00", allocation(999_999L, "100.00")), "Unknown invoice");
        assertRejected(paymentRequest("RET-A", "100.005", allocation(invoice.id(), "100.005")), "2 decimals");
    }

    @Test
    void aPaymentReceivedBeforeTheInvoiceDateIs400() {
        // R12.2, A16: money received before the invoice existed would be an advance
        assertRejected(paymentReceivedOn(INVOICE_DAY.minusDays(1)), "advances are not supported");

        // The invoice date itself is fine (paid on delivery of the bill)
        paymentService.record(paymentReceivedOn(INVOICE_DAY), admin());
        assertThat(outstanding("RET-A")).isEqualTo("7775.00");
    }

    private PaymentRequest paymentReceivedOn(LocalDate receivedOn) {
        return new PaymentRequest(customerId("RET-A"), receivedOn, new BigDecimal("100.00"), PaymentMode.CASH,
                null, List.of(allocation(invoice.id(), "100.00")));
    }

    @Test
    void aCancelledInvoiceCannotBePaid() {
        invoiceService.cancel(invoice.id(), "Wrong order", CREDIT_NOTE_DAY, admin());

        assertRejected(paymentRequest("RET-A", "100.00", allocation(invoice.id(), "100.00")), "CANCELLED");
    }

    @Test
    void oneBadAllocationRejectsTheWholePayment() {
        InvoiceResponse second = invoice(approvedOrder("salesman_a", "RET-A", "10").id());

        assertRejected(paymentRequest("RET-A", "1100.00",
                allocation(invoice.id(), "100.00"), allocation(second.id(), "1000.00")), "more than the outstanding");

        assertThat(rowCount("payment_allocation")).isZero();
    }

    @Test
    void twoPaymentsAtTheSameMomentCannotOverpay() throws Exception {
        var request = paymentRequest("RET-A", "5000.00", allocation(invoice.id(), "5000.00"));
        var cashier = admin();
        Callable<Object> payNow = () -> paymentService.record(request, cashier);

        List<String> outcomes = raceOutcomes(List.of(payNow, payNow));

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "BadRequestException");
        assertThat(outstanding("RET-A")).isEqualTo("2875.00");
    }

    /** A17: received today by the business clock when left out. The no-advance check still applies (R12.2). */
    @Test
    void theReceivedDateDefaultsToTheBusinessDate() {
        PaymentResponse payment = paymentService.record(new PaymentRequest(customerId("RET-A"), null,
                new BigDecimal("100.00"), PaymentMode.CASH, null, List.of(allocation(invoice.id(), "100.00"))), admin());
        assertThat(payment.receivedOn()).isEqualTo(LocalDate.of(2026, 11, 28));

        InvoiceResponse later = invoiceService.createFromOrder(
                approvedOrder("salesman_a", "RET-A", "10").id(), LocalDate.of(2026, 11, 30), admin());
        assertThatThrownBy(() -> paymentService.record(new PaymentRequest(customerId("RET-A"), null,
                new BigDecimal("100.00"), PaymentMode.CASH, null, List.of(allocation(later.id(), "100.00"))), admin()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("advances are not supported");
    }
}
