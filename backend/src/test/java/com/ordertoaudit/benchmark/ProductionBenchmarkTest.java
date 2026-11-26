package com.ordertoaudit.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.ordertoaudit.AbstractProductionTest;
import com.ordertoaudit.production.entity.OverheadType;
import com.ordertoaudit.production.entity.RunStatus;
import com.ordertoaudit.production.web.OverheadRequest;
import com.ordertoaudit.production.web.RunResponse;

/**
 * Problem statement section 5, steps 1 to 3 and 8, against the expected results in business-rules.md section 16.
 * Every test starts from the seed, buys the 60 kg of flour, and posts the benchmark run.
 */
class ProductionBenchmarkTest extends AbstractProductionTest {

    private RunResponse postBenchmarkRun() {
        purchaseSixtyKgFlour();
        Long runId = draftRunWithBenchmarkActuals();
        return productionService.post(runId, admin());
    }

    @Test
    void runIsPostedWithTheFirstRunNumberOfTheYear() {
        RunResponse run = postBenchmarkRun();

        assertThat(run.runNo()).isEqualTo("RUN/2026-27/0001");
        assertThat(run.status()).isEqualTo(RunStatus.POSTED);
        assertThat(run.postedBy()).isEqualTo("admin");
        assertThat(run.postedAt()).isNotNull();
    }

    @Test
    void closingStockMatchesTheBrief() {
        postBenchmarkRun();

        assertThat(qtyOnHand("FLOUR").toPlainString()).isEqualTo("120.0000");   // 100 + 60 - 40
        assertThat(qtyOnHand("OIL").toPlainString()).isEqualTo("19.5000");      // 30 - 10.5
        assertThat(qtyOnHand("SPICE").toPlainString()).isEqualTo("3.8000");     // 5 - 1.2
        assertThat(qtyOnHand("POUCH").toPlainString()).isEqualTo("306.0000");   // 500 - 194
        assertThat(qtyOnHand("CARTON").toPlainString()).isEqualTo("21.0000");   // 30 - 9
        assertThat(qtyOnHand("MURUKKU200").toPlainString()).isEqualTo("238.0000");   // 50 + 188
    }

    @Test
    void finishedGoodsEnterStockAtMaterialPlusPackagingCost() {
        postBenchmarkRun();

        // Opening 50 x 30.00 = 1500.00, plus the run: 3435.00 + 620.00 = 4055.00 (overheads are not in stock value)
        assertThat(stockValue("MURUKKU200").toPlainString()).isEqualTo("5555.00");
        BigDecimal outputValue = jdbc.queryForObject(
                "SELECT value FROM stock_movement WHERE movement_type = 'PRODUCTION_OUTPUT'", BigDecimal.class);
        assertThat(outputValue.toPlainString()).isEqualTo("4055.00");
    }

    @Test
    void variancesAndFlagsMatchTheBrief() {
        RunResponse run = postBenchmarkRun();

        assertThat(run.tolerancePct().toPlainString()).isEqualTo("3.00");

        assertThat(lineOf(run, "OIL").variancePct().toPlainString()).isEqualTo("5.00");
        assertThat(lineOf(run, "OIL").flagged()).isTrue();

        assertThat(lineOf(run, "POUCH").plannedQty().toPlainString()).isEqualTo("188.0000");   // judged against 188 packed
        assertThat(lineOf(run, "POUCH").actualQty().toPlainString()).isEqualTo("194.0000");    // 188 + 6 damaged
        assertThat(lineOf(run, "POUCH").scrapQty().toPlainString()).isEqualTo("6.0000");
        assertThat(lineOf(run, "POUCH").variancePct().toPlainString()).isEqualTo("3.19");
        assertThat(lineOf(run, "POUCH").flagged()).isTrue();

        assertThat(lineOf(run, "CARTON").plannedQty().toPlainString()).isEqualTo("9.0000");    // whole cartons: floor(9.4)
        for (String code : new String[] {"FLOUR", "SPICE", "CARTON"}) {
            assertThat(lineOf(run, code).variancePct().toPlainString()).as(code).isEqualTo("0.00");
            assertThat(lineOf(run, code).flagged()).as(code).isFalse();
        }

        assertThat(run.plannedOutput().toPlainString()).isEqualTo("200.0000");
        assertThat(run.actualOutput().toPlainString()).isEqualTo("188.0000");
        assertThat(run.yieldVariancePct().toPlainString()).isEqualTo("-6.00");
        assertThat(run.yieldFlagged()).isTrue();
    }

    @Test
    void linesRememberTheAverageCostTheyWereIssuedAt() {
        RunResponse run = postBenchmarkRun();

        assertThat(lineOf(run, "FLOUR").unitCost().toPlainString()).isEqualTo("43.1250");   // (100 x 42 + 60 x 45) / 160
        assertThat(lineOf(run, "OIL").unitCost().toPlainString()).isEqualTo("140.0000");
        assertThat(lineOf(run, "SPICE").unitCost().toPlainString()).isEqualTo("200.0000");
        assertThat(lineOf(run, "POUCH").unitCost().toPlainString()).isEqualTo("2.5000");
        assertThat(lineOf(run, "CARTON").unitCost().toPlainString()).isEqualTo("15.0000");
    }

    @Test
    void costsMatchTheBriefAfterLabourAndGasAreAdded() {
        RunResponse posted = postBenchmarkRun();

        // Right after posting: material and packaging only
        assertThat(posted.costing().materialCost().toPlainString()).isEqualTo("3435.00");    // 1725 + 1470 + 240
        assertThat(posted.costing().packagingCost().toPlainString()).isEqualTo("620.00");    // 485 + 135
        assertThat(posted.costing().overheadTotal().toPlainString()).isEqualTo("0.00");

        // Benchmark step 8, after posting
        productionService.addOverhead(posted.id(),
                new OverheadRequest(OverheadType.LABOUR, new BigDecimal("1200"), "Labour"), admin());
        RunResponse costed = productionService.addOverhead(posted.id(),
                new OverheadRequest(OverheadType.POWER_GAS, new BigDecimal("600"), "Gas and power"), admin());

        RunResponse.Costing costing = costed.costing();
        assertThat(costing.materialCost().toPlainString()).isEqualTo("3435.00");
        assertThat(costing.packagingCost().toPlainString()).isEqualTo("620.00");
        assertThat(costing.overheadsByType().get(OverheadType.LABOUR).toPlainString()).isEqualTo("1200.00");
        assertThat(costing.overheadsByType().get(OverheadType.POWER_GAS).toPlainString()).isEqualTo("600.00");
        assertThat(costing.overheadsByType().get(OverheadType.OTHER).toPlainString()).isEqualTo("0.00");
        assertThat(costing.overheadTotal().toPlainString()).isEqualTo("1800.00");
        assertThat(costing.totalCost().toPlainString()).isEqualTo("5855.00");

        assertThat(costing.costPerPacket().toPlainString()).isEqualTo("31.1436");            // 5855 / 188
        assertThat(costing.costPerPacketRounded().toPlainString()).isEqualTo("31.14");

        // Margin from the full-precision cost (R4.4): 50.00 - 31.1436 = 18.8564, which is 37.71 %
        assertThat(costing.sellingPrice().toPlainString()).isEqualTo("50.00");
        assertThat(costing.marginPerPacket().toPlainString()).isEqualTo("18.8564");
        assertThat(costing.marginPerPacketRounded().toPlainString()).isEqualTo("18.86");
        assertThat(costing.marginPct().toPlainString()).isEqualTo("37.71");

        // Overheads never touch stock value
        assertThat(stockValue("MURUKKU200").toPlainString()).isEqualTo("5555.00");
    }
}
