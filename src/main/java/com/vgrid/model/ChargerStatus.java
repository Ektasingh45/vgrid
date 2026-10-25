package com.vgrid.model;

/**
 * Status of a single charger.
 * AVAILABLE and OUT_OF_SERVICE are stored in the database (from Open Charge Map or user reports);
 * RESERVED and IN_USE are worked out live from VGrid bookings and charging check-ins.
 */
public enum ChargerStatus {
    AVAILABLE("Available"),
    RESERVED("Reserved now"),
    IN_USE("In use"),
    OUT_OF_SERVICE("Out of service");

    private final String displayName;

    ChargerStatus(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
