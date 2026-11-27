package com.ordertoaudit.masters.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.masters.entity.Bom;

public interface BomRepository extends JpaRepository<Bom, Long> {

    /** At most one active BOM per product (unique index ux_bom_one_active). */
    Optional<Bom> findByProductIdAndActiveTrue(Long productId);

    /** All versions of a product's recipe, newest first. */
    List<Bom> findByProductIdOrderByVersionDesc(Long productId);

    @Query("select coalesce(max(b.version), 0) from Bom b where b.product.id = :productId")
    int lastVersion(@Param("productId") Long productId);

    /** M7: an item in any recipe version (quantities there are in its base unit). */
    @Query("select count(l) > 0 from BomLine l where l.item.id = :itemId")
    boolean isUsedInAnyBom(@Param("itemId") Long itemId);

    /** M10: the products whose ACTIVE recipe uses the item. */
    @Query("""
            select distinct b.product.code from BomLine l join l.bom b
            where l.item.id = :itemId and b.active = true order by b.product.code""")
    List<String> productsWhoseActiveBomUses(@Param("itemId") Long itemId);
}
