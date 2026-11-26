package com.ordertoaudit.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import com.ordertoaudit.AbstractLedgerTest;
import com.ordertoaudit.common.error.InsufficientStockException;
import com.ordertoaudit.inventory.entity.StockMovement;
import com.ordertoaudit.inventory.service.LedgerService;
import com.ordertoaudit.inventory.web.LedgerRowResponse;

/** R6.* and R7.3, R7.4: the ledger, locking, no negative stock, weighted average valuation. */
class InventoryServiceTest extends AbstractLedgerTest {

    @Autowired
    LedgerService ledgerService;

    @Test
    void outboundMoreThanTheBalanceIsRejectedAndWritesNothing() {
        int movementsBefore = movementCount();

        // The oil issue is fine on its own, but it must be rolled back together with the failing flour issue
        assertThatThrownBy(() -> postMovements(issue("OIL", "5", 1L), issue("FLOUR", "150", 1L)))
                .isInstanceOfSatisfying(InsufficientStockException.class, e -> {
                    assertThat(e.getItemCode()).isEqualTo("FLOUR");
                    assertThat(e.getShortage().toPlainString()).isEqualTo("50.0000");
                    assertThat(e.getMessage()).contains("FLOUR", "available 100.0000", "requested 150.0000", "short by 50.0000");
                });

        assertThat(movementCount()).isEqualTo(movementsBefore);
        assertThat(qtyOnHand("OIL").toPlainString()).isEqualTo("30.0000");
        assertThat(qtyOnHand("FLOUR").toPlainString()).isEqualTo("100.0000");
    }

    @Test
    void outboundIsValuedAtTheWeightedAverage() {
        postMovements(receipt("FLOUR", "60", "2700.00", 1L));   // 160 kg, 6900.00, average 43.125

        StockMovement out = postMovements(issue("FLOUR", "40", 2L)).get(0);

        assertThat(out.getQty().toPlainString()).isEqualTo("-40.0000");
        assertThat(out.getUnitCost().toPlainString()).isEqualTo("43.1250");
        assertThat(out.getValueAmount().toPlainString()).isEqualTo("-1725.00");   // 40 x 43.125
        assertThat(qtyOnHand("FLOUR").toPlainString()).isEqualTo("120.0000");
        assertThat(stockValue("FLOUR").toPlainString()).isEqualTo("5175.00");
    }

    @Test
    void emptyingAnItemLeavesValueExactlyZero() {
        // 6900.01 over 160 kg does not divide evenly, so each partial issue is rounded
        postMovements(receipt("FLOUR", "60", "2700.01", 1L));

        StockMovement first = postMovements(issue("FLOUR", "53.3333", 2L)).get(0);
        StockMovement second = postMovements(issue("FLOUR", "53.3333", 3L)).get(0);
        StockMovement last = postMovements(issue("FLOUR", "53.3334", 4L)).get(0);   // takes everything that is left

        assertThat(qtyOnHand("FLOUR").toPlainString()).isEqualTo("0.0000");
        assertThat(stockValue("FLOUR").toPlainString()).isEqualTo("0.00");
        // The three issues together removed exactly the whole value
        BigDecimal removed = first.getValueAmount().add(second.getValueAmount()).add(last.getValueAmount());
        assertThat(removed.toPlainString()).isEqualTo("-6900.01");
    }

    @Test
    void anIssueInTheSameCallSeesTheReceiptBeforeIt() {
        // 100 kg on hand. Issuing 150 only works if the 60 kg receipt in the same call is already visible.
        List<StockMovement> posted = postMovements(receipt("FLOUR", "60", "2700.00", 1L), issue("FLOUR", "150", 2L));

        StockMovement received = posted.get(0);
        StockMovement issued = posted.get(1);
        assertThat(received.getQty().toPlainString()).isEqualTo("60.0000");
        assertThat(received.getUnitCost().toPlainString()).isEqualTo("45.0000");
        assertThat(issued.getQty().toPlainString()).isEqualTo("-150.0000");
        assertThat(issued.getUnitCost().toPlainString()).isEqualTo("43.1250");       // 6900.00 / 160, including the receipt
        assertThat(issued.getValueAmount().toPlainString()).isEqualTo("-6468.75");   // 6900.00 x 150 / 160

        assertThat(qtyOnHand("FLOUR").toPlainString()).isEqualTo("10.0000");
        assertThat(stockValue("FLOUR").toPlainString()).isEqualTo("431.25");
    }

    @Test
    void ledgerReconcilesWithStockBalance() {
        postMovements(receipt("FLOUR", "60", "2700.00", 1L), receipt("OIL", "10", "1400.00", 1L));
        postMovements(issue("FLOUR", "40", 2L), issue("OIL", "10.5", 2L));
        postMovements(issue("FLOUR", "0.3333", 3L));

        // Every item: the view equals the plain sum of its movements
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.code, b.qty_on_hand, b.stock_value,
                       (SELECT SUM(m.qty)   FROM stock_movement m WHERE m.item_id = b.item_id) AS ledger_qty,
                       (SELECT SUM(m.value) FROM stock_movement m WHERE m.item_id = b.item_id) AS ledger_value
                FROM stock_balance b""");
        assertThat(rows).hasSize(6);
        for (Map<String, Object> row : rows) {
            assertThat((BigDecimal) row.get("qty_on_hand")).isEqualByComparingTo((BigDecimal) row.get("ledger_qty"));
            assertThat((BigDecimal) row.get("stock_value")).isEqualByComparingTo((BigDecimal) row.get("ledger_value"));
        }

        // The ledger endpoint's running balance ends at the current balance (R6.4)
        List<LedgerRowResponse> flourLedger = ledgerService.ledger("FLOUR", null, null);
        LedgerRowResponse lastRow = flourLedger.get(flourLedger.size() - 1);
        assertThat(lastRow.runningQty()).isEqualByComparingTo(qtyOnHand("FLOUR"));
        assertThat(lastRow.runningValue()).isEqualByComparingTo(stockValue("FLOUR"));

        // closing = opening + receipts - issues
        BigDecimal opening = new BigDecimal("100");
        BigDecimal receipts = new BigDecimal("60");
        BigDecimal issues = new BigDecimal("40").add(new BigDecimal("0.3333"));
        assertThat(qtyOnHand("FLOUR")).isEqualByComparingTo(opening.add(receipts).subtract(issues));
    }

    @Test
    void twoThreadsEachIssuing60KgFrom100KgExactlyOneSucceeds() throws Exception {
        // Real transactions, real row locks. The second one must wait for the first and then see only 40 kg.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<String> attempt1 = attemptIssue(60L, ready, go);
        Callable<String> attempt2 = attemptIssue(61L, ready, go);
        Future<String> first = pool.submit(attempt1);
        Future<String> second = pool.submit(attempt2);
        ready.await();
        go.countDown();

        List<String> results = new ArrayList<>(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)));
        pool.shutdown();
        Collections.sort(results);

        assertThat(results).containsExactly("OK", "SHORT");
        assertThat(qtyOnHand("FLOUR").toPlainString()).isEqualTo("40.0000");
        assertThat(stockValue("FLOUR").toPlainString()).isEqualTo("1680.00");   // 4200.00 - 60 x 42
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM stock_movement WHERE movement_type = 'ISSUE'", Integer.class)).isEqualTo(1);
    }

    private Callable<String> attemptIssue(long sourceId, CountDownLatch ready, CountDownLatch go) {
        return () -> {
            var request = issue("FLOUR", "60", sourceId);
            ready.countDown();
            go.await();
            try {
                tx.execute(status -> inventoryService.post(List.of(request)));
                return "OK";
            } catch (InsufficientStockException e) {
                return "SHORT";
            }
        };
    }

    @Test
    void postingOutsideATransactionIsRefused() {
        // InventoryService is MANDATORY: a movement may only be written inside a posting transaction
        assertThatThrownBy(() -> inventoryService.post(receipt("FLOUR", "1", "45.00", 1L)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void theSameDocumentCannotPostTheSameItemTwice() {
        postMovements(receipt("FLOUR", "60", "2700.00", 77L));

        // R6.8 is enforced by the database unique key, and the failed post leaves nothing behind
        assertThatThrownBy(() -> postMovements(receipt("FLOUR", "10", "450.00", 77L)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(qtyOnHand("FLOUR").toPlainString()).isEqualTo("160.0000");
    }
}
