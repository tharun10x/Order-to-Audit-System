package com.ordertoaudit.masters.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.masters.entity.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findAllByOrderByNameAsc();

    /** M4: another ACTIVE supplier with this name, ignoring case. id is the supplier being edited, or null. */
    @Query("""
            select count(s) > 0 from Supplier s
            where s.active = true and lower(s.name) = lower(:name) and (:id is null or s.id <> :id)""")
    boolean activeNameTaken(@Param("name") String name, @Param("id") Long id);
}
