package com.ordertoaudit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;
import com.ordertoaudit.inventory.entity.StockMovement;
import com.ordertoaudit.inventory.service.InventoryService;
import com.ordertoaudit.inventory.service.MovementRequest;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.repository.AppUserRepository;

/**
 * Base for tests that post real transactions (they COMMIT, because the concurrency test needs that).
 * To keep the seed intact for other test classes, everything created by a test is deleted before and
 * after it: sales documents, payments, purchases, production runs, audit rows, every movement except the OPENING rows from the seed,
 * and the document counters. Master data goes back to the seed too: records the masters tests created are deleted,
 * and the seed records are active again, with the 200 g price and the version 1 recipe.
 */
public abstract class AbstractLedgerTest extends AbstractIntegrationTest {

    protected static final LocalDate PURCHASE_DAY = LocalDate.of(2026, 11, 2);
    protected static final Instant PURCHASE_INSTANT = PURCHASE_DAY.atStartOfDay(Money.ZONE).toInstant();

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TransactionTemplate tx;

    @Autowired
    protected InventoryService inventoryService;

    @Autowired
    private AppUserRepository userRepository;

    @BeforeEach
    @AfterEach
    void removeEverythingExceptTheSeed() {
        jdbc.update("DELETE FROM audit_log");
        // Sales, in foreign-key order
        jdbc.update("DELETE FROM payment_allocation");
        jdbc.update("DELETE FROM payment");
        jdbc.update("DELETE FROM credit_note");
        jdbc.update("DELETE FROM dispatch_line");
        jdbc.update("DELETE FROM dispatch");
        jdbc.update("DELETE FROM invoice_line");
        jdbc.update("DELETE FROM invoice");
        jdbc.update("DELETE FROM sales_order_line");
        jdbc.update("DELETE FROM sales_order");
        jdbc.update("DELETE FROM overhead_entry");
        jdbc.update("DELETE FROM production_consumption");
        jdbc.update("DELETE FROM production_run");
        jdbc.update("DELETE FROM purchase_line");
        jdbc.update("DELETE FROM purchase");
        jdbc.update("DELETE FROM stock_movement WHERE source_type <> 'OPENING'");
        // Document counters go back to the seed state: only 2026-27 rows, each starting at 1
        jdbc.update("DELETE FROM invoice_sequence WHERE financial_year <> '2026-27'");
        jdbc.update("UPDATE invoice_sequence SET next_number = 1");
        // Masters, after the documents that point at them
        jdbc.update("DELETE FROM bom_line WHERE bom_id IN (SELECT id FROM bom WHERE version > 1)");
        jdbc.update("DELETE FROM bom WHERE version > 1");
        jdbc.update("UPDATE bom SET active = TRUE");
        jdbc.update("DELETE FROM item_uom_conversion WHERE item_id IN (SELECT id FROM item WHERE code NOT IN "
                + "('FLOUR', 'OIL', 'SPICE', 'POUCH', 'CARTON', 'MURUKKU200'))");
        jdbc.update("DELETE FROM bom_line WHERE bom_id IN (SELECT b.id FROM bom b JOIN item i ON i.id = b.product_id "
                + "WHERE i.code <> 'MURUKKU200')");
        jdbc.update("DELETE FROM bom WHERE product_id IN (SELECT id FROM item WHERE code <> 'MURUKKU200')");
        jdbc.update("DELETE FROM item WHERE code NOT IN ('FLOUR', 'OIL', 'SPICE', 'POUCH', 'CARTON', 'MURUKKU200')");
        jdbc.update("UPDATE item SET active = TRUE, selling_price = CASE WHEN code = 'MURUKKU200' THEN 50 END");
        jdbc.update("DELETE FROM customer WHERE code NOT IN ('RET-A', 'DIST-B')");
        jdbc.update("UPDATE customer SET active = TRUE");
        jdbc.update("DELETE FROM supplier WHERE name <> 'Sample Flour Mills'");
        jdbc.update("UPDATE supplier SET active = TRUE");
    }

    protected AppUser admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    protected Long itemId(String code) {
        return jdbc.queryForObject("SELECT id FROM item WHERE code = ?", Long.class, code);
    }

    protected Long supplierId() {
        return jdbc.queryForObject("SELECT id FROM supplier LIMIT 1", Long.class);
    }

    protected BigDecimal qtyOnHand(String code) {
        return jdbc.queryForObject("SELECT qty_on_hand FROM stock_balance WHERE code = ?", BigDecimal.class, code);
    }

    protected BigDecimal stockValue(String code) {
        return jdbc.queryForObject("SELECT stock_value FROM stock_balance WHERE code = ?", BigDecimal.class, code);
    }

    protected int movementCount() {
        return jdbc.queryForObject("SELECT count(*) FROM stock_movement", Integer.class);
    }

    protected MovementRequest receipt(String code, String qty, String value, long sourceId) {
        return MovementRequest.inbound(itemId(code), MovementType.RECEIPT, new BigDecimal(qty), new BigDecimal(value),
                SourceType.PURCHASE, sourceId, PURCHASE_INSTANT, admin());
    }

    protected MovementRequest issue(String code, String qty, long sourceId) {
        return MovementRequest.outbound(itemId(code), MovementType.ISSUE, new BigDecimal(qty),
                SourceType.PRODUCTION_RUN, sourceId, PURCHASE_INSTANT, admin());
    }

    /** Posts the movements the way a posting service would: inside one transaction. */
    protected List<StockMovement> postMovements(MovementRequest... requests) {
        return tx.execute(status -> inventoryService.post(List.of(requests)));
    }
}
