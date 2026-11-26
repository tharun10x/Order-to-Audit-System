package com.ordertoaudit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;

import com.ordertoaudit.billing.service.DispatchService;
import com.ordertoaudit.billing.service.InvoicePdfService;
import com.ordertoaudit.billing.service.InvoiceService;
import com.ordertoaudit.billing.web.DispatchRequest;
import com.ordertoaudit.billing.web.DispatchResponse;
import com.ordertoaudit.billing.web.InvoiceResponse;
import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.repository.AppUserRepository;
import com.ordertoaudit.payments.entity.PaymentMode;
import com.ordertoaudit.payments.service.PaymentService;
import com.ordertoaudit.payments.web.PaymentRequest;
import com.ordertoaudit.payments.web.PaymentResponse;
import com.ordertoaudit.sales.service.SalesOrderService;
import com.ordertoaudit.sales.web.ApproveOrderRequest;
import com.ordertoaudit.sales.web.CreateOrderRequest;
import com.ordertoaudit.sales.web.OrderResponse;

/** Shared helpers for sales tests. Starts where the production benchmark ends: 238 packets in stock. */
public abstract class AbstractSalesTest extends AbstractProductionTest {

    protected static final String PRODUCT = "MURUKKU200";
    protected static final LocalDate INVOICE_DAY = LocalDate.of(2026, 11, 5);
    protected static final LocalDate DISPATCH_DAY = LocalDate.of(2026, 11, 6);
    protected static final LocalDate PAYMENT_DAY = LocalDate.of(2026, 11, 10);
    protected static final LocalDate CREDIT_NOTE_DAY = LocalDate.of(2026, 11, 12);

    @Autowired
    protected SalesOrderService orderService;

    @Autowired
    protected InvoiceService invoiceService;

    @Autowired
    protected InvoicePdfService pdfService;

    @Autowired
    protected DispatchService dispatchService;

    @Autowired
    protected PaymentService paymentService;

    @Autowired
    private AppUserRepository users;

    /** Benchmark steps 1 to 3: buy the flour and post the production run. */
    protected void produceBenchmarkStock() {
        purchaseSixtyKgFlour();
        productionService.post(draftRunWithBenchmarkActuals(), admin());
    }

    protected AppUser user(String username) {
        return users.findByUsername(username).orElseThrow();
    }

    protected Long customerId(String code) {
        return jdbc.queryForObject("SELECT id FROM customer WHERE code = ?", Long.class, code);
    }

    protected CreateOrderRequest orderRequest(String customerCode, UUID clientUuid, String qty, String uom) {
        return new CreateOrderRequest(customerId(customerCode), clientUuid,
                List.of(new CreateOrderRequest.Line(PRODUCT, new BigDecimal(qty), uom)));
    }

    /** An order in packets, entered by the given user. */
    protected OrderResponse order(String username, String customerCode, String packets) {
        return orderService.create(orderRequest(customerCode, UUID.randomUUID(), packets, "pkt"), user(username)).order();
    }

    protected ApproveOrderRequest approving(String packets) {
        return new ApproveOrderRequest(List.of(new ApproveOrderRequest.Line(PRODUCT, new BigDecimal(packets))));
    }

    protected OrderResponse approvedOrder(String username, String customerCode, String packets) {
        return orderService.approve(order(username, customerCode, packets).id(), null, admin());
    }

    protected InvoiceResponse invoice(Long orderId) {
        return invoiceService.createFromOrder(orderId, INVOICE_DAY, admin());
    }

    /** Benchmark step 4 and the invoice of step 5: Retailer A, 150 packets. */
    protected InvoiceResponse retailerInvoice() {
        return invoice(approvedOrder("salesman_a", "RET-A", "150").id());
    }

    protected DispatchResponse dispatch(Long invoiceId) {
        return dispatchService.dispatch(new DispatchRequest(invoiceId, DISPATCH_DAY, "TN45 AB 1234"), admin());
    }

    protected PaymentRequest.Allocation allocation(Long invoiceId, String amount) {
        return new PaymentRequest.Allocation(invoiceId, new BigDecimal(amount));
    }

    protected PaymentRequest paymentRequest(String customerCode, String amount, PaymentRequest.Allocation... parts) {
        return new PaymentRequest(customerId(customerCode), PAYMENT_DAY, new BigDecimal(amount), PaymentMode.UPI,
                "UPI-001", List.of(parts));
    }

    /** One payment settling part or all of one invoice. */
    protected PaymentResponse pay(String customerCode, Long invoiceId, String amount) {
        return paymentService.record(paymentRequest(customerCode, amount, allocation(invoiceId, amount)), admin());
    }

    protected String orderStatus(Long orderId) {
        return jdbc.queryForObject("SELECT status FROM sales_order WHERE id = ?", String.class, orderId);
    }

    protected int rowCount(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    /** The text of a PDF, as a reader would see it. */
    protected static String pdfText(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Starts the tasks at the same moment on separate threads and returns one result per task:
     * "OK", or the simple name of the exception it threw.
     */
    protected static List<String> raceOutcomes(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    task.call();
                    return "OK";
                } catch (RuntimeException e) {
                    return e.getClass().getSimpleName();
                }
            }));
        }
        ready.await();
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return outcomes;
    }
}
