package com.ordertoaudit.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ordertoaudit.AbstractLedgerTest;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.UnitMismatchException;
import com.ordertoaudit.inventory.service.LedgerService;
import com.ordertoaudit.inventory.web.BalanceResponse;
import com.ordertoaudit.purchase.service.PurchaseService;
import com.ordertoaudit.purchase.web.PurchaseRequest;
import com.ordertoaudit.purchase.web.PurchaseResponse;

/** R7.1 to R7.3. Benchmark step 1: 60 kg flour at 45 on top of the opening 100 kg at 42. */
class PurchaseServiceTest extends AbstractLedgerTest {

    @Autowired
    PurchaseService purchaseService;

    @Autowired
    LedgerService ledgerService;

    private PurchaseRequest bill(String billNo, PurchaseRequest.Line... lines) {
        return new PurchaseRequest(supplierId(), PURCHASE_DAY, billNo, List.of(lines));
    }

    private PurchaseRequest.Line line(String itemCode, String qty, String uom, String rate) {
        return new PurchaseRequest.Line(itemCode, new BigDecimal(qty), uom, new BigDecimal(rate));
    }

    private BalanceResponse flourBalance() {
        return ledgerService.balances().stream().filter(b -> b.itemCode().equals("FLOUR")).findFirst().orElseThrow();
    }

    private void assertFlourIs160kgAt43point125() {
        BalanceResponse flour = flourBalance();
        assertThat(flour.qtyOnHand().toPlainString()).isEqualTo("160.0000");
        assertThat(flour.stockValue().toPlainString()).isEqualTo("6900.00");
        assertThat(flour.avgCost().toPlainString()).isEqualTo("43.1250");
    }

    @Test
    void benchmarkPurchaseOf60KgFlourGives160KgAt43point125() {
        purchaseService.record(bill("B-1", line("FLOUR", "60", "kg", "45")), admin());

        assertFlourIs160kgAt43point125();
    }

    @Test
    void sameBillEnteredAs60000GramsGivesTheSameResult() {
        // 60000 g at 0.045 per g is 2700.00, exactly the same bill
        PurchaseResponse response = purchaseService.record(bill("B-2", line("FLOUR", "60000", "g", "0.045")), admin());

        assertFlourIs160kgAt43point125();
        PurchaseResponse.Line saved = response.lines().get(0);
        assertThat(saved.qty().toPlainString()).isEqualTo("60.0000");   // stored in kg
        assertThat(saved.rate().toPlainString()).isEqualTo("45.0000");  // per kg
        assertThat(saved.amount().toPlainString()).isEqualTo("2700.00");
    }

    @Test
    void flourEnteredInLitresIsRejectedAndNothingIsSaved() {
        int movementsBefore = movementCount();

        assertThatThrownBy(() -> purchaseService.record(bill("B-3", line("FLOUR", "60", "L", "45")), admin()))
                .isInstanceOf(UnitMismatchException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase", Integer.class)).isZero();
        assertThat(movementCount()).isEqualTo(movementsBefore);
    }

    @Test
    void oneBadLineRejectsTheWholeBill() {
        int movementsBefore = movementCount();

        assertThatThrownBy(() -> purchaseService.record(
                bill("B-4", line("FLOUR", "60", "kg", "45"), line("OIL", "5", "kg", "140")), admin()))
                .isInstanceOf(UnitMismatchException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase", Integer.class)).isZero();
        assertThat(movementCount()).isEqualTo(movementsBefore);
        assertThat(qtyOnHand("FLOUR").toPlainString()).isEqualTo("100.0000");
    }

    @Test
    void stockValueEqualsTheBillAmountEvenWhenTheRateHasManyDecimals() {
        // 3 kg at 33.333 = 99.999, the bill says 100.00
        purchaseService.record(bill("B-5", line("FLOUR", "3", "kg", "33.333")), admin());

        Map<String, Object> savedLine = jdbc.queryForMap("SELECT qty, rate, amount FROM purchase_line");
        assertThat(savedLine.get("amount")).isEqualTo(new BigDecimal("100.00"));
        assertThat(savedLine.get("rate")).isEqualTo(new BigDecimal("33.3330"));   // 99.999 / 3

        Map<String, Object> receipt = jdbc.queryForMap(
                "SELECT qty, unit_cost, value FROM stock_movement WHERE movement_type = 'RECEIPT'");
        assertThat(receipt.get("value")).isEqualTo(new BigDecimal("100.00"));      // = the bill amount
        assertThat(receipt.get("unit_cost")).isEqualTo(new BigDecimal("33.3333")); // 100.00 / 3

        // Opening 4200.00 + bill 100.00
        assertThat(stockValue("FLOUR").toPlainString()).isEqualTo("4300.00");
    }

    @Test
    void aPurchaseWritesExactlyOneAuditRow() {
        PurchaseResponse response = purchaseService.record(bill("B-6", line("FLOUR", "60", "kg", "45")), admin());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class)).isEqualTo(1);
        Map<String, Object> audit = jdbc.queryForMap(
                "SELECT entity_type, entity_id, action, username, before_data IS NULL AS no_before, after_data->>'billNo' AS bill_no FROM audit_log");
        assertThat(audit.get("entity_type")).isEqualTo("PURCHASE");
        assertThat(audit.get("entity_id")).isEqualTo(response.id());
        assertThat(audit.get("action")).isEqualTo("POST");
        assertThat(audit.get("username")).isEqualTo("admin");
        assertThat(audit.get("no_before")).isEqualTo(true);
        assertThat(audit.get("bill_no")).isEqualTo("B-6");
    }

    @Test
    void theSameBillNumberFromTheSameSupplierIsAConflict() {
        purchaseService.record(bill("B-7", line("FLOUR", "1", "kg", "45")), admin());

        assertThatThrownBy(() -> purchaseService.record(bill("B-7", line("OIL", "1", "L", "140")), admin()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void receiptPointsBackToThePurchase() {
        PurchaseResponse response = purchaseService.record(bill("B-8", line("FLOUR", "60", "kg", "45")), admin());

        Map<String, Object> receipt = jdbc.queryForMap(
                "SELECT source_type, source_id, qty FROM stock_movement WHERE movement_type = 'RECEIPT'");
        assertThat(receipt.get("source_type")).isEqualTo("PURCHASE");
        assertThat(receipt.get("source_id")).isEqualTo(response.id());
        assertThat(receipt.get("qty")).isEqualTo(new BigDecimal("60.0000"));
    }
}
