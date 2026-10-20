package com.vgrid.model;

/** Live status of a single charger (simulated in this demo). */
public enum ChargerStatus {
    AVAILABLE("Available"),
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
