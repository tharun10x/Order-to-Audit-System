package com.ordertoaudit.inventory.entity;

/** Whether a movement adds stock (IN) or removes it (OUT). The ledger stores it as the sign of qty. */
public enum Direction {
    IN,
    OUT
}
