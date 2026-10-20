package com.vgrid.model;

public enum ReservationStatus {
    CONFIRMED("Confirmed"),
    ACTION_REQUIRED("Action required"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String displayName;

    ReservationStatus(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
