package com.vgrid.service;

import com.vgrid.dao.ReservationDao;
import com.vgrid.model.*;
import com.vgrid.util.Format;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Creates, cancels and updates the signed-in user's reservations.
 * Every change is saved through the ReservationDao first; then the list is re-read with reload().
 */
public class ReservationService {
    public static final int MAX_DAYS_AHEAD = 14;

    public static final List<String> TIME_SLOTS = List.of(
            "08:00 – 09:00", "09:00 – 10:00", "10:00 – 11:00", "11:00 – 12:00",
            "12:00 – 13:00", "13:00 – 14:00", "14:00 – 15:00", "15:00 – 16:00",
            "16:00 – 17:00", "17:00 – 18:00", "18:00 – 19:00", "19:00 – 20:00",
            "20:00 – 21:00");

    private final ReservationDao dao;
    private final StationService stations;
    private final RecommendationService recommendations;
    private final NotificationService notifications;
    private final UserSession session;
    private List<Reservation> reservations = List.of();

    public ReservationService(ReservationDao dao, StationService stations, RecommendationService recommendations,
                              NotificationService notifications, UserSession session) {
        this.dao = dao;
        this.stations = stations;
        this.recommendations = recommendations;
        this.notifications = notifications;
        this.session = session;
        reload();
    }

    /**
     * Re-reads the user's reservations (bookings whose slot has ended become Completed first).
     * Call StationService.reload() first if stations changed too.
     */
    public void reload() {
        dao.completeEndedBookings(LocalDateTime.now());
        reservations = dao.findReservations(session.getUserId(), stations::findStation);
    }

    // ------------------------------------------------------------------ queries

    /** Active reservations first (soonest first), then past/cancelled (newest first). */
    public List<Reservation> getAllReservations() {
        Comparator<Reservation> soonest = Comparator.comparing(Reservation::getDate)
                .thenComparing(Reservation::getSlotStart);
        List<Reservation> result = new ArrayList<>(reservations.stream()
                .filter(Reservation::isActive).sorted(soonest).toList());
        result.addAll(reservations.stream()
                .filter(r -> !r.isActive()).sorted(soonest.reversed()).toList());
        return result;
    }

    public List<Reservation> getByStatus(ReservationStatus... statuses) {
        List<ReservationStatus> wanted = List.of(statuses);
        return getAllReservations().stream().filter(r -> wanted.contains(r.getStatus())).toList();
    }

    public boolean hasAnyReservations() {
        return !reservations.isEmpty();
    }

    public long countActive() {
        return reservations.stream().filter(Reservation::isActive).count();
    }

    public long countNeedingAttention() {
        return reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.ACTION_REQUIRED).count();
    }

    public Optional<Reservation> getNextReservation() {
        return getAllReservations().stream().filter(Reservation::isActive).findFirst();
    }

    public Reservation findById(String id) {
        return reservations.stream()
                .filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }

    /** A slot is unavailable when it is in the past or when any driver already holds it. */
    public boolean isSlotAvailable(Station station, Charger charger, LocalDate date, String slot) {
        if (isSlotInPast(date, slot)) return false;
        return !dao.isSlotTaken(station.getId(), charger.getId(), date, slot);
    }

    public boolean isSlotInPast(LocalDate date, String slot) {
        LocalTime start = Reservation.slotStart(slot);
        return date.isBefore(LocalDate.now())
                || (date.equals(LocalDate.now()) && !start.isAfter(LocalTime.now()));
    }

    /**
     * Compatible stations for a reservation whose charger failed, keeping only those whose best
     * charger is still free in the same date and time slot.
     */
    public List<RecommendationResult> findAlternatives(Reservation reservation, int limit) {
        return recommendations.findAlternatives(reservation, Integer.MAX_VALUE).stream()
                .filter(r -> !dao.isSlotTaken(r.station().getId(), r.bestCharger().getId(),
                        reservation.getDate(), reservation.getTimeSlot()))
                .limit(limit)
                .toList();
    }

    // ------------------------------------------------------------------ actions

    /** Validates the booking and saves it. Throws IllegalArgumentException with a friendly message. */
    public Reservation createReservation(Station station, Charger charger, LocalDate date, String slot) {
        if (station == null || charger == null) {
            throw new IllegalArgumentException("Please choose a charger.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Please choose a reservation date.");
        }
        if (date.isBefore(LocalDate.now()) || date.isAfter(LocalDate.now().plusDays(MAX_DAYS_AHEAD))) {
            throw new IllegalArgumentException("Reservations can be made from today up to "
                    + MAX_DAYS_AHEAD + " days ahead.");
        }
        if (slot == null) {
            throw new IllegalArgumentException("Please select an available time slot.");
        }
        if (!charger.isWorking()) {
            throw new IllegalArgumentException("This charger is out of service. Please choose another one.");
        }
        if (!isSlotAvailable(station, charger, date, slot)) {
            throw new IllegalArgumentException("That time slot is no longer available. Please pick another.");
        }

        // The database still has the final word: if another driver took the slot a moment ago,
        // this throws ConflictException (an IllegalArgumentException) and nothing is saved.
        Reservation reservation = dao.createReservation(session.getUserId(), station, charger, date, slot,
                LocalDateTime.now());

        notifications.add(NotificationType.RESERVATION_CONFIRMED,
                "Reservation " + reservation.getId() + " confirmed",
                station.getName() + " · " + Format.date(date) + ", " + slot + " · Charger "
                        + charger.getId() + " (" + charger.getConnectorType() + ", " + Format.power(charger.getPowerKw()) + ").",
                reservation.getId());
        notifications.logActivity("Reserved " + station.getName(),
                reservation.getId() + " · " + charger.getConnectorType() + " · " + Format.power(charger.getPowerKw()));
        reload();
        return reservation;
    }

    public void cancelReservation(Reservation reservation) {
        dao.updateStatus(reservation.getId(), ReservationStatus.CANCELLED,
                "Cancelled by you on " + Format.dateTime(LocalDateTime.now()) + ".");
        notifications.add(NotificationType.RESERVATION_CANCELLED,
                "Reservation " + reservation.getId() + " cancelled",
                "Your slot at " + reservation.getStation().getName() + " on " + Format.longDate(reservation.getDate())
                        + ", " + reservation.getTimeSlot() + " has been released.",
                reservation.getId());
        notifications.logActivity("Cancelled reservation " + reservation.getId(), reservation.getStation().getName());
        reload();
    }

    /**
     * A driver reports that a charger does not work. The charger becomes "out of service" for everyone,
     * and every upcoming booking on it (from ANY user) needs action: each owner gets an alert with alternatives.
     * Returns how many bookings were affected.
     */
    public int reportChargerProblem(Station station, Charger charger) {
        String note = "Charger " + charger.getId() + " was reported out of service by a driver on "
                + Format.dateTime(LocalDateTime.now()) + ".";
        List<BookingRef> affected = dao.reportChargerProblem(station.getId(), charger.getId(), LocalDateTime.now(), note);

        for (BookingRef booking : affected) {
            String unavailable = "Charger " + charger.getId() + " at " + station.getName()
                    + " was reported out of service. Your reservation " + booking.reservationId() + " needs a new charger.";
            String alternatives = charger.getConnectorType() + " chargers nearby may be free for "
                    + booking.reservationId() + ". Compare and switch in one step.";
            if (booking.userId() == session.getUserId()) {
                notifications.add(NotificationType.CHARGER_UNAVAILABLE, "Reserved charger unavailable", unavailable,
                        booking.reservationId());
                notifications.add(NotificationType.ALTERNATIVE_SUGGESTION, "Find a compatible alternative", alternatives,
                        booking.reservationId());
            } else {
                notifications.addFor(booking.userId(), NotificationType.CHARGER_UNAVAILABLE,
                        "Reserved charger unavailable", unavailable, booking.reservationId());
                notifications.addFor(booking.userId(), NotificationType.ALTERNATIVE_SUGGESTION,
                        "Find a compatible alternative", alternatives, booking.reservationId());
            }
        }
        notifications.logActivity("Reported charger " + charger.getId() + " out of service",
                station.getName() + (affected.isEmpty() ? "" : " · " + Format.plural(affected.size(), "booking") + " affected"));
        stations.reload(); // the charger's status changed
        reload();
        return affected.size();
    }

    /**
     * Moves the reservation to another station/charger, keeping the same date and time slot.
     * Throws IllegalArgumentException if that slot is not free at the new charger.
     */
    public void switchToAlternative(Reservation reservation, Station newStation, Charger newCharger) {
        if (!isSlotAvailable(newStation, newCharger, reservation.getDate(), reservation.getTimeSlot())) {
            throw new IllegalArgumentException("That time slot is not free at " + newStation.getName()
                    + ". Please choose another alternative.");
        }
        String oldStation = reservation.getStation().getName();
        dao.moveReservation(reservation.getId(), newStation, newCharger,
                "Moved from " + oldStation + " after a charger problem.");

        notifications.add(NotificationType.RESERVATION_CONFIRMED,
                "Reservation " + reservation.getId() + " moved",
                "Now at " + newStation.getName() + " · Charger " + newCharger.getId() + " ("
                        + newCharger.getConnectorType() + ", " + Format.power(newCharger.getPowerKw()) + ") · "
                        + Format.date(reservation.getDate()) + ", " + reservation.getTimeSlot() + ".",
                reservation.getId());
        notifications.logActivity("Switched " + reservation.getId() + " to " + newStation.getName(),
                "Alternative charger");
        stations.reload();
        reload();
    }

    /** Removes completed and cancelled reservations. Returns how many were removed. */
    public int clearPastReservations() {
        int removed = dao.deleteInactive(session.getUserId());
        reload();
        return removed;
    }
}
