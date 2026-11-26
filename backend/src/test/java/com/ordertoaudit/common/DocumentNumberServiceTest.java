package com.ordertoaudit.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import com.ordertoaudit.AbstractLedgerTest;
import com.ordertoaudit.common.numbering.DocType;
import com.ordertoaudit.common.numbering.DocumentNumberService;

/** Gap-free numbers per document type per financial year (R10.2, R8). */
class DocumentNumberServiceTest extends AbstractLedgerTest {

    private static final LocalDate NOV_2026 = LocalDate.of(2026, 11, 3);

    @Autowired
    DocumentNumberService numbers;

    private String next(DocType type, LocalDate date) {
        return tx.execute(status -> numbers.next(type, date));
    }

    @Test
    void financialYearRunsFromAprilToMarch() {
        assertThat(DocumentNumberService.financialYear(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
        assertThat(DocumentNumberService.financialYear(LocalDate.of(2027, 3, 31))).isEqualTo("2026-27");
        assertThat(DocumentNumberService.financialYear(LocalDate.of(2027, 4, 1))).isEqualTo("2027-28");
        assertThat(DocumentNumberService.financialYear(LocalDate.of(2099, 12, 31))).isEqualTo("2099-00");
    }

    @Test
    void numbersAreConsecutiveAndEachDocumentTypeHasItsOwnCounter() {
        assertThat(next(DocType.INVOICE, NOV_2026)).isEqualTo("INV/2026-27/0001");
        assertThat(next(DocType.INVOICE, NOV_2026)).isEqualTo("INV/2026-27/0002");
        assertThat(next(DocType.CREDIT_NOTE, NOV_2026)).isEqualTo("CN/2026-27/0001");
        assertThat(next(DocType.PRODUCTION_RUN, NOV_2026)).isEqualTo("RUN/2026-27/0001");
        assertThat(next(DocType.INVOICE, NOV_2026)).isEqualTo("INV/2026-27/0003");
    }

    @Test
    void severalNumbersInOneTransactionAreConsecutive() {
        List<String> issued = tx.execute(status -> List.of(
                numbers.next(DocType.INVOICE, NOV_2026), numbers.next(DocType.INVOICE, NOV_2026)));

        assertThat(issued).containsExactly("INV/2026-27/0001", "INV/2026-27/0002");
        assertThat(next(DocType.INVOICE, NOV_2026)).isEqualTo("INV/2026-27/0003");
    }

    @Test
    void aNewFinancialYearGetsItsCounterRowOnDemandStartingAtOne() {
        assertThat(next(DocType.INVOICE, LocalDate.of(2027, 4, 1))).isEqualTo("INV/2027-28/0001");
        assertThat(next(DocType.INVOICE, LocalDate.of(2027, 4, 2))).isEqualTo("INV/2027-28/0002");
        assertThat(next(DocType.INVOICE, LocalDate.of(2027, 3, 31))).isEqualTo("INV/2026-27/0001");   // the old year is untouched

        assertThat(jdbc.queryForObject(
                "SELECT prefix FROM invoice_sequence WHERE doc_type = 'INVOICE' AND financial_year = '2027-28'", String.class))
                .isEqualTo("INV");
    }

    @Test
    void aRolledBackTransactionLeavesNoGap() {
        assertThat(next(DocType.INVOICE, NOV_2026)).isEqualTo("INV/2026-27/0001");

        assertThatThrownBy(() -> tx.execute(status -> {
            numbers.next(DocType.INVOICE, NOV_2026);   // would be 0002
            throw new IllegalStateException("document failed after taking a number");
        })).isInstanceOf(IllegalStateException.class);

        // 0002 was never used, so it is handed out again (a database SEQUENCE would have skipped it)
        assertThat(next(DocType.INVOICE, NOV_2026)).isEqualTo("INV/2026-27/0002");
    }

    @Test
    void aNumberCannotBeTakenOutsideATransaction() {
        assertThatThrownBy(() -> numbers.next(DocType.INVOICE, NOV_2026))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void concurrentTransactionsNeverGetTheSameNumber() throws Exception {
        // 2028-29 has no counter row yet, so this also races the creation of the row
        LocalDate newYear = LocalDate.of(2028, 5, 1);
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return next(DocType.INVOICE, newYear);
            }));
        }
        ready.await();
        go.countDown();

        List<String> issued = new ArrayList<>();
        for (Future<String> future : futures) {
            issued.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        Collections.sort(issued);

        assertThat(issued).containsExactly("INV/2028-29/0001", "INV/2028-29/0002", "INV/2028-29/0003",
                "INV/2028-29/0004", "INV/2028-29/0005", "INV/2028-29/0006");
    }
}
