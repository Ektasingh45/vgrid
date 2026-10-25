package com.vgrid.model;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * What an OCPP-connected charger last reported to the VGrid OCPP server.
 * status is the OCPP 1.6 connector status, e.g. "Available", "Charging", "Faulted".
 */
public record OcppChargerState(String ocppId, String stationId, String chargerId, boolean connected,
                               String status, String errorCode, LocalDateTime lastSeen,
                               boolean transactionActive, double energyKwh) {

    /** A charger that has not been heard from for this long is treated as offline. */
    public static final int OFFLINE_AFTER_SECONDS = 180;

    private static final Set<String> BUSY = Set.of("Preparing", "Charging", "SuspendedEV", "SuspendedEVSE", "Finishing");

    /** Connected and sent something (e.g. a heartbeat) recently. */
    public boolean isOnline(LocalDateTime now) {
        return connected && lastSeen != null && lastSeen.isAfter(now.minusSeconds(OFFLINE_AFTER_SECONDS));
    }

    /** A car is plugged in or charging. */
    public boolean isBusy() {
        return transactionActive || (status != null && BUSY.contains(status));
    }
}
