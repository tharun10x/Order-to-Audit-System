package com.ordertoaudit.production.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.production.entity.ProductionRun;

import jakarta.persistence.LockModeType;

public interface ProductionRunRepository extends JpaRepository<ProductionRun, Long> {

    /** SELECT ... FOR UPDATE: only one transaction at a time can change (or post) this run. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProductionRun r where r.id = :id")
    Optional<ProductionRun> lockById(@Param("id") Long id);

    List<ProductionRun> findByRunDateBetweenOrderByRunDateAscIdAsc(LocalDate from, LocalDate to);
}
