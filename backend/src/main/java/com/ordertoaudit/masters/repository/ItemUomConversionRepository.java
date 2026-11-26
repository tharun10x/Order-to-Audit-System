package com.ordertoaudit.masters.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordertoaudit.masters.entity.ItemUomConversion;

public interface ItemUomConversionRepository extends JpaRepository<ItemUomConversion, Long> {

    Optional<ItemUomConversion> findByItemIdAndUomId(Long itemId, Long uomId);
}
