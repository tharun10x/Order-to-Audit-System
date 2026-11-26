package com.ordertoaudit.inventory.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.inventory.entity.MovementType;
import com.ordertoaudit.inventory.entity.SourceType;
import com.ordertoaudit.inventory.entity.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("""
            select m from StockMovement m
            where m.item.id = :itemId and m.movementDate >= :from and m.movementDate < :to
            order by m.movementDate asc, m.id asc""")
    List<StockMovement> findLedger(@Param("itemId") Long itemId, @Param("from") Instant from, @Param("to") Instant to);

    /** Total value per item of the movements one document caused (used for the run cost report). */
    @Query("""
            select m.item.id as itemId, sum(m.valueAmount) as total from StockMovement m
            where m.sourceType = :sourceType and m.sourceId = :sourceId and m.movementType = :movementType
            group by m.item.id""")
    List<ItemValue> sumValueByItem(@Param("sourceType") SourceType sourceType, @Param("sourceId") Long sourceId,
                                   @Param("movementType") MovementType movementType);

    @Query("select coalesce(sum(m.qty), 0) from StockMovement m where m.item.id = :itemId and m.movementDate < :before")
    BigDecimal sumQtyBefore(@Param("itemId") Long itemId, @Param("before") Instant before);

    @Query("select coalesce(sum(m.valueAmount), 0) from StockMovement m where m.item.id = :itemId and m.movementDate < :before")
    BigDecimal sumValueBefore(@Param("itemId") Long itemId, @Param("before") Instant before);
}
