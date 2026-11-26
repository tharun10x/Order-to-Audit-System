package com.ordertoaudit.audit.entity;

/** audit_log.action: must match the CHECK constraint in V1 (R14.1). */
public enum AuditAction {
    CREATE,
    UPDATE,
    APPROVE,
    REJECT,
    CANCEL,
    POST,
    REVERSE,
    DISPATCH,
    PAYMENT,
    LOGIN,
    LOGIN_FAILED
}
