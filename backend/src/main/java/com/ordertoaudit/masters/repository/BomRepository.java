package com.ordertoaudit.masters.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordertoaudit.masters.entity.Bom;

public interface BomRepository extends JpaRepository<Bom, Long> {

    /** At most one active BOM per product (unique index ux_bom_one_active). */
    Optional<Bom> findByProductIdAndActiveTrue(Long productId);
}
