package com.vgrid.model;

/** Just enough about a booking to alert its owner, e.g. when the charger is reported broken. */
public record BookingRef(String reservationId, int userId) {
}
