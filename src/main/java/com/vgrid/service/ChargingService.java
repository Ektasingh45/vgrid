package com.vgrid.service;

import com.vgrid.dao.ChargingDao;
import com.vgrid.dao.ReservationDao;
import com.vgrid.model.*;
import com.vgrid.util.Format;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Check-in / check-out at a charger. While a driver is checked in, the charger shows as "In use"
 * for everyone. If they arrive for their own booking, finishing completes that booking.
 */
public class ChargingService {
    private final ChargingDao dao;
    private final ReservationDao reservations;
    private final StationService stations;
    private final ReservationService reservationService;
    private final NotificationService notifications;
    private final UserSession session;

    public ChargingService(ChargingDao dao, ReservationDao reservations, StationService stations,
                           ReservationService reservationService, NotificationService notifications,
                           UserSession session) {
        this.dao = dao;
        this.reservations = reservations;
        this.stations = stations;
        this.reservationService = reservationService;
        this.notifications = notifications;
        this.session = session;
    }

    /** The signed-in user's current check-in, if any. */
    public Optional<ChargingSession> getActiveSession() {
        return dao.findActiveSession(session.getUserId(), LocalDateTime.now());
    }

    /** Throws IllegalArgumentException with a message the screen can show. */
    public void startCharging(Station station, Charger charger) {
        LocalDateTime now = LocalDateTime.now();
        if (getActiveSession().isPresent()) {
            throw new IllegalArgumentException("You are already charging. Press Done there first.");
        }
        if (!charger.isWorking()) {
            throw new IllegalArgumentException("This charger is out of service.");
        }
        if (stations.ocppStateAt(station, charger) != null) {
            throw new IllegalArgumentException("Charger " + charger.getId()
                    + " reports its own status over OCPP - just plug in, no check-in needed.");
        }
        // Someone else's booking for this hour has priority.
        Optional<BookingRef> booking = reservations.findBooking(station.getId(), charger.getId(),
                now.toLocalDate(), now.toLocalTime().truncatedTo(ChronoUnit.HOURS));
        if (booking.isPresent() && booking.get().userId() != session.getUserId()) {
            throw new IllegalArgumentException("Charger " + charger.getId()
                    + " is reserved by another driver for this hour. Please use another charger.");
        }
        String ownBooking = booking.map(BookingRef::reservationId).orElse(null);

        // The database refuses a second active session on the same charger (ConflictException).
        dao.startSession(new ChargingSession(session.getUserId(), station.getId(), charger.getId(), ownBooking, now));
        notifications.logActivity("Started charging at " + station.getName(),
                "Charger " + charger.getId() + " · " + charger.getConnectorType()
                        + (ownBooking == null ? "" : " · " + ownBooking));
        stations.reload();
        reservationService.reload();
    }

    /** "Done". Returns a short summary for a message, or null if the user was not charging. */
    public String stopCharging() {
        LocalDateTime now = LocalDateTime.now();
        Optional<ChargingSession> ended = dao.endSession(session.getUserId(), now);
        if (ended.isEmpty()) {
            return null;
        }
        ChargingSession s = ended.get();
        long minutes = Math.max(1, Duration.between(s.startedAt(), now).toMinutes());
        Station station = stations.findStation(s.stationId());
        String name = station == null ? s.stationId() : station.getName();
        if (s.reservationId() != null) {
            reservations.updateStatus(s.reservationId(), ReservationStatus.COMPLETED,
                    "Charged for " + minutes + " min, finished " + Format.dateTime(now) + ".");
        }
        notifications.logActivity("Finished charging at " + name,
                "Charger " + s.chargerId() + " · " + minutes + " min");
        stations.reload();
        reservationService.reload();
        return "Charging finished at " + name + " (" + minutes + " min)";
    }
}
