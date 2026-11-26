package com.ordertoaudit.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.inventory.entity.StockBalance;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    /**
     * Selecting plain columns (not the entity) makes Hibernate run the SQL every time,
     * so InventoryService always sees movements it inserted earlier in the same transaction.
     */
    @Query("select b.qtyOnHand as qtyOnHand, b.stockValue as stockValue from StockBalance b where b.itemId = :itemId")
    Optional<BalanceSnapshot> snapshot(@Param("itemId") Long itemId);

    List<StockBalance> findAllByOrderByCodeAsc();
}
