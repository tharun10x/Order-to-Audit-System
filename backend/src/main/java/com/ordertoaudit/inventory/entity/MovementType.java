package com.ordertoaudit.inventory.entity;

/**
 * stock_movement.movement_type (R6). Each type has a fixed direction, except ADJUSTMENT and REVERSAL,
 * which can go either way. This mirrors the database check sign_matches_type.
 */
public enum MovementType {
    OPENING(Direction.IN),
    RECEIPT(Direction.IN),
    ISSUE(Direction.OUT),
    PRODUCTION_OUTPUT(Direction.IN),
    SALE(Direction.OUT),
    RETURN(Direction.IN),
    ADJUSTMENT(null),
    REVERSAL(null);

    private final Direction fixedDirection;

    MovementType(Direction fixedDirection) {
        this.fixedDirection = fixedDirection;
    }

    public boolean allows(Direction direction) {
        return fixedDirection == null || fixedDirection == direction;
    }
}
