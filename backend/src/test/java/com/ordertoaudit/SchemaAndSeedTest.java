package com.ordertoaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the V1 schema and the benchmark seed (V1000) against a real Postgres.
 *
 * Every test runs in a transaction that is rolled back, so tests cannot affect each other.
 * A rejected statement would normally abort that transaction, so {@link #assertRejected}
 * wraps it in a savepoint and rolls back to it. That lets one test check several bad inserts.
 */
@Transactional
class SchemaAndSeedTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    // ------------------------------------------------------------------
    // Seed data (problem statement section 5, assumptions A1, A2, A14)
    // ------------------------------------------------------------------

    @Test
    void seedLoadsTheExpectedMasterDataCounts() {
        assertThat(count("app_user")).isEqualTo(6);
        assertThat(count("company")).isEqualTo(1);
        assertThat(count("uom")).isEqualTo(7);
        assertThat(count("item")).isEqualTo(6);
        assertThat(count("item_uom_conversion")).isEqualTo(1);
        assertThat(count("customer")).isEqualTo(2);
        assertThat(count("supplier")).isEqualTo(1);
        assertThat(count("bom")).isEqualTo(1);
        assertThat(count("bom_line")).isEqualTo(5);
        assertThat(count("invoice_sequence")).isEqualTo(3);   // INVOICE, CREDIT_NOTE (seed) and PRODUCTION_RUN (V2)
        assertThat(count("stock_movement")).isEqualTo(6);
    }

    @Test
    void seedDoesNotIncludeTheSixtyKgPurchase() {
        // The benchmark test posts this purchase itself (step 1).
        assertThat(count("purchase")).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM stock_movement WHERE movement_type = 'RECEIPT'", Integer.class)).isZero();
    }

    @Test
    void openingBalancesMatchTheBrief() {
        // A14: finished goods open at 30.00 per packet = 1500.00
        assertBalance("FLOUR",      "100", "4200.00");
        assertBalance("OIL",        "30",  "4200.00");
        assertBalance("SPICE",      "5",   "1000.00");
        assertBalance("POUCH",      "500", "1250.00");
        assertBalance("CARTON",     "30",  "450.00");
        assertBalance("MURUKKU200", "50",  "1500.00");
    }

    @Test
    void bomMatchesTheBenchmarkRecipe() {
        Map<String, BigDecimal> perBatch = Map.of(
                "FLOUR", new BigDecimal("40"), "OIL", new BigDecimal("10"), "SPICE", new BigDecimal("1.2"),
                "POUCH", new BigDecimal("200"), "CARTON", new BigDecimal("10"));
        perBatch.forEach((code, expected) -> {
            BigDecimal actual = jdbc.queryForObject(
                    "SELECT bl.qty FROM bom_line bl JOIN item i ON i.id = bl.item_id WHERE i.code = ?",
                    BigDecimal.class, code);
            assertThat(actual).isEqualByComparingTo(expected);
        });
        assertThat(jdbc.queryForObject("SELECT output_qty FROM bom", BigDecimal.class))
                .isEqualByComparingTo("200");
    }

    @Test
    void companyAndCustomerStatesDriveTheGstSplit() {
        assertThat(jdbc.queryForObject("SELECT state_code FROM company", String.class)).isEqualTo("33");
        assertThat(jdbc.queryForObject("SELECT state_code FROM customer WHERE code = 'RET-A'", String.class))
                .isEqualTo("33");
        assertThat(jdbc.queryForObject("SELECT state_code FROM customer WHERE code = 'DIST-B'", String.class))
                .isEqualTo("29");
    }

    @Test
    void ledgerReconcilesWithTheBalanceView() {
        // R6.4 / R6.5: the view is derived from the ledger, so both must agree per item and in total.
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
        // Total opening value: 4200 + 4200 + 1000 + 1250 + 450 + 1500
        assertThat(jdbc.queryForObject("SELECT SUM(stock_value) FROM stock_balance", BigDecimal.class))
                .isEqualByComparingTo("12600.00");
    }

    @Test
    void everyMovementValueIsQtyTimesUnitCost() {
        // R7.4: value is stored on the movement as qty x unit_cost, rounded to 2 decimals.
        Integer mismatches = jdbc.queryForObject(
                "SELECT count(*) FROM stock_movement WHERE value <> round(qty * unit_cost, 2)", Integer.class);
        assertThat(mismatches).isZero();
    }

    // ------------------------------------------------------------------
    // Fix 1 + line uniques: no double posting (R6.8)
    // ------------------------------------------------------------------

    @Test
    void sameDocumentCannotMoveTheSameItemTwice() {
        insertMovement("FLOUR", "60", "45", "2700.00", "RECEIPT", "PURCHASE", 900L);

        assertRejected("ux_stock_movement_no_double_post",
                () -> insertMovement("FLOUR", "60", "45", "2700.00", "RECEIPT", "PURCHASE", 900L));

        // Another item on the same document, or the same item on another document, is fine.
        assertThatCode(() -> insertMovement("OIL", "10", "140", "1400.00", "RECEIPT", "PURCHASE", 900L))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertMovement("FLOUR", "10", "45", "450.00", "RECEIPT", "PURCHASE", 901L))
                .doesNotThrowAnyException();
    }

    @Test
    void secondOpeningRowForAnItemIsRejected() {
        // OPENING rows have source_id NULL. NULLS NOT DISTINCT makes the unique constraint still catch them.
        assertRejected("ux_stock_movement_no_double_post", () -> jdbc.update("""
                INSERT INTO stock_movement (item_id, movement_date, qty, unit_cost, value, movement_type, source_type, created_by)
                VALUES ((SELECT id FROM item WHERE code = 'FLOUR'), now(), 1, 42, 42.00, 'OPENING', 'OPENING', %s)
                """.formatted(adminId())));
    }

    @Test
    void purchaseBillWithTwoLinesForTheSameItemIsRejected() {
        long purchaseId = insertPurchase();
        insertPurchaseLine(purchaseId, "FLOUR");

        // Rejected at the line, before a second RECEIPT could ever be posted.
        assertRejected("ux_purchase_line_item", () -> insertPurchaseLine(purchaseId, "FLOUR"));
        assertThatCode(() -> insertPurchaseLine(purchaseId, "OIL")).doesNotThrowAnyException();
    }

    @Test
    void dispatchWithTwoLinesForTheSameItemIsRejected() {
        long dispatchId = insertDispatch(insertInvoice("INTRA"));
        jdbc.update("INSERT INTO dispatch_line (dispatch_id, item_id, qty) VALUES (?, (SELECT id FROM item WHERE code = 'MURUKKU200'), 10)", dispatchId);

        assertRejected("ux_dispatch_line_item", () -> jdbc.update(
                "INSERT INTO dispatch_line (dispatch_id, item_id, qty) VALUES (?, (SELECT id FROM item WHERE code = 'MURUKKU200'), 5)", dispatchId));
    }

    @Test
    void invoiceWithTwoLinesForTheSameItemIsRejected() {
        long invoiceId = insertInvoice("INTRA");
        insertInvoiceLine(invoiceId);

        assertRejected("ux_invoice_line_item", () -> insertInvoiceLine(invoiceId));
    }

    // ------------------------------------------------------------------
    // Fix 2: audit_log
    // ------------------------------------------------------------------

    @Test
    void auditLogAcceptsEveryActionInTheWidenedList() {
        List<String> actions = List.of("CREATE", "UPDATE", "APPROVE", "REJECT", "CANCEL",
                "POST", "REVERSE", "DISPATCH", "PAYMENT", "LOGIN", "LOGIN_FAILED");
        for (String action : actions) {
            assertThatCode(() -> jdbc.update(
                    "INSERT INTO audit_log (entity_type, entity_id, action, user_id) VALUES ('INVOICE', 1, ?, ?)",
                    action, adminId())).doesNotThrowAnyException();
        }
        assertRejected("audit_log_action_check", () -> jdbc.update(
                "INSERT INTO audit_log (entity_type, entity_id, action, user_id) VALUES ('INVOICE', 1, 'DELETE', ?)", adminId()));
        // COMPLETE was removed: runs are POSTED now
        assertRejected("audit_log_action_check", () -> jdbc.update(
                "INSERT INTO audit_log (entity_type, entity_id, action, user_id) VALUES ('PRODUCTION_RUN', 1, 'COMPLETE', ?)", adminId()));
    }

    @Test
    void failedLoginCanBeLoggedWithNoUserAndNoEntity() {
        // R14.4: unknown username -> no user_id and no entity_id, but the username is kept.
        jdbc.update("INSERT INTO audit_log (entity_type, action, username) VALUES ('USER', 'LOGIN_FAILED', 'nobody')");

        Map<String, Object> row = jdbc.queryForMap("SELECT user_id, entity_id, username FROM audit_log WHERE action = 'LOGIN_FAILED'");
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("entity_id")).isNull();
        assertThat(row.get("username")).isEqualTo("nobody");
    }

    // ------------------------------------------------------------------
    // Fix 3: credit_note tax columns, invoice_sequence key
    // ------------------------------------------------------------------

    @Test
    void creditNoteTotalMustEqualTaxablePlusTaxes() {
        long invoiceId = insertInvoice("INTRA");
        assertThatCode(() -> insertCreditNote(invoiceId, "CN/T/1", "INTRA", "100.00", "2.50", "2.50", "0.00", "105.00"))
                .doesNotThrowAnyException();

        long otherInvoice = insertInvoice("INTRA");
        assertRejected("cn_total_adds_up",
                () -> insertCreditNote(otherInvoice, "CN/T/2", "INTRA", "100.00", "2.50", "2.50", "0.00", "104.00"));
    }

    @Test
    void creditNoteTaxMustMatchSupplyType() {
        long intra = insertInvoice("INTRA");
        // Intra-state with IGST is wrong
        assertRejected("cn_tax_matches_supply",
                () -> insertCreditNote(intra, "CN/T/3", "INTRA", "100.00", "0.00", "0.00", "5.00", "105.00"));

        long inter = insertInvoice("INTER");
        // Inter-state with CGST/SGST is wrong
        assertRejected("cn_tax_matches_supply",
                () -> insertCreditNote(inter, "CN/T/4", "INTER", "100.00", "2.50", "2.50", "0.00", "105.00"));
        // Inter-state with IGST only is right
        assertThatCode(() -> insertCreditNote(inter, "CN/T/5", "INTER", "100.00", "0.00", "0.00", "5.00", "105.00"))
                .doesNotThrowAnyException();
    }

    @Test
    void invoiceSequenceIsOneRowPerDocTypePerFinancialYear() {
        // 2026-27 already has INVOICE and CREDIT_NOTE (seed) and PRODUCTION_RUN (V2): one row per doc_type...
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM invoice_sequence WHERE financial_year = '2026-27'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT prefix FROM invoice_sequence WHERE doc_type = 'PRODUCTION_RUN'", String.class)).isEqualTo("RUN");
        // The doc_type list was widened by V2, but only to the three real document types
        assertRejected("invoice_sequence_doc_type_check",
                () -> jdbc.update("INSERT INTO invoice_sequence (doc_type, financial_year, prefix) VALUES ('DISPATCH', '2026-27', 'DSP')"));
        // ...but the same (doc_type, financial_year) twice does not.
        assertRejected("invoice_sequence_pkey",
                () -> jdbc.update("INSERT INTO invoice_sequence (doc_type, financial_year, prefix) VALUES ('INVOICE', '2026-27', 'INV')"));
        assertThatCode(() -> jdbc.update("INSERT INTO invoice_sequence (doc_type, financial_year, prefix) VALUES ('INVOICE', '2027-28', 'INV')"))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // Fix 4: state codes are exactly two digits
    // ------------------------------------------------------------------

    @Test
    void stateCodeMustBeTwoDigitsEverywhere() {
        for (String bad : List.of("3", "AB", "3A", "333")) {
            assertRejected(null, () -> insertCustomer("C-" + bad, bad, null));
            assertRejected(null, () -> jdbc.update("INSERT INTO supplier (name, state_code) VALUES ('S', ?)", bad));
            assertRejected(null, () -> jdbc.update("INSERT INTO company (name, gstin, state_code) VALUES ('X', '33AAACO1234A1Z5', ?)", bad));
        }
        assertRejected("invoice_place_of_supply_check", () -> insertInvoiceWithPlace("AB"));
        // supplier state_code is optional
        assertThatCode(() -> jdbc.update("INSERT INTO supplier (name) VALUES ('No state')")).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // Fix 5: GSTIN must start with the state code (R5.3)
    // ------------------------------------------------------------------

    @Test
    void customerGstinMustMatchStateAndBeFifteenCharacters() {
        // First two digits (29) differ from state 33
        assertRejected("gstin_matches_state", () -> insertCustomer("G1", "33", "29AAACD7654B1Z2"));
        // 14 characters
        assertRejected("gstin_matches_state", () -> insertCustomer("G2", "33", "33AAACR5055K1Z"));
        // Matching state, and no GSTIN at all (unregistered), are both fine
        assertThatCode(() -> insertCustomer("G3", "33", "33AAACR5055K1Z5")).doesNotThrowAnyException();
        assertThatCode(() -> insertCustomer("G4", "33", null)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // Fix 6: stock_movement value follows the sign of qty
    // ------------------------------------------------------------------

    @Test
    void movementValueMustHaveTheSameSignAsQty() {
        // ADJUSTMENT accepts either sign of qty, so only the new check is being tested here.
        assertRejected("value_sign_matches_qty",
                () -> insertMovement("FLOUR", "10", "42", "-420.00", "ADJUSTMENT", "ADJUSTMENT", 800L));
        assertRejected("value_sign_matches_qty",
                () -> insertMovement("FLOUR", "-10", "42", "420.00", "ADJUSTMENT", "ADJUSTMENT", 801L));

        assertThatCode(() -> insertMovement("FLOUR", "10", "42", "420.00", "ADJUSTMENT", "ADJUSTMENT", 802L)).doesNotThrowAnyException();
        assertThatCode(() -> insertMovement("FLOUR", "-10", "42", "-420.00", "ADJUSTMENT", "ADJUSTMENT", 803L)).doesNotThrowAnyException();
        // value 0 is allowed (a tiny qty x tiny cost can round to 0.00)
        assertThatCode(() -> insertMovement("FLOUR", "-0.0001", "0.0100", "0.00", "ADJUSTMENT", "ADJUSTMENT", 804L)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // Fix 7: stock_adjustment needs a reason (R6.9)
    // ------------------------------------------------------------------

    @Test
    void stockAdjustmentRequiresAReason() {
        assertThatCode(() -> insertAdjustment("-2", "Spillage found at stock count")).doesNotThrowAnyException();

        assertRejected("stock_adjustment_reason_check", () -> insertAdjustment("-2", "   "));
        assertRejected("null value in column \"reason\"", () -> insertAdjustment("-2", null));
        assertRejected("stock_adjustment_qty_check", () -> insertAdjustment("0", "No change"));
    }

    // ------------------------------------------------------------------
    // Fix 8: variance tolerance lives on company (R8.9)
    // ------------------------------------------------------------------

    @Test
    void varianceToleranceDefaultsToThreePercent() {
        assertThat(jdbc.queryForObject("SELECT variance_tolerance_pct FROM company", BigDecimal.class))
                .isEqualByComparingTo("3.00");

        jdbc.update("INSERT INTO company (name, gstin, state_code) VALUES ('Second', '33AAACO1234A1Z5', '33')");
        assertThat(jdbc.queryForObject("SELECT variance_tolerance_pct FROM company WHERE name = 'Second'", BigDecimal.class))
                .isEqualByComparingTo("3.00");

        assertRejected("company_variance_tolerance_pct_check",
                () -> jdbc.update("UPDATE company SET variance_tolerance_pct = -1"));
    }

    // ------------------------------------------------------------------
    // Run statuses, reversal state, REVERSAL movements, seeded logins
    // ------------------------------------------------------------------

    @Test
    void productionRunStatusRejectsOldValuesAndDefaultsToDraft() {
        assertRejected("production_run_status_check", () -> insertProductionRun("COMPLETED", null));
        assertRejected("production_run_status_check", () -> insertProductionRun("PLANNED", null));

        for (String status : List.of("DRAFT", "CANCELLED")) {
            assertThatCode(() -> insertProductionRun(status, null)).doesNotThrowAnyException();
        }
        for (String status : List.of("POSTED", "REVERSED")) {
            assertThatCode(() -> insertProductionRun(status, "188")).doesNotThrowAnyException();
        }

        // Status omitted -> DRAFT, with no posting details yet
        long id = jdbc.queryForObject("""
                INSERT INTO production_run (run_no, bom_id, run_date, batches, planned_output, created_by)
                VALUES ('RUN/DEFAULT', (SELECT id FROM bom LIMIT 1), current_date, 1, 200, ?) RETURNING id""",
                Long.class, adminId());
        Map<String, Object> row = jdbc.queryForMap("SELECT status, posted_at, posted_by FROM production_run WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("posted_at")).isNull();
        assertThat(row.get("posted_by")).isNull();
    }

    @Test
    void reversedRunCanStoreWhoReversedItAndWhen() {
        long id = jdbc.queryForObject("""
                INSERT INTO production_run (run_no, bom_id, run_date, batches, planned_output, actual_output, status,
                                            posted_at, posted_by, created_by)
                VALUES ('RUN/REV', (SELECT id FROM bom LIMIT 1), current_date, 1, 200, 188, 'POSTED', now(), ?, ?)
                RETURNING id""", Long.class, adminId(), adminId());

        jdbc.update("UPDATE production_run SET status = 'REVERSED', reversed_at = now(), reversed_by = ? WHERE id = ?",
                adminId(), id);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, reversed_at, reversed_by FROM production_run WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("REVERSED");
        assertThat(row.get("reversed_at")).isNotNull();
        assertThat(((Number) row.get("reversed_by")).longValue()).isEqualTo(adminId());
    }

    @Test
    void postedOrReversedRunMustHaveActualOutput() {
        assertRejected("posted_has_output", () -> insertProductionRun("POSTED", null));
        assertRejected("posted_has_output", () -> insertProductionRun("REVERSED", null));
        // A DRAFT run has no output yet, and that is fine
        assertThatCode(() -> insertProductionRun("DRAFT", null)).doesNotThrowAnyException();
    }

    @Test
    void reversedPaymentIsExcludedFromInvoiceOutstanding() {
        long invoiceId = insertInvoice("INTRA");                 // total 105.00
        long goodPayment = insertPayment("POSTED", "50.00");
        long reversedPayment = insertPayment("REVERSED", "20.00");
        allocate(goodPayment, invoiceId, "50.00");
        allocate(reversedPayment, invoiceId, "20.00");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT paid, outstanding FROM invoice_outstanding WHERE invoice_id = ?", invoiceId);
        assertThat((BigDecimal) row.get("paid")).isEqualByComparingTo("50.00");
        assertThat((BigDecimal) row.get("outstanding")).isEqualByComparingTo("55.00");

        // Reversing the other payment too leaves the full amount outstanding
        jdbc.update("UPDATE payment SET status = 'REVERSED', reversed_at = now(), reversed_by = ? WHERE id = ?",
                adminId(), goodPayment);
        assertThat(jdbc.queryForObject("SELECT outstanding FROM invoice_outstanding WHERE invoice_id = ?",
                BigDecimal.class, invoiceId)).isEqualByComparingTo("105.00");
    }

    @Test
    void purchaseAndPaymentStatusOnlyAllowPostedOrReversed() {
        long purchaseId = insertPurchase();
        assertThat(jdbc.queryForObject("SELECT status FROM purchase WHERE id = ?", String.class, purchaseId))
                .isEqualTo("POSTED");
        assertRejected("purchase_status_check", () -> jdbc.update("UPDATE purchase SET status = 'CANCELLED' WHERE id = ?", purchaseId));
        assertThatCode(() -> jdbc.update(
                "UPDATE purchase SET status = 'REVERSED', reversed_at = now(), reversed_by = ? WHERE id = ?",
                adminId(), purchaseId)).doesNotThrowAnyException();

        assertRejected("payment_status_check", () -> insertPayment("CANCELLED", "10.00"));
    }

    @Test
    void productionRunReversalColumnsMustMatchStatus() {
        assertReversalStateEnforced("production_run", insertProductionRun("POSTED", "188"));
    }

    @Test
    void purchaseReversalColumnsMustMatchStatus() {
        assertReversalStateEnforced("purchase", insertPurchase());
    }

    @Test
    void paymentReversalColumnsMustMatchStatus() {
        assertReversalStateEnforced("payment", insertPayment("POSTED", "10.00"));
    }

    @Test
    void reversalMovementAcceptsEitherSign() {
        // A reversal of a receipt is negative, a reversal of an issue is positive.
        // Both keep the original document's source_type and source_id.
        assertThatCode(() -> insertMovement("FLOUR", "-60", "45", "-2700.00", "REVERSAL", "PURCHASE", 950L)).doesNotThrowAnyException();
        assertThatCode(() -> insertMovement("OIL", "10.5", "140", "1470.00", "REVERSAL", "PRODUCTION_RUN", 951L)).doesNotThrowAnyException();

        // value_sign_matches_qty still applies to reversals
        assertRejected("value_sign_matches_qty", () -> insertMovement("FLOUR", "-5", "45", "225.00", "REVERSAL", "PURCHASE", 952L));
    }

    @Test
    void reversalCanFollowTheOriginalPostingOfTheSameDocumentOnce() {
        // The original and its reversal differ in movement_type, so R6.8 lets both exist...
        insertMovement("FLOUR", "60", "45", "2700.00", "RECEIPT", "PURCHASE", 960L);
        assertThatCode(() -> insertMovement("FLOUR", "-60", "45", "-2700.00", "REVERSAL", "PURCHASE", 960L)).doesNotThrowAnyException();
        // ...but the document cannot be reversed twice.
        assertRejected("ux_stock_movement_no_double_post",
                () -> insertMovement("FLOUR", "-60", "45", "-2700.00", "REVERSAL", "PURCHASE", 960L));
    }

    @Test
    void seededPasswordsAreBcryptCostTenAndMatchTheDocumentedLogins() {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM app_user", String.class);
        assertThat(hashes).hasSize(6).allSatisfy(hash -> assertThat(hash).matches("^\\$2[ab]\\$10\\$.{53}$"));

        // The logins listed in the comment at the top of V1000__benchmark_seed.sql
        Map<String, String> logins = Map.of(
                "admin", "Admin@123", "production", "Prod@123", "salesman_a", "Sales@123",
                "salesman_b", "Sales@123", "auditor", "Audit@123", "owner", "Owner@123");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        logins.forEach((username, password) -> {
            String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username = ?", String.class, username);
            assertThat(encoder.matches(password, hash)).as("login " + username).isTrue();
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Runs the statement and expects the database to reject it. If constraintName is not null,
     * the database error must mention it. The savepoint keeps the test transaction usable afterwards.
     */
    private void assertRejected(String constraintName, Runnable statement) {
        jdbc.execute("SAVEPOINT before_bad_statement");
        try {
            statement.run();
            throw new AssertionError("Expected the database to reject the statement, but it succeeded");
        } catch (DataIntegrityViolationException e) {
            if (constraintName != null) {
                assertThat(e.getMostSpecificCause().getMessage()).contains(constraintName);
            }
        } finally {
            jdbc.execute("ROLLBACK TO SAVEPOINT before_bad_statement");
        }
    }

    /** The same rules for production_run, purchase and payment. The row passed in is POSTED. */
    private void assertReversalStateEnforced(String table, long id) {
        String reversedState = table + "_reversed_state";
        String reversedByWithAt = table + "_reversed_by_with_at";

        // REVERSED without reversed_at
        assertRejected(reversedState, () -> jdbc.update("UPDATE " + table + " SET status = 'REVERSED' WHERE id = ?", id));
        // reversed_at set on a POSTED row. Alone it also breaks the "by" rule (reported first, alphabetically);
        // with reversed_by too, only the state rule is broken.
        assertRejected(reversedByWithAt, () -> jdbc.update("UPDATE " + table + " SET reversed_at = now() WHERE id = ?", id));
        assertRejected(reversedState,
                () -> jdbc.update("UPDATE " + table + " SET reversed_at = now(), reversed_by = ? WHERE id = ?", adminId(), id));
        // reversed_at without reversed_by, and reversed_by without reversed_at
        assertRejected(reversedByWithAt,
                () -> jdbc.update("UPDATE " + table + " SET status = 'REVERSED', reversed_at = now() WHERE id = ?", id));
        assertRejected(reversedByWithAt, () -> jdbc.update("UPDATE " + table + " SET reversed_by = ? WHERE id = ?", adminId(), id));

        // A complete reversal is accepted
        assertThatCode(() -> jdbc.update(
                "UPDATE " + table + " SET status = 'REVERSED', reversed_at = now(), reversed_by = ? WHERE id = ?", adminId(), id))
                .doesNotThrowAnyException();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private void assertBalance(String itemCode, String expectedQty, String expectedValue) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT qty_on_hand, stock_value FROM stock_balance WHERE code = ?", itemCode);
        assertThat((BigDecimal) row.get("qty_on_hand")).as(itemCode + " qty").isEqualByComparingTo(expectedQty);
        assertThat((BigDecimal) row.get("stock_value")).as(itemCode + " value").isEqualByComparingTo(expectedValue);
    }

    private long adminId() {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'admin'", Long.class);
    }

    private void insertMovement(String itemCode, String qty, String unitCost, String value,
                                String movementType, String sourceType, long sourceId) {
        jdbc.update("""
                INSERT INTO stock_movement (item_id, movement_date, qty, unit_cost, value, movement_type, source_type, source_id, created_by)
                VALUES ((SELECT id FROM item WHERE code = ?), now(), ?, ?, ?, ?, ?, ?, ?)""",
                itemCode, new BigDecimal(qty), new BigDecimal(unitCost), new BigDecimal(value),
                movementType, sourceType, sourceId, adminId());
    }

    private void insertAdjustment(String qty, String reason) {
        jdbc.update("""
                INSERT INTO stock_adjustment (item_id, adjustment_date, qty, reason, created_by)
                VALUES ((SELECT id FROM item WHERE code = 'FLOUR'), current_date, ?, ?, ?)""",
                new BigDecimal(qty), reason, adminId());
    }

    private void insertCustomer(String code, String stateCode, String gstin) {
        jdbc.update("INSERT INTO customer (code, name, type, gstin, state_code) VALUES (?, 'Test', 'RETAILER', ?, ?)",
                code, gstin, stateCode);
    }

    /** A REVERSED run gets reversed_at and reversed_by, so only the rule under test can reject it. */
    private long insertProductionRun(String status, String actualOutput) {
        boolean reversed = status.equals("REVERSED");
        return jdbc.queryForObject("""
                INSERT INTO production_run (run_no, bom_id, run_date, batches, planned_output, actual_output, status,
                                            reversed_at, reversed_by, created_by)
                VALUES ('RUN/' || nextval('production_run_id_seq'), (SELECT id FROM bom LIMIT 1), current_date, 1, 200, ?, ?, ?, ?, ?)
                RETURNING id""", Long.class,
                actualOutput == null ? null : new BigDecimal(actualOutput), status,
                reversed ? Timestamp.from(Instant.now()) : null, reversed ? adminId() : null, adminId());
    }

    /** A REVERSED payment gets reversed_at and reversed_by, as the table's reversal constraints require. */
    private long insertPayment(String status, String amount) {
        boolean reversed = status.equals("REVERSED");
        return jdbc.queryForObject("""
                INSERT INTO payment (customer_id, received_on, amount, mode, status, reversed_at, reversed_by, created_by)
                VALUES ((SELECT id FROM customer WHERE code = 'RET-A'), current_date, ?, 'CASH', ?, ?, ?, ?)
                RETURNING id""", Long.class, new BigDecimal(amount), status,
                reversed ? Timestamp.from(Instant.now()) : null, reversed ? adminId() : null, adminId());
    }

    private void allocate(long paymentId, long invoiceId, String amount) {
        jdbc.update("INSERT INTO payment_allocation (payment_id, invoice_id, amount) VALUES (?, ?, ?)",
                paymentId, invoiceId, new BigDecimal(amount));
    }

    private long insertPurchase() {
        return jdbc.queryForObject("""
                INSERT INTO purchase (supplier_id, purchase_date, bill_no, created_by)
                VALUES ((SELECT id FROM supplier LIMIT 1), current_date, 'BILL-' || nextval('purchase_id_seq'), ?)
                RETURNING id""", Long.class, adminId());
    }

    private void insertPurchaseLine(long purchaseId, String itemCode) {
        jdbc.update("""
                INSERT INTO purchase_line (purchase_id, item_id, qty, rate, amount)
                VALUES (?, (SELECT id FROM item WHERE code = ?), 10, 45, 450.00)""", purchaseId, itemCode);
    }

    private long insertInvoice(String supplyType) {
        return insertInvoice(supplyType, "33");
    }

    private void insertInvoiceWithPlace(String placeOfSupply) {
        insertInvoice("INTRA", placeOfSupply);
    }

    /** An INTRA invoice is 100.00 + 2.50 CGST + 2.50 SGST = 105.00. An INTER invoice is 100.00 + 5.00 IGST. */
    private long insertInvoice(String supplyType, String placeOfSupply) {
        boolean intra = supplyType.equals("INTRA");
        return jdbc.queryForObject("""
                INSERT INTO invoice (invoice_no, financial_year, invoice_date, customer_id, place_of_supply, supply_type,
                                     taxable_value, cgst, sgst, igst, total, created_by)
                VALUES ('INV/TEST/' || nextval('invoice_id_seq'), '2026-27', current_date,
                        (SELECT id FROM customer WHERE code = 'RET-A'), ?, ?, 100.00, ?, ?, ?, 105.00, ?)
                RETURNING id""", Long.class,
                placeOfSupply, supplyType,
                new BigDecimal(intra ? "2.50" : "0.00"), new BigDecimal(intra ? "2.50" : "0.00"),
                new BigDecimal(intra ? "0.00" : "5.00"), adminId());
    }

    private void insertInvoiceLine(long invoiceId) {
        jdbc.update("""
                INSERT INTO invoice_line (invoice_id, item_id, hsn_code, qty, rate, taxable_value, gst_rate)
                VALUES (?, (SELECT id FROM item WHERE code = 'MURUKKU200'), '21069099', 2, 50.00, 100.00, 5.00)""", invoiceId);
    }

    private long insertDispatch(long invoiceId) {
        return jdbc.queryForObject("""
                INSERT INTO dispatch (dispatch_no, invoice_id, dispatch_date, created_by)
                VALUES ('DSP/TEST/' || nextval('dispatch_id_seq'), ?, current_date, ?)
                RETURNING id""", Long.class, invoiceId, adminId());
    }

    private void insertCreditNote(long invoiceId, String noteNo, String supplyType, String taxable,
                                  String cgst, String sgst, String igst, String total) {
        jdbc.update("""
                INSERT INTO credit_note (note_no, invoice_id, note_date, reason, supply_type, taxable_value, cgst, sgst, igst, total, created_by)
                VALUES (?, ?, current_date, 'Test', ?, ?, ?, ?, ?, ?, ?)""",
                noteNo, invoiceId, supplyType, new BigDecimal(taxable), new BigDecimal(cgst),
                new BigDecimal(sgst), new BigDecimal(igst), new BigDecimal(total), adminId());
    }
}
