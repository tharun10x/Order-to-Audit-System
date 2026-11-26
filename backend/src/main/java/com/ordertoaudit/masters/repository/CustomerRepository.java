package com.ordertoaudit.masters.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordertoaudit.masters.entity.Customer;

import jakarta.persistence.LockModeType;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** SELECT ... FOR UPDATE: new orders for one customer are created one at a time (R9.7). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> lockById(@Param("id") Long id);
}
