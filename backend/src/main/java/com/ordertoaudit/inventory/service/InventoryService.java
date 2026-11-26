package com.ordertoaudit.inventory.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.InsufficientStockException;
import com.ordertoaudit.inventory.entity.Direction;
import com.ordertoaudit.inventory.entity.SourceType;
import com.ordertoaudit.inventory.entity.StockMovement;
import com.ordertoaudit.inventory.repository.BalanceSnapshot;
import com.ordertoaudit.inventory.repository.StockBalanceRepository;
import com.ordertoaudit.inventory.repository.StockMovementRepository;
import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.repository.ItemRepository;

/**
 * The ONLY class that inserts stock_movement rows (R6). Stock is never stored as a number:
 * the stock_balance view sums the ledger, and the weighted average cost is stockValue / qtyOnHand (R7.3).
 *
 * MANDATORY: it must be called from inside a posting service's transaction, so a movement can never be
 * committed without the document that caused it.
 */
@Service
public class InventoryService {

    private final ItemRepository itemRepository;
    private final StockMovementRepository movementRepository;
    private final StockBalanceRepository balanceRepository;

    public InventoryService(ItemRepository itemRepository, StockMovementRepository movementRepository,
                            StockBalanceRepository balanceRepository) {
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
        this.balanceRepository = balanceRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement post(MovementRequest request) {
        return post(List.of(request)).get(0);
    }

    /**
     * Posts the movements in the order given. Each one sees the effect of the ones before it
     * (a RECEIPT followed by an ISSUE of the same item works). If any movement fails, the caller's
     * whole transaction rolls back, so nothing is written (R6.6).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockMovement> post(List<MovementRequest> requests) {
        Map<Long, Item> lockedItems = lockItems(requests);
        List<StockMovement> posted = new ArrayList<>();
        for (MovementRequest request : requests) {
            posted.add(postOne(request, lockedItems.get(request.itemId())));
        }
        return posted;
    }

    /** R6.7: lock every item involved, in ascending id order, before reading any balance. */
    private Map<Long, Item> lockItems(List<MovementRequest> requests) {
        Set<Long> ids = new TreeSet<>();
        for (MovementRequest request : requests) {
            ids.add(request.itemId());
        }
        List<Item> items = itemRepository.lockAllByIdOrderById(ids);
        if (items.size() != ids.size()) {
            throw new BadRequestException("Unknown item id in stock movement: " + ids);
        }
        return items.stream().collect(Collectors.toMap(Item::getId, Function.identity()));
    }

    private StockMovement postOne(MovementRequest request, Item item) {
        validate(request, item);

        BalanceSnapshot balance = balanceRepository.snapshot(item.getId())
                .orElseThrow(() -> new IllegalStateException("No stock_balance row for item " + item.getCode()));
        BigDecimal qty = Money.qty(request.qty());

        BigDecimal signedQty;
        BigDecimal signedValue;
        BigDecimal unitCost;

        if (request.direction() == Direction.IN) {
            // Inbound: the value is given (bill amount); unit cost = value / qty
            BigDecimal value = Money.money(request.value());
            signedQty = qty;
            signedValue = value;
            unitCost = value.divide(qty, Money.RATE_SCALE, Money.ROUNDING);
        } else {
            BigDecimal onHand = balance.getQtyOnHand();
            BigDecimal stockValue = balance.getStockValue();
            if (qty.compareTo(onHand) > 0) {
                throw new InsufficientStockException(item.getCode(), onHand, qty);
            }
            BigDecimal value;
            if (qty.compareTo(onHand) == 0) {
                // Emptying the item: take the remaining value exactly, so no paise are left behind
                value = stockValue;
            } else {
                // Weighted average: stockValue x qty / onHand, rounded once
                value = stockValue.multiply(qty).divide(onHand, Money.MONEY_SCALE, Money.ROUNDING);
            }
            unitCost = stockValue.divide(onHand, Money.RATE_SCALE, Money.ROUNDING);
            signedQty = qty.negate();
            signedValue = value.negate();
        }

        StockMovement movement = new StockMovement();
        movement.setItem(item);
        movement.setMovementDate(request.movementDate());
        movement.setQty(signedQty);
        movement.setUnitCost(unitCost);
        movement.setValueAmount(signedValue);
        movement.setMovementType(request.type());
        movement.setSourceType(request.sourceType());
        movement.setSourceId(request.sourceId());
        movement.setRemarks(request.remarks());
        movement.setCreatedBy(request.user());
        // saveAndFlush: the row is in the database before the next movement reads the balance
        return movementRepository.saveAndFlush(movement);
    }

    private void validate(MovementRequest request, Item item) {
        if (request.qty() == null || Money.qty(request.qty()).signum() <= 0) {
            throw new BadRequestException("Movement quantity for " + item.getCode() + " must be greater than zero");
        }
        if (!request.type().allows(request.direction())) {
            throw new BadRequestException(request.type() + " cannot be an " + request.direction() + " movement");
        }
        if (request.sourceType() != SourceType.OPENING && request.sourceId() == null) {
            throw new BadRequestException("Every movement needs its source document id (R6.2)");
        }
        if (request.direction() == Direction.IN && (request.value() == null || request.value().signum() < 0)) {
            throw new BadRequestException("An inbound movement needs a value of zero or more");
        }
        if (request.movementDate() == null || request.user() == null) {
            throw new BadRequestException("A movement needs a date and a user");
        }
    }
}
