package com.ordertoaudit.audit.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.audit.entity.AuditAction;
import com.ordertoaudit.audit.entity.AuditLog;
import com.ordertoaudit.audit.repository.AuditLogRepository;
import com.ordertoaudit.masters.entity.AppUser;

/**
 * Writes audit_log rows (R14). MANDATORY: the audit row is saved in the same transaction as the action
 * it describes, so either both exist or neither does.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** before / after are small JSON-friendly maps (null when not applicable). user may be null for a failed login. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(String entityType, Long entityId, AuditAction action,
                    Map<String, Object> before, Map<String, Object> after, AppUser user) {
        AuditLog entry = new AuditLog();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setBeforeData(before);
        entry.setAfterData(after);
        entry.setUser(user);
        entry.setUsername(user == null ? null : user.getUsername());
        auditLogRepository.save(entry);
    }
}
