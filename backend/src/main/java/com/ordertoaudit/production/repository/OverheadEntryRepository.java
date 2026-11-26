package com.ordertoaudit.production.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordertoaudit.production.entity.OverheadEntry;

public interface OverheadEntryRepository extends JpaRepository<OverheadEntry, Long> {

    List<OverheadEntry> findByRunIdOrderById(Long runId);
}
