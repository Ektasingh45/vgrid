package com.vgrid.dao;

import com.vgrid.model.ChargingSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Check-ins: "Start charging" and "Done". Sessions older than one hour end automatically.
 * A charger can have only one active session, and a user can charge at only one charger at a time.
 */
public interface ChargingDao {

    /** All active sessions right now (any user). */
    List<ChargingSession> findActiveSessions(LocalDateTime now);

    Optional<ChargingSession> findActiveSession(int userId, LocalDateTime now);

    /** Throws ConflictException if the charger is already in use or the user is already charging. */
    void startSession(ChargingSession session);

    /** Ends the user's active session. Returns it, or empty if there was none. */
    Optional<ChargingSession> endSession(int userId, LocalDateTime now);
}
