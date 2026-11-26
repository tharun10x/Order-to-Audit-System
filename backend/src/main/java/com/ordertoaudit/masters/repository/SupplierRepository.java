package com.ordertoaudit.masters.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordertoaudit.masters.entity.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
}
