package com.ordertoaudit.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ordertoaudit.AbstractSalesTest;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.ConflictException;
import com.ordertoaudit.common.error.ForbiddenException;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.common.error.OrderShortageException;
import com.ordertoaudit.sales.entity.OrderStatus;
import com.ordertoaudit.sales.web.CreateOrderRequest;
import com.ordertoaudit.sales.web.OrderResponse;
import com.ordertoaudit.sales.web.UpdateOrderLinesRequest;

/** R9: creating, resending, editing, approving, rejecting and cancelling orders. */
class SalesOrderServiceTest extends AbstractSalesTest {

    @BeforeEach
    void stock() {
        produceBenchmarkStock();   // 238 packets
    }

    private UpdateOrderLinesRequest newLines(String packets, String rate) {
        return new UpdateOrderLinesRequest(List.of(
                new UpdateOrderLinesRequest.Line(PRODUCT, new BigDecimal(packets), new BigDecimal(rate))));
    }

    @Test
    void aNewOrderCopiesThePriceAndBelongsToTheSalesman() {
        OrderResponse order = order("salesman_a", "RET-A", "10");

        assertThat(order.status()).isEqualTo(OrderStatus.SUBMITTED);
        assertThat(order.orderNo()).matches("SO/\\d{4}-\\d{2}/0001");
        assertThat(order.salesman()).isEqualTo("salesman_a");
        assertThat(order.lines().get(0).rate().toPlainString()).isEqualTo("50.00");   // R9.2
    }

    @Test
    void anOrderEnteredByTheAdminBelongsToTheCustomersSalesman() {
        assertThat(order("admin", "DIST-B", "10").salesman()).isEqualTo("salesman_b");
    }

    @Test
    void cartonsAreConvertedToPackets() {
        OrderResponse order = orderService.create(
                orderRequest("RET-A", UUID.randomUUID(), "3", "ctn"), user("salesman_a")).order();

        assertThat(order.lines().get(0).qty().toPlainString()).isEqualTo("60.0000");   // 3 x 20
    }

    @Test
    void partPacketsAndRepeatedProductsAreRejected() {
        assertThatThrownBy(() -> order("salesman_a", "RET-A", "1.5"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("whole number of packets");

        CreateOrderRequest twice = new CreateOrderRequest(customerId("RET-A"), UUID.randomUUID(), List.of(
                new CreateOrderRequest.Line(PRODUCT, BigDecimal.ONE, "ctn"),
                new CreateOrderRequest.Line(PRODUCT, BigDecimal.ONE, "pkt")));
        assertThatThrownBy(() -> orderService.create(twice, admin()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("more than one line");
        assertThat(rowCount("sales_order")).isZero();
    }

    @Test
    void resendingTheSameClientUuidReturnsTheSameOrder() {
        CreateOrderRequest request = orderRequest("RET-A", UUID.randomUUID(), "10", "pkt");

        var first = orderService.create(request, user("salesman_a"));
        var second = orderService.create(request, user("salesman_a"));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.order().id()).isEqualTo(first.order().id());
        assertThat(second.order().orderNo()).isEqualTo(first.order().orderNo());
        assertThat(rowCount("sales_order")).isEqualTo(1);
    }

    @Test
    void twoResendsAtTheSameMomentStillCreateOneOrder() throws Exception {
        CreateOrderRequest request = orderRequest("RET-A", UUID.randomUUID(), "10", "pkt");
        var salesman = user("salesman_a");
        Callable<Object> send = () -> orderService.create(request, salesman);

        assertThat(raceOutcomes(List.of(send, send))).containsExactly("OK", "OK");

        assertThat(rowCount("sales_order")).isEqualTo(1);
    }

    @Test
    void aClientUuidCannotBeReusedByAnotherSalesmanOrForAnotherCustomer() {
        UUID uuid = UUID.randomUUID();
        orderService.create(orderRequest("RET-A", uuid, "10", "pkt"), user("salesman_a"));

        assertThatThrownBy(() -> orderService.create(orderRequest("DIST-B", uuid, "10", "pkt"), user("salesman_b")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> orderService.create(orderRequest("DIST-B", uuid, "10", "pkt"), admin()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aSalesmanCannotOrderForSomeoneElsesCustomer() {
        assertThatThrownBy(() -> order("salesman_a", "DIST-B", "10")).isInstanceOf(ForbiddenException.class);

        assertThat(rowCount("sales_order")).isZero();
    }

    @Test
    void aSalesmanSeesOnlyTheirOwnOrders() {
        OrderResponse mine = order("salesman_a", "RET-A", "10");
        OrderResponse other = order("salesman_b", "DIST-B", "10");

        assertThat(orderService.list(null, user("salesman_a"))).extracting(OrderResponse::id).containsExactly(mine.id());
        assertThat(orderService.list(null, admin())).extracting(OrderResponse::id).containsExactly(other.id(), mine.id());
        assertThat(orderService.list(OrderStatus.APPROVED, admin())).isEmpty();
        assertThatThrownBy(() -> orderService.get(other.id(), user("salesman_a"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> orderService.stockCheck(other.id(), user("salesman_a")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void theAdminCanChangeQuantityAndPriceAndBothVersionsAreAudited() {
        OrderResponse order = order("salesman_a", "RET-A", "10");

        OrderResponse edited = orderService.updateLines(order.id(), newLines("12", "48.50"), admin());

        assertThat(edited.lines()).singleElement().satisfies(line -> {
            assertThat(line.qty().toPlainString()).isEqualTo("12.0000");
            assertThat(line.rate().toPlainString()).isEqualTo("48.50");
        });
        var audit = jdbc.queryForMap("""
                SELECT before_data -> 'lines' -> 0 ->> 'rate' AS old_rate, after_data -> 'lines' -> 0 ->> 'rate' AS new_rate
                FROM audit_log WHERE entity_type = 'SALES_ORDER' AND action = 'UPDATE'""");
        assertThat(new BigDecimal((String) audit.get("old_rate"))).isEqualByComparingTo("50");
        assertThat(new BigDecimal((String) audit.get("new_rate"))).isEqualByComparingTo("48.5");
    }

    @Test
    void anApprovedOrderCannotBeEdited() {
        OrderResponse order = approvedOrder("salesman_a", "RET-A", "10");

        assertThatThrownBy(() -> orderService.updateLines(order.id(), newLines("12", "50"), admin()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("APPROVED");
    }

    @Test
    void anApprovedOrderReservesStockForTheNextApprovalWithoutMovingIt() {
        int movementsBefore = movementCount();
        approvedOrder("salesman_a", "RET-A", "150");
        OrderResponse next = order("salesman_b", "DIST-B", "100");

        var line = orderService.stockCheck(next.id(), admin()).lines().get(0);
        assertThat(line.onHand().toPlainString()).isEqualTo("238.0000");
        assertThat(line.reserved().toPlainString()).isEqualTo("150.0000");
        assertThat(line.available().toPlainString()).isEqualTo("88.0000");
        assertThatThrownBy(() -> orderService.approve(next.id(), null, admin()))
                .isInstanceOf(OrderShortageException.class);
        assertThat(movementCount()).isEqualTo(movementsBefore);   // R9.6
    }

    @Test
    void invoicedOrdersStillReserveAndDispatchedOrdersDoNot() {
        var invoice = retailerInvoice();
        OrderResponse next = order("salesman_b", "DIST-B", "100");
        assertThat(orderService.stockCheck(next.id(), admin()).lines().get(0).reserved().toPlainString())
                .isEqualTo("150.0000");

        dispatch(invoice.id());

        var line = orderService.stockCheck(next.id(), admin()).lines().get(0);
        assertThat(line.reserved().toPlainString()).isEqualTo("0.0000");
        assertThat(line.available().toPlainString()).isEqualTo("88.0000");   // not reduced twice
    }

    @Test
    void cancellingAnApprovedOrderReleasesItsStock() {
        OrderResponse first = approvedOrder("salesman_a", "RET-A", "150");
        OrderResponse next = order("salesman_b", "DIST-B", "100");

        orderService.cancel(first.id(), admin());

        assertThat(orderService.approve(next.id(), null, admin()).status()).isEqualTo(OrderStatus.APPROVED);
    }

    @Test
    void ofTwoApprovalsThatTogetherExceedStockExactlyOneSucceeds() throws Exception {
        OrderResponse first = order("salesman_a", "RET-A", "150");
        OrderResponse second = order("salesman_b", "DIST-B", "150");
        var approver = admin();

        List<String> outcomes = raceOutcomes(List.of(
                () -> orderService.approve(first.id(), null, approver),
                () -> orderService.approve(second.id(), null, approver)));

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "OrderShortageException");
        assertThat(List.of(orderStatus(first.id()), orderStatus(second.id())))
                .containsExactlyInAnyOrder("APPROVED", "SUBMITTED");
    }

    @Test
    void theApprovedQuantityMustBeValid() {
        OrderResponse order = order("salesman_a", "RET-A", "10");

        assertThatThrownBy(() -> orderService.approve(order.id(), approving("11"), admin()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("more than ordered");
        assertThatThrownBy(() -> orderService.approve(order.id(), approving("0"), admin()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> orderService.approve(order.id(), approving("2.5"), admin()))
                .isInstanceOf(BadRequestException.class);
        assertThat(orderStatus(order.id())).isEqualTo("SUBMITTED");
    }

    @Test
    void rejectingKeepsTheReasonAndIsFinal() {
        OrderResponse order = order("salesman_a", "RET-A", "10");

        assertThatThrownBy(() -> orderService.reject(order.id(), " ", admin())).isInstanceOf(BadRequestException.class);
        OrderResponse rejected = orderService.reject(order.id(), "Credit limit", admin());

        assertThat(rejected.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejected.remarks()).isEqualTo("Credit limit");
        assertThatThrownBy(() -> orderService.approve(order.id(), null, admin())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> orderService.cancel(order.id(), admin())).isInstanceOf(ConflictException.class);
    }

    @Test
    void anInvoicedOrderCannotBeCancelledAsAnOrder() {
        var invoice = retailerInvoice();

        assertThatThrownBy(() -> orderService.cancel(invoice.orderId(), admin()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("INVOICED");
    }

    @Test
    void everyOrderStateChangeIsAudited() {
        OrderResponse order = order("salesman_a", "RET-A", "10");
        orderService.updateLines(order.id(), newLines("9", "50"), admin());
        orderService.approve(order.id(), null, admin());
        orderService.cancel(order.id(), admin());
        orderService.reject(order("salesman_a", "RET-A", "1").id(), "No", admin());

        assertThat(jdbc.queryForList("SELECT action FROM audit_log WHERE entity_type = 'SALES_ORDER' ORDER BY id",
                String.class)).containsExactly("CREATE", "UPDATE", "APPROVE", "CANCEL", "CREATE", "REJECT");
    }
}
