package com.ordertoaudit.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ordertoaudit.AbstractSalesTest;
import com.ordertoaudit.billing.entity.InvoiceStatus;
import com.ordertoaudit.billing.web.DispatchResponse;
import com.ordertoaudit.billing.web.InvoiceResponse;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.InsufficientStockException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;
import com.ordertoaudit.inventory.service.MovementRequest;
import com.ordertoaudit.sales.web.OrderResponse;

/** R10 and R11: invoicing, dispatch, cancellation by credit note, and the PDF. */
class InvoiceAndDispatchTest extends AbstractSalesTest {

    @BeforeEach
    void stock() {
        produceBenchmarkStock();   // 238 packets worth 5555.00
    }

    private InvoiceResponse cancel(Long invoiceId) {
        return invoiceService.cancel(invoiceId, "Wrong order", CREDIT_NOTE_DAY, admin());
    }

    private String pdfOf(Long invoiceId) {
        return pdfText(pdfService.pdf(invoiceId, admin()).content());
    }

    @Test
    void invoicingAnUnapprovedOrderIs409AndUsesNoNumber() {
        OrderResponse order = order("salesman_a", "RET-A", "10");

        assertThatThrownBy(() -> invoice(order.id()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("SUBMITTED");

        assertThat(rowCount("invoice")).isZero();
        orderService.approve(order.id(), null, admin());
        assertThat(invoice(order.id()).invoiceNo()).isEqualTo("INV/2026-27/0001");
    }

    @Test
    void invoicingTwiceIs409() {
        InvoiceResponse invoice = retailerInvoice();

        assertThatThrownBy(() -> invoice(invoice.orderId()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("INVOICED");

        assertThat(rowCount("invoice")).isEqualTo(1);
    }

    @Test
    void invoicingDoesNotChangeStock() {
        OrderResponse order = approvedOrder("salesman_a", "RET-A", "150");
        int movementsBefore = movementCount();
        BigDecimal valueBefore = stockValue(PRODUCT);

        invoice(order.id());

        assertThat(movementCount()).isEqualTo(movementsBefore);
        assertThat(qtyOnHand(PRODUCT).toPlainString()).isEqualTo("238.0000");
        assertThat(stockValue(PRODUCT)).isEqualTo(valueBefore);
    }

    @Test
    void dispatchIsNumberedAndHasOneLinePerInvoiceLine() {
        InvoiceResponse invoice = retailerInvoice();

        DispatchResponse dispatch = dispatch(invoice.id());

        assertThat(dispatch.dispatchNo()).isEqualTo("DC/2026-27/0001");
        assertThat(dispatch.lines()).singleElement()
                .satisfies(line -> assertThat(line.qty().toPlainString()).isEqualTo("150.0000"));
        assertThat(jdbc.queryForObject("SELECT source_id FROM stock_movement WHERE movement_type = 'SALE'", Long.class))
                .isEqualTo(dispatch.id());
        assertThat(invoiceService.get(invoice.id(), admin()).dispatchNo()).isEqualTo("DC/2026-27/0001");
    }

    @Test
    void dispatchingTwiceIs409() {
        InvoiceResponse invoice = retailerInvoice();
        dispatch(invoice.id());
        int movementsAfterFirst = movementCount();

        assertThatThrownBy(() -> dispatch(invoice.id()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("DISPATCHED");

        assertThat(movementCount()).isEqualTo(movementsAfterFirst);
        assertThat(qtyOnHand(PRODUCT).toPlainString()).isEqualTo("88.0000");
    }

    @Test
    void twoDispatchesAtTheSameMomentMoveStockOnce() throws Exception {
        InvoiceResponse invoice = retailerInvoice();

        List<String> outcomes = raceOutcomes(List.of(() -> dispatch(invoice.id()), () -> dispatch(invoice.id())));

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "ConflictException");
        assertThat(qtyOnHand(PRODUCT).toPlainString()).isEqualTo("88.0000");
    }

    @Test
    void dispatchRechecksStockAndWritesNothingWhenItIsShort() {
        InvoiceResponse invoice = retailerInvoice();
        // A stock count finds 100 packets missing after approval: 138 left, 150 invoiced (R11.3)
        postMovements(MovementRequest.outbound(itemId(PRODUCT), MovementType.ADJUSTMENT, new BigDecimal("100"),
                SourceType.ADJUSTMENT, 1L, PURCHASE_INSTANT, admin()));

        assertThatThrownBy(() -> dispatch(invoice.id())).isInstanceOf(InsufficientStockException.class);

        assertThat(rowCount("dispatch")).isZero();
        assertThat(orderStatus(invoice.orderId())).isEqualTo("INVOICED");
        assertThat(jdbc.queryForObject("SELECT next_number FROM invoice_sequence "
                + "WHERE doc_type = 'DISPATCH' AND financial_year = '2026-27'", Integer.class)).isEqualTo(1);
    }

    @Test
    void aDispatchCannotBeDatedBeforeItsInvoice() {
        InvoiceResponse invoice = retailerInvoice();

        assertThatThrownBy(() -> dispatchService.dispatch(new com.ordertoaudit.billing.web.DispatchRequest(
                invoice.id(), INVOICE_DAY.minusDays(1), null), admin()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancellingAPaidInvoiceIs409() {
        InvoiceResponse invoice = retailerInvoice();
        pay("RET-A", invoice.id(), "100.00");

        assertThatThrownBy(() -> cancel(invoice.id()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("payments");

        assertThat(rowCount("credit_note")).isZero();
        assertThat(invoiceService.get(invoice.id(), admin()).status()).isEqualTo(InvoiceStatus.POSTED);
    }

    @Test
    void cancellingADispatchedInvoiceRaisesACreditNoteAndTakesTheGoodsBack() {
        InvoiceResponse invoice = retailerInvoice();
        dispatch(invoice.id());

        InvoiceResponse cancelled = cancel(invoice.id());

        assertThat(cancelled.status()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(cancelled.creditNoteNo()).isEqualTo("CN/2026-27/0001");
        assertThat(cancelled.outstanding()).isNull();
        assertThat(orderStatus(invoice.orderId())).isEqualTo("CANCELLED");

        Map<String, Object> note = jdbc.queryForMap("SELECT * FROM credit_note");
        assertThat(note.get("supply_type")).isEqualTo("INTRA");
        assertThat(note.get("taxable_value").toString()).isEqualTo("7500.00");
        assertThat(note.get("cgst").toString()).isEqualTo("187.50");
        assertThat(note.get("sgst").toString()).isEqualTo("187.50");
        assertThat(note.get("igst").toString()).isEqualTo("0.00");
        assertThat(note.get("total").toString()).isEqualTo("7875.00");

        // R10.7: the 150 packets come back with exactly the value that left
        Map<String, Object> returned = jdbc.queryForMap("SELECT * FROM stock_movement WHERE movement_type = 'RETURN'");
        assertThat(returned.get("source_type")).isEqualTo("CREDIT_NOTE");
        assertThat(returned.get("qty").toString()).isEqualTo("150.0000");
        assertThat(returned.get("value").toString()).isEqualTo("3501.05");
        assertThat(returned.get("unit_cost").toString()).isEqualTo(jdbc.queryForObject(
                "SELECT unit_cost FROM stock_movement WHERE movement_type = 'SALE'", BigDecimal.class).toString());
        assertThat(qtyOnHand(PRODUCT).toPlainString()).isEqualTo("238.0000");
        assertThat(stockValue(PRODUCT).toPlainString()).isEqualTo("5555.00");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_outstanding", Integer.class)).isZero();
        assertThat(paymentService.customerOutstanding(customerId("RET-A"), admin()).totalOutstanding().toPlainString())
                .isEqualTo("0.00");
    }

    @Test
    void cancellingAnUndispatchedInvoiceMovesNoStockAndFreesTheReservation() {
        InvoiceResponse invoice = retailerInvoice();
        int movementsBefore = movementCount();

        cancel(invoice.id());

        assertThat(movementCount()).isEqualTo(movementsBefore);
        OrderResponse next = order("salesman_b", "DIST-B", "238");
        assertThat(orderService.stockCheck(next.id(), admin()).canApprove()).isTrue();
        assertThatThrownBy(() -> dispatch(invoice.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void anInvoiceCanOnlyBeCancelledOnceWithAReasonAndASensibleDate() {
        InvoiceResponse invoice = retailerInvoice();

        assertThatThrownBy(() -> invoiceService.cancel(invoice.id(), " ", CREDIT_NOTE_DAY, admin()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> invoiceService.cancel(invoice.id(), "Early", INVOICE_DAY.minusDays(1), admin()))
                .isInstanceOf(BadRequestException.class);
        cancel(invoice.id());
        assertThatThrownBy(() -> cancel(invoice.id()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("CANCELLED");
        assertThat(rowCount("credit_note")).isEqualTo(1);
    }

    @Test
    void invoiceDispatchAndCancellationAreAudited() {
        InvoiceResponse invoice = retailerInvoice();
        dispatch(invoice.id());
        cancel(invoice.id());

        List<String> entries = jdbc.queryForList("""
                SELECT entity_type || ' ' || action FROM audit_log
                WHERE entity_type IN ('INVOICE', 'DISPATCH', 'CREDIT_NOTE', 'SALES_ORDER') ORDER BY id""", String.class);
        assertThat(entries).containsExactly(
                "SALES_ORDER CREATE", "SALES_ORDER APPROVE",
                "INVOICE POST", "SALES_ORDER UPDATE",
                "DISPATCH DISPATCH", "SALES_ORDER UPDATE",
                "INVOICE CANCEL", "CREDIT_NOTE CREATE", "SALES_ORDER CANCEL");
    }

    @Test
    void aSalesmanSeesOnlyTheInvoicesOfTheirCustomers() {
        InvoiceResponse invoice = retailerInvoice();

        assertThat(invoiceService.list(user("salesman_a"))).hasSize(1);
        assertThat(invoiceService.list(user("salesman_b"))).isEmpty();
        assertThatThrownBy(() -> invoiceService.get(invoice.id(), user("salesman_b")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> pdfService.pdf(invoice.id(), user("salesman_b")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> paymentService.customerOutstanding(customerId("RET-A"), user("salesman_b")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void thePdfShowsTheGstDetails() {
        InvoiceResponse invoice = retailerInvoice();

        String text = pdfOf(invoice.id());

        assertThat(text).contains("TAX INVOICE", "INV/2026-27/0001", "05-Nov-2026",
                "Sample Murukku Foods", "33AAACO1234A1Z5", "Tamil Nadu (33)",
                "Retailer A", "33AAACR5055K1Z5",
                "Murukku 200 g", "21069099", "CGST", "SGST", "7,500.00", "187.50", "7,875.00",
                "₹",   // only possible with the embedded DejaVu font
                "Rupees Seven Thousand Eight Hundred Seventy-Five Only");
        assertThat(text).doesNotContain("IGST", "CANCELLED");
    }

    @Test
    void anInterStatePdfShowsIgst() {
        InvoiceResponse invoice = invoice(approvedOrder("salesman_b", "DIST-B", "120").id());

        String text = pdfOf(invoice.id());

        assertThat(text).contains("IGST", "Karnataka (29)", "300.00", "6,300.00");
        assertThat(text).doesNotContain("CGST", "SGST");
    }

    @Test
    void aCancelledInvoicePdfPointsToTheCreditNote() {
        InvoiceResponse invoice = retailerInvoice();
        cancel(invoice.id());

        assertThat(pdfOf(invoice.id())).contains("CANCELLED — see CN/2026-27/0001", "INV/2026-27/0001", "7,875.00");
    }

    /** R10.5: the invoice keeps its own copy of the company, customer and item details. */
    @Test
    void editingMasterDataAfterInvoicingDoesNotChangeTheInvoiceOrItsPdf() {
        InvoiceResponse invoice = retailerInvoice();
        Map<String, Object> company = jdbc.queryForMap("SELECT name, address FROM company");
        Map<String, Object> customer = jdbc.queryForMap("SELECT name, address, gstin FROM customer WHERE code = 'RET-A'");
        String itemName = jdbc.queryForObject("SELECT name FROM item WHERE code = ?", String.class, PRODUCT);
        try {
            jdbc.update("UPDATE company SET name = 'Renamed Foods', address = 'Madurai'");
            jdbc.update("UPDATE customer SET name = 'Renamed Stores', address = 'Salem', gstin = NULL WHERE code = 'RET-A'");
            jdbc.update("UPDATE item SET name = 'Renamed Snack' WHERE code = ?", PRODUCT);

            String text = pdfOf(invoice.id());
            assertThat(text).contains("Sample Murukku Foods", "Tiruchirappalli, Tamil Nadu",
                    "Retailer A", "33AAACR5055K1Z5", "Murukku 200 g");
            assertThat(text).doesNotContain("Renamed", "Madurai", "Salem", "Unregistered");

            InvoiceResponse reread = invoiceService.get(invoice.id(), admin());
            assertThat(reread.buyer().name()).isEqualTo("Retailer A");
            assertThat(reread.buyer().address()).isEqualTo(customer.get("address"));
            assertThat(reread.seller().name()).isEqualTo("Sample Murukku Foods");
            assertThat(reread.lines().get(0).itemName()).isEqualTo(itemName);
        } finally {
            // These are seed rows shared with other tests
            jdbc.update("UPDATE company SET name = ?, address = ?", company.get("name"), company.get("address"));
            jdbc.update("UPDATE customer SET name = ?, address = ?, gstin = ? WHERE code = 'RET-A'",
                    customer.get("name"), customer.get("address"), customer.get("gstin"));
            jdbc.update("UPDATE item SET name = ? WHERE code = ?", itemName, PRODUCT);
        }
    }
}
