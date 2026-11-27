package com.ordertoaudit.masters.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.masters.entity.Item;
import com.ordertoaudit.masters.entity.ItemKind;

import jakarta.persistence.LockModeType;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByCode(String code);

    List<Item> findByKindAndActiveTrueOrderByNameAsc(ItemKind kind);

    List<Item> findAllByOrderByKindAscCodeAsc();

    boolean existsByCode(String code);

    /** M7: has the item ever moved? SQL, so the masters package needs no inventory classes. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM stock_movement WHERE item_id = :itemId)", nativeQuery = true)
    boolean hasStockMovements(@Param("itemId") Long itemId);

    /**
     * R6.7: locks the item rows (SELECT ... FOR UPDATE). The ORDER BY makes every transaction
     * take the locks in ascending id order, so two transactions cannot deadlock on each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id in :ids order by i.id asc")
    List<Item> lockAllByIdOrderById(@Param("ids") Collection<Long> ids);
}
