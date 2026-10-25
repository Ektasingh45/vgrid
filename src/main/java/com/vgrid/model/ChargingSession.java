package com.vgrid.model;

import java.time.LocalDateTime;

/**
 * A driver checked in at a charger ("Start charging"). It ends when they press "Done",
 * or automatically after one hour. reservationId is set when they arrived for their own booking.
 */
public record ChargingSession(int userId, String stationId, String chargerId, String reservationId,
                              LocalDateTime startedAt) {

    public static final int MAX_MINUTES = 60;

    public LocalDateTime endsAt() {
        return startedAt.plusMinutes(MAX_MINUTES);
    }
}
