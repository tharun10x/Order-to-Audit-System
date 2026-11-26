package com.ordertoaudit.production.entity;

/** production_run.status. DRAFT -> POSTED -> REVERSED, or DRAFT -> CANCELLED (R8). */
public enum RunStatus {
    DRAFT,
    POSTED,
    CANCELLED,
    REVERSED
}
