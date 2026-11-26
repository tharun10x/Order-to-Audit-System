package com.ordertoaudit.inventory.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.inventory.entity.StockBalance;
import com.ordertoaudit.inventory.entity.StockMovement;
import com.ordertoaudit.inventory.repository.StockBalanceRepository;
import com.ordertoaudit.inventory.repository.StockMovementRepository;
import com.ordertoaudit.inventory.web.BalanceResponse;
import com.ordertoaudit.inventory.web.LedgerRowResponse;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.repository.ItemRepository;

/** Read side of the stock ledger: current balances, and the movement list with a running balance (R15). */
@Service
public class LedgerService {

    private static final Instant BEGINNING = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant FAR_FUTURE = Instant.parse("2999-12-31T00:00:00Z");

    private final ItemRepository itemRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;

    public LedgerService(ItemRepository itemRepository, StockBalanceRepository balanceRepository,
                         StockMovementRepository movementRepository) {
        this.itemRepository = itemRepository;
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> balances() {
        return balanceRepository.findAllByOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    /**
     * Movements of one item between two dates (both inclusive, factory time zone). Both dates are optional.
     * The running balance starts from everything that happened before "from".
     */
    @Transactional(readOnly = true)
    public List<LedgerRowResponse> ledger(String itemCode, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        Item item = itemRepository.findByCode(itemCode)
                .orElseThrow(() -> new BadRequestException("Unknown item code '" + itemCode + "'"));

        Instant start = (from == null) ? BEGINNING : from.atStartOfDay(Money.ZONE).toInstant();
        Instant end = (to == null) ? FAR_FUTURE : to.plusDays(1).atStartOfDay(Money.ZONE).toInstant();

        BigDecimal runningQty = movementRepository.sumQtyBefore(item.getId(), start);
        BigDecimal runningValue = movementRepository.sumValueBefore(item.getId(), start);

        List<LedgerRowResponse> rows = new ArrayList<>();
        for (StockMovement m : movementRepository.findLedger(item.getId(), start, end)) {
            runningQty = runningQty.add(m.getQty());
            runningValue = runningValue.add(m.getValueAmount());
            rows.add(new LedgerRowResponse(m.getId(), m.getMovementDate(), m.getMovementType(), m.getSourceType(),
                    m.getSourceId(), m.getQty(), m.getUnitCost(), m.getValueAmount(), runningQty, runningValue,
                    m.getRemarks()));
        }
        return rows;
    }

    private BalanceResponse toResponse(StockBalance b) {
        BigDecimal avgCost = null;
        if (b.getQtyOnHand().signum() > 0) {
            avgCost = b.getStockValue().divide(b.getQtyOnHand(), Money.RATE_SCALE, Money.ROUNDING);
        }
        return new BalanceResponse(b.getItemId(), b.getCode(), b.getName(), b.getKind(),
                b.getQtyOnHand(), b.getStockValue(), avgCost);
    }
}
