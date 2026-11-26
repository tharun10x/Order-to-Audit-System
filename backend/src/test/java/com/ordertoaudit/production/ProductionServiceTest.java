package com.ordertoaudit.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.ordertoaudit.AbstractProductionTest;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.InsufficientStockException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.common.error.UnitMismatchException;
import com.ordertoaudit.production.entity.OverheadType;
import com.ordertoaudit.production.entity.RunStatus;
import com.ordertoaudit.production.web.ActualsRequest;
import com.ordertoaudit.production.web.CreateRunRequest;
import com.ordertoaudit.production.web.OverheadRequest;
import com.ordertoaudit.production.web.RunResponse;

/** R8: create, enter actuals, post, cancel, overheads, and the rules around them. */
class ProductionServiceTest extends AbstractProductionTest {

    private String statusInDb(Long runId) {
        return jdbc.queryForObject("SELECT status FROM production_run WHERE id = ?", String.class, runId);
    }

    // ------------------------------------------------------------------
    // create and run numbers
    // ------------------------------------------------------------------

    @Test
    void createCopiesTheActiveBomIntoADraftRun() {
        RunResponse run = productionService.create(
                new CreateRunRequest("MURUKKU200", RUN_DAY, new BigDecimal("2")), admin());

        assertThat(run.status()).isEqualTo(RunStatus.DRAFT);
        assertThat(run.batches().toPlainString()).isEqualTo("2.0000");
        assertThat(run.plannedOutput().toPlainString()).isEqualTo("400.0000");   // 200 x 2 batches
        assertThat(run.lines()).hasSize(5);
        assertThat(lineOf(run, "FLOUR").plannedQty().toPlainString()).isEqualTo("80.0000");   // 40 x 2
        assertThat(lineOf(run, "OIL").plannedQty().toPlainString()).isEqualTo("20.0000");
        assertThat(lineOf(run, "SPICE").plannedQty().toPlainString()).isEqualTo("2.4000");
        assertThat(lineOf(run, "POUCH").plannedQty().toPlainString()).isEqualTo("400.0000");   // provisional until posting
        assertThat(lineOf(run, "CARTON").plannedQty().toPlainString()).isEqualTo("20.0000");   // provisional until posting
        assertThat(lineOf(run, "FLOUR").actualQty()).isNull();
        assertThat(run.costing()).isNull();
        assertThat(movementCount()).isEqualTo(6);   // creating a run never moves stock
    }

    @Test
    void runNumbersCountPerFinancialYear() {
        assertThat(createRun(LocalDate.of(2026, 11, 3)).runNo()).isEqualTo("RUN/2026-27/0001");
        assertThat(createRun(LocalDate.of(2026, 11, 4)).runNo()).isEqualTo("RUN/2026-27/0002");
        // April 2027 is a new financial year, so the count starts again
        assertThat(createRun(LocalDate.of(2027, 4, 1)).runNo()).isEqualTo("RUN/2027-28/0001");
        // March 2027 still belongs to 2026-27
        assertThat(createRun(LocalDate.of(2027, 3, 31)).runNo()).isEqualTo("RUN/2026-27/0003");
    }

    @Test
    void createRejectsUnknownOrNonFinishedProducts() {
        assertThatThrownBy(() -> productionService.create(new CreateRunRequest("NOPE", RUN_DAY, BigDecimal.ONE), admin()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> productionService.create(new CreateRunRequest("FLOUR", RUN_DAY, BigDecimal.ONE), admin()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("finished good");
    }

    @Test
    void aFailedCreateDoesNotUseUpARunNumber() {
        assertThatThrownBy(() -> productionService.create(new CreateRunRequest("NOPE", RUN_DAY, BigDecimal.ONE), admin()))
                .isInstanceOf(BadRequestException.class);

        assertThat(createRun(RUN_DAY).runNo()).isEqualTo("RUN/2026-27/0001");
    }

    // ------------------------------------------------------------------
    // enter actuals
    // ------------------------------------------------------------------

    @Test
    void actualsAreConvertedToBaseUnitsAndKeepScrapInsideTheActual() {
        Long runId = draftRunWithBenchmarkActuals();

        RunResponse run = reportService.report(runId);
        assertThat(lineOf(run, "OIL").actualQty().toPlainString()).isEqualTo("10.5000");   // entered as 10500 ml
        assertThat(lineOf(run, "POUCH").actualQty().toPlainString()).isEqualTo("194.0000");
        assertThat(lineOf(run, "POUCH").scrapQty().toPlainString()).isEqualTo("6.0000");
        assertThat(run.actualOutput().toPlainString()).isEqualTo("188.0000");
        assertThat(run.status()).isEqualTo(RunStatus.DRAFT);
    }

    @Test
    void badActualsAreRejected() {
        Long runId = createRun(RUN_DAY).id();

        // Scrap more than was issued
        assertThatThrownBy(() -> productionService.enterActuals(runId, new ActualsRequest(new BigDecimal("188"),
                List.of(actual("POUCH", "10", "11", "pc"))), admin()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Scrap");
        // An item that is not in the recipe
        assertThatThrownBy(() -> productionService.enterActuals(runId, new ActualsRequest(new BigDecimal("188"),
                List.of(actual("MURUKKU200", "10", "0", "pkt"))), admin()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("not part of the recipe");
        // The same item twice
        assertThatThrownBy(() -> productionService.enterActuals(runId, new ActualsRequest(new BigDecimal("188"),
                List.of(actual("FLOUR", "10", "0", "kg"), actual("FLOUR", "5", "0", "kg"))), admin()))
                .isInstanceOf(BadRequestException.class);
        // A unit that does not fit the item (oil in kg)
        assertThatThrownBy(() -> productionService.enterActuals(runId, new ActualsRequest(new BigDecimal("188"),
                List.of(actual("OIL", "10", "0", "kg"))), admin()))
                .isInstanceOf(UnitMismatchException.class);
    }

    @Test
    void enteringActualsOnAPostedRunIsRejected() {
        Long runId = draftRunWithBenchmarkActuals();
        productionService.post(runId, admin());

        assertThatThrownBy(() -> productionService.enterActuals(runId, benchmarkActuals(), admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("POSTED");
    }

    // ------------------------------------------------------------------
    // post
    // ------------------------------------------------------------------

    @Test
    void postingMovesStockOncePerLineAndLinksEveryMovementToTheRun() {
        Long runId = draftRunWithBenchmarkActuals();

        productionService.post(runId, admin());

        // 5 ISSUE rows + 1 PRODUCTION_OUTPUT, all pointing back to the run
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE source_type = 'PRODUCTION_RUN' AND source_id = ?",
                Integer.class, runId)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE movement_type = 'ISSUE'", Integer.class)).isEqualTo(5);

        // The output is valued at exactly what the issues cost
        BigDecimal issued = jdbc.queryForObject(
                "SELECT -SUM(value) FROM stock_movement WHERE movement_type = 'ISSUE'", BigDecimal.class);
        BigDecimal output = jdbc.queryForObject(
                "SELECT value FROM stock_movement WHERE movement_type = 'PRODUCTION_OUTPUT'", BigDecimal.class);
        assertThat(output).isEqualByComparingTo(issued);
        assertThat(issued.toPlainString()).isEqualTo("4010.00");   // 40 x 42 + 10.5 x 140 + 1.2 x 200 + 194 x 2.5 + 9 x 15
        assertThat(jdbc.queryForObject("SELECT qty FROM stock_movement WHERE movement_type = 'PRODUCTION_OUTPUT'", BigDecimal.class))
                .isEqualByComparingTo("188");
    }

    @Test
    void anItemWithNothingIssuedGetsNoIssueRow() {
        Long runId = createRun(RUN_DAY).id();
        productionService.enterActuals(runId, new ActualsRequest(new BigDecimal("188"), List.of(
                actual("FLOUR", "40", "0", "kg"), actual("OIL", "10", "0", "L"), actual("SPICE", "0", "0", "kg"),
                actual("POUCH", "188", "0", "pc"), actual("CARTON", "9", "0", "pc"))), admin());

        productionService.post(runId, admin());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE movement_type = 'ISSUE'", Integer.class)).isEqualTo(4);
        assertThat(qtyOnHand("SPICE").toPlainString()).isEqualTo("5.0000");
    }

    @Test
    void postingTwiceIs409AndWritesNoNewMovements() {
        Long runId = draftRunWithBenchmarkActuals();
        productionService.post(runId, admin());
        int movementsAfterFirstPost = movementCount();

        assertThatThrownBy(() -> productionService.post(runId, admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("POSTED");

        assertThat(movementCount()).isEqualTo(movementsAfterFirstPost);
        assertThat(qtyOnHand("FLOUR").toPlainString()).isEqualTo("60.0000");   // 100 - 40, issued once
    }

    @Test
    void insufficientStockIs409WithTheWholeShortageListAndTheRunStaysDraft() {
        Long runId = createRun(RUN_DAY).id();
        // Flour: 100 on hand, 150 wanted. Oil: 30 on hand, 40 wanted. The rest is fine.
        productionService.enterActuals(runId, new ActualsRequest(new BigDecimal("188"), List.of(
                actual("FLOUR", "150", "0", "kg"), actual("OIL", "40", "0", "L"), actual("SPICE", "1.2", "0", "kg"),
                actual("POUCH", "188", "0", "pc"), actual("CARTON", "9", "0", "pc"))), admin());
        int movementsBefore = movementCount();

        assertThatThrownBy(() -> productionService.post(runId, admin()))
                .isInstanceOfSatisfying(InsufficientStockException.class, e -> {
                    assertThat(e.getShortages()).extracting(s -> s.itemCode()).containsExactly("FLOUR", "OIL");
                    assertThat(e.getShortages().get(0).shortBy().toPlainString()).isEqualTo("50.0000");
                    assertThat(e.getShortages().get(1).shortBy().toPlainString()).isEqualTo("10.0000");
                });

        assertThat(movementCount()).isEqualTo(movementsBefore);
        assertThat(statusInDb(runId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT posted_at FROM production_run WHERE id = ?", Object.class, runId)).isNull();
        // The packaging standard was not recalculated either: the whole transaction rolled back
        RunResponse run = reportService.report(runId);
        assertThat(lineOf(run, "POUCH").plannedQty().toPlainString()).isEqualTo("200.0000");
        assertThat(lineOf(run, "CARTON").plannedQty().toPlainString()).isEqualTo("10.0000");
        assertThat(lineOf(run, "FLOUR").unitCost()).isNull();
    }

    @Test
    void postingNeedsGoodOutputAndAnActualOnEveryLine() {
        Long runId = createRun(RUN_DAY).id();
        assertThatThrownBy(() -> productionService.post(runId, admin()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("good output");

        // Output entered, but only one line
        productionService.enterActuals(runId, new ActualsRequest(new BigDecimal("188"),
                List.of(actual("FLOUR", "40", "0", "kg"))), admin());
        assertThatThrownBy(() -> productionService.post(runId, admin()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("OIL");

        // Zero good output cannot be posted
        productionService.enterActuals(runId, new ActualsRequest(BigDecimal.ZERO, benchmarkActuals().lines()), admin());
        assertThatThrownBy(() -> productionService.post(runId, admin()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("good output");
        assertThat(movementCount()).isEqualTo(6);
        assertThat(statusInDb(runId)).isEqualTo("DRAFT");
    }

    @Test
    void twoThreadsPostingTheSameRunExactlyOneSucceeds() throws Exception {
        Long runId = draftRunWithBenchmarkActuals();
        int movementsBefore = movementCount();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                var user = admin();
                ready.countDown();
                go.await();
                try {
                    productionService.post(runId, user);
                    return "OK";
                } catch (ConflictException e) {
                    return "CONFLICT";
                }
            }));
        }
        ready.await();
        go.countDown();

        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        Collections.sort(results);

        assertThat(results).containsExactly("CONFLICT", "OK");
        assertThat(movementCount()).isEqualTo(movementsBefore + 6);          // posted once
        assertThat(qtyOnHand("FLOUR").toPlainString()).isEqualTo("60.0000");   // 100 - 40, not 100 - 80
        assertThat(qtyOnHand("MURUKKU200").toPlainString()).isEqualTo("238.0000");
    }

    @Test
    void changingTheBomAfterPostingDoesNotChangeTheRun() {
        Long runId = draftRunWithBenchmarkActuals();
        productionService.post(runId, admin());

        try {
            // Someone edits the recipe afterwards: double the quantities and the yield
            jdbc.update("UPDATE bom_line SET qty = qty * 2");
            jdbc.update("UPDATE bom SET output_qty = 400");

            RunResponse run = reportService.report(runId);
            assertThat(run.plannedOutput().toPlainString()).isEqualTo("200.0000");
            assertThat(lineOf(run, "FLOUR").plannedQty().toPlainString()).isEqualTo("40.0000");
            assertThat(lineOf(run, "OIL").plannedQty().toPlainString()).isEqualTo("10.0000");
            assertThat(lineOf(run, "SPICE").plannedQty().toPlainString()).isEqualTo("1.2000");
            assertThat(lineOf(run, "POUCH").plannedQty().toPlainString()).isEqualTo("188.0000");
            assertThat(lineOf(run, "CARTON").plannedQty().toPlainString()).isEqualTo("9.0000");
            assertThat(lineOf(run, "OIL").variancePct().toPlainString()).isEqualTo("5.00");
            assertThat(run.yieldVariancePct().toPlainString()).isEqualTo("-6.00");
        } finally {
            jdbc.update("UPDATE bom_line SET qty = qty / 2");
            jdbc.update("UPDATE bom SET output_qty = 200");
        }
    }

    @Test
    void aRunCreatedBeforeABomChangeIsPostedWithItsOwnStandards() {
        // R8.5: the standards are copied at creation, so editing the recipe between create and post changes nothing
        Long runId = draftRunWithBenchmarkActuals();
        try {
            jdbc.update("UPDATE bom_line SET qty = qty * 2");
            jdbc.update("UPDATE bom SET output_qty = 400");

            RunResponse run = productionService.post(runId, admin());

            assertThat(lineOf(run, "POUCH").plannedQty().toPlainString()).isEqualTo("188.0000");
            assertThat(lineOf(run, "CARTON").plannedQty().toPlainString()).isEqualTo("9.0000");
        } finally {
            jdbc.update("UPDATE bom_line SET qty = qty / 2");
            jdbc.update("UPDATE bom SET output_qty = 200");
        }
    }

    // ------------------------------------------------------------------
    // cancel and overheads
    // ------------------------------------------------------------------

    @Test
    void aDraftRunCanBeCancelledAndThenNothingElseWorksOnIt() {
        Long runId = draftRunWithBenchmarkActuals();

        RunResponse cancelled = productionService.cancel(runId, admin());

        assertThat(cancelled.status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(statusInDb(runId)).isEqualTo("CANCELLED");
        assertThat(movementCount()).isEqualTo(6);   // no stock effect
        assertThatThrownBy(() -> productionService.post(runId, admin())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> productionService.enterActuals(runId, benchmarkActuals(), admin()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> productionService.cancel(runId, admin())).isInstanceOf(ConflictException.class);
    }

    @Test
    void aPostedRunCannotBeCancelled() {
        Long runId = draftRunWithBenchmarkActuals();
        productionService.post(runId, admin());

        assertThatThrownBy(() -> productionService.cancel(runId, admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("POSTED");
        assertThat(statusInDb(runId)).isEqualTo("POSTED");
    }

    @Test
    void overheadsAreAllowedOnDraftAndPostedRunsOnly() {
        Long draftId = createRun(RUN_DAY).id();
        Long postedId = draftRunWithBenchmarkActuals();
        productionService.post(postedId, admin());
        Long cancelledId = createRun(RUN_DAY).id();
        productionService.cancel(cancelledId, admin());
        OverheadRequest labour = new OverheadRequest(OverheadType.LABOUR, new BigDecimal("1200"), "Labour");

        RunResponse onDraft = productionService.addOverhead(draftId, labour, admin());
        assertThat(onDraft.overheads()).hasSize(1);
        assertThat(onDraft.costing()).isNull();   // costing starts once the run is posted
        assertThat(productionService.addOverhead(postedId, labour, admin()).costing().overheadTotal().toPlainString())
                .isEqualTo("1200.00");
        assertThatThrownBy(() -> productionService.addOverhead(cancelledId, labour, admin()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownRunIsNotFound() {
        assertThatThrownBy(() -> productionService.post(999_999L, admin())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> reportService.report(999_999L)).isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------
    // audit
    // ------------------------------------------------------------------

    @Test
    void everyActionWritesAnAuditRow() {
        Long runId = createRun(RUN_DAY).id();
        productionService.enterActuals(runId, benchmarkActuals(), admin());
        productionService.post(runId, admin());
        productionService.addOverhead(runId, new OverheadRequest(OverheadType.OTHER, new BigDecimal("50"), null), admin());
        Long cancelledId = createRun(RUN_DAY).id();
        productionService.cancel(cancelledId, admin());

        List<String> actions = jdbc.queryForList(
                "SELECT entity_type || ':' || action FROM audit_log ORDER BY id", String.class);
        assertThat(actions).containsExactly(
                "PRODUCTION_RUN:CREATE", "PRODUCTION_RUN:UPDATE", "PRODUCTION_RUN:POST",
                "OVERHEAD_ENTRY:CREATE", "PRODUCTION_RUN:CREATE", "PRODUCTION_RUN:CANCEL");

        // The post entry holds the before and after picture of the run
        assertThat(jdbc.queryForObject(
                "SELECT before_data->>'status' || '>' || (after_data->>'status') FROM audit_log WHERE action = 'POST'",
                String.class)).isEqualTo("DRAFT>POSTED");
    }
}
