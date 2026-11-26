package com.ordertoaudit.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordertoaudit.audit.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
