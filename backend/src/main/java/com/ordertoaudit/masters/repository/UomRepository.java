package com.ordertoaudit.masters.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordertoaudit.masters.entity.Uom;

public interface UomRepository extends JpaRepository<Uom, Long> {

    Optional<Uom> findByCode(String code);
}
