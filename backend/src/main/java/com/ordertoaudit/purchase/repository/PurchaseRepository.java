package com.ordertoaudit.purchase.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordertoaudit.purchase.entity.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    boolean existsBySupplierIdAndBillNo(Long supplierId, String billNo);

    List<Purchase> findAllByOrderByPurchaseDateDescIdDesc();
}
