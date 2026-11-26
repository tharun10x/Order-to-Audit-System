package com.ordertoaudit.sales.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.inventory.repository.StockBalanceRepository;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.repository.BomRepository;
import com.ordertoaudit.sales.repository.SalesOrderRepository;
import com.ordertoaudit.sales.web.StockCheckResponse;

/** R9.4: one calculation of availability, used both to show it and to enforce it at approval. */
@Service
public class StockCheckService {

    private final StockBalanceRepository balanceRepository;
    private final SalesOrderRepository orderRepository;
    private final BomRepository bomRepository;

    public StockCheckService(StockBalanceRepository balanceRepository, SalesOrderRepository orderRepository,
                             BomRepository bomRepository) {
        this.balanceRepository = balanceRepository;
        this.orderRepository = orderRepository;
        this.bomRepository = bomRepository;
    }

    /**
     * available = on hand - reserved by other orders; shortage = max(0, wanted - available).
     * To act on the answer, the caller must already hold the item lock (R6.7).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockCheckResponse.Line check(Item item, BigDecimal wanted, Long orderId) {
        BigDecimal onHand = Money.qty(balanceRepository.snapshot(item.getId())
                .orElseThrow(() -> new IllegalStateException("No stock_balance row for item " + item.getCode()))
                .getQtyOnHand());
        BigDecimal reserved = Money.qty(orderRepository.reservedQty(item.getId(), orderId));
        BigDecimal available = onHand.subtract(reserved);
        BigDecimal shortage = Money.qty(wanted.subtract(available).max(BigDecimal.ZERO));
        return new StockCheckResponse.Line(item.getCode(), wanted, onHand, reserved, available, shortage,
                batchesNeeded(item, shortage));
    }

    /** R9.5: ceil(shortage / expected yield of one batch). */
    private Integer batchesNeeded(Item item, BigDecimal shortage) {
        if (shortage.signum() == 0) {
            return 0;
        }
        return bomRepository.findByProductIdAndActiveTrue(item.getId())
                .map(bom -> shortage.divide(bom.getOutputQty(), 0, RoundingMode.CEILING).intValueExact())
                .orElse(null);
    }
}
