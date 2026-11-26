package com.ordertoaudit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.ordertoaudit.production.service.ProductionService;
import com.ordertoaudit.production.service.RunReportService;
import com.ordertoaudit.production.web.ActualsRequest;
import com.ordertoaudit.production.web.CreateRunRequest;
import com.ordertoaudit.production.web.RunResponse;
import com.ordertoaudit.purchase.service.PurchaseService;
import com.ordertoaudit.purchase.web.PurchaseRequest;

/** Shared helpers for production tests: the benchmark run's numbers (problem statement section 5). */
public abstract class AbstractProductionTest extends AbstractLedgerTest {

    protected static final LocalDate RUN_DAY = LocalDate.of(2026, 11, 3);

    @Autowired
    protected ProductionService productionService;

    @Autowired
    protected RunReportService reportService;

    @Autowired
    protected PurchaseService purchaseService;

    /** Benchmark step 1: 60 kg flour at 45 on top of the opening 100 kg at 42. */
    protected void purchaseSixtyKgFlour() {
        purchaseService.record(new PurchaseRequest(supplierId(), PURCHASE_DAY, "BENCH-1",
                List.of(new PurchaseRequest.Line("FLOUR", new BigDecimal("60"), "kg", new BigDecimal("45")))), admin());
    }

    protected RunResponse createRun(LocalDate date) {
        return productionService.create(new CreateRunRequest("MURUKKU200", date, BigDecimal.ONE), admin());
    }

    protected ActualsRequest.Line actual(String itemCode, String qty, String scrap, String uom) {
        return new ActualsRequest.Line(itemCode, new BigDecimal(qty), new BigDecimal(scrap), uom);
    }

    /** Benchmark steps 2 and 3: flour 40, oil 10.5 L (as 10500 ml), spice 1.2, pouches 194 (6 damaged), cartons 9, output 188. */
    protected ActualsRequest benchmarkActuals() {
        return new ActualsRequest(new BigDecimal("188"), List.of(
                actual("FLOUR", "40", "0", "kg"),
                actual("OIL", "10500", "0", "ml"),
                actual("SPICE", "1.2", "0", "kg"),
                actual("POUCH", "194", "6", "pc"),
                actual("CARTON", "9", "0", "pc")));
    }

    /** Creates a run on RUN_DAY and enters the benchmark actuals. It is still DRAFT. */
    protected Long draftRunWithBenchmarkActuals() {
        Long runId = createRun(RUN_DAY).id();
        productionService.enterActuals(runId, benchmarkActuals(), admin());
        return runId;
    }

    protected RunResponse.Line lineOf(RunResponse run, String itemCode) {
        return run.lines().stream().filter(l -> l.itemCode().equals(itemCode)).findFirst().orElseThrow();
    }
}
