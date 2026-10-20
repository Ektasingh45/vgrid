package com.vgrid.service;

import com.vgrid.data.DemoDataStore;
import com.vgrid.model.*;
import com.vgrid.util.Format;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Creates, cancels and updates SIMULATED reservations held in memory. */
public class ReservationService {
    public static final int MAX_DAYS_AHEAD = 14;

    public static final List<String> TIME_SLOTS = List.of(
            "08:00 – 09:00", "09:00 – 10:00", "10:00 – 11:00", "11:00 – 12:00",
            "12:00 – 13:00", "13:00 – 14:00", "14:00 – 15:00", "15:00 – 16:00",
            "16:00 – 17:00", "17:00 – 18:00", "18:00 – 19:00", "19:00 – 20:00",
            "20:00 – 21:00");

    private final DemoDataStore store;
    private final NotificationService notifications;

    public ReservationService(DemoDataStore store, NotificationService notifications) {
        this.store = store;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ queries

    /** Active reservations first (soonest first), then past/cancelled (newest first). */
    public List<Reservation> getAllReservations() {
        Comparator<Reservation> soonest = Comparator.comparing(Reservation::getDate)
                .thenComparing(Reservation::getSlotStart);
        List<Reservation> result = new ArrayList<>(store.getReservations().stream()
                .filter(Reservation::isActive).sorted(soonest).toList());
        result.addAll(store.getReservations().stream()
                .filter(r -> !r.isActive()).sorted(soonest.reversed()).toList());
        return result;
    }

    public List<Reservation> getByStatus(ReservationStatus... statuses) {
        List<ReservationStatus> wanted = List.of(statuses);
        return getAllReservations().stream().filter(r -> wanted.contains(r.getStatus())).toList();
    }

    public boolean hasAnyReservations() {
        return !store.getReservations().isEmpty();
    }

    public long countActive() {
        return store.getReservations().stream().filter(Reservation::isActive).count();
    }

    public long countNeedingAttention() {
        return store.getReservations().stream()
                .filter(r -> r.getStatus() == ReservationStatus.ACTION_REQUIRED).count();
    }

    public Optional<Reservation> getNextReservation() {
        return getAllReservations().stream().filter(Reservation::isActive).findFirst();
    }

    public Reservation findById(String id) {
        return store.getReservations().stream()
                .filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * A slot is unavailable when it is in the past, already reserved by the user,
     * or "booked by another driver" (decided by a fixed pseudo-random rule so the demo is repeatable).
     */
    public boolean isSlotAvailable(Station station, Charger charger, LocalDate date, String slot) {
        LocalTime start = LocalTime.parse(slot.substring(0, 5));
        if (date.isBefore(LocalDate.now())) return false;
        if (date.equals(LocalDate.now()) && !start.isAfter(LocalTime.now())) return false;

        boolean alreadyReserved = store.getReservations().stream().anyMatch(r -> r.isActive()
                && r.getStation() == station && r.getCharger() == charger
                && r.getDate().equals(date) && r.getTimeSlot().equals(slot));
        if (alreadyReserved) return false;

        int simulatedBooking = Math.abs(Objects.hash(station.getId(), charger.getId(), date.toString(), slot));
        return simulatedBooking % 4 != 0; // roughly 1 in 4 slots shows as booked
    }

    public boolean isSlotInPast(LocalDate date, String slot) {
        LocalTime start = LocalTime.parse(slot.substring(0, 5));
        return date.isBefore(LocalDate.now())
                || (date.equals(LocalDate.now()) && !start.isAfter(LocalTime.now()));
    }

    // ------------------------------------------------------------------ actions

    /** Validates the booking and stores it in memory. Throws IllegalArgumentException with a friendly message. */
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

        Reservation reservation = new Reservation(store.nextReservationId(), station, charger, date, slot,
                ReservationStatus.CONFIRMED, LocalDateTime.now());
        store.getReservations().add(reservation);

        notifications.add(NotificationType.RESERVATION_CONFIRMED,
                "Reservation " + reservation.getId() + " confirmed",
                station.getName() + " · " + Format.date(date) + ", " + slot + " · Charger "
                        + charger.getId() + " (" + charger.getConnectorType() + ", " + Format.power(charger.getPowerKw()) + ").",
                reservation.getId());
        notifications.logActivity("Reserved " + station.getName(),
                reservation.getId() + " · " + charger.getConnectorType() + " · " + Format.power(charger.getPowerKw()));
        return reservation;
    }

    public void cancelReservation(Reservation reservation) {
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setNote("Cancelled by you on " + Format.dateTime(LocalDateTime.now()) + ".");
        notifications.add(NotificationType.RESERVATION_CANCELLED,
                "Reservation " + reservation.getId() + " cancelled",
                "Your slot at " + reservation.getStation().getName() + " on " + Format.longDate(reservation.getDate())
                        + ", " + reservation.getTimeSlot() + " has been released.",
                reservation.getId());
        notifications.logActivity("Cancelled reservation " + reservation.getId(), reservation.getStation().getName());
    }

    /** DEMO ONLY: pretends the reserved charger has just failed. */
    public void simulateChargerFailure(Reservation reservation) {
        Charger charger = reservation.getCharger();
        charger.setStatus(ChargerStatus.OUT_OF_SERVICE);
        reservation.setStatus(ReservationStatus.ACTION_REQUIRED);
        reservation.setNote("Charger " + charger.getId() + " reported out of service (simulated event).");

        notifications.add(NotificationType.CHARGER_UNAVAILABLE, "Reserved charger unavailable",
                "Charger " + charger.getId() + " at " + reservation.getStation().getName()
                        + " is out of service for reservation " + reservation.getId() + " (simulated event).",
                reservation.getId());
        notifications.add(NotificationType.ALTERNATIVE_SUGGESTION, "Compatible alternatives found",
                charger.getConnectorType() + " chargers are free at nearby stations for "
                        + reservation.getId() + ". Compare and switch in one step.",
                reservation.getId());
        notifications.logActivity("Charger issue on " + reservation.getId(),
                reservation.getStation().getName() + " · simulated");
    }

    /** Moves the reservation to another station/charger, keeping the same date and time slot. */
    public void switchToAlternative(Reservation reservation, Station newStation, Charger newCharger) {
        String oldStation = reservation.getStation().getName();
        reservation.setStation(newStation);
        reservation.setCharger(newCharger);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setNote("Moved from " + oldStation + " after a charger issue (simulated).");

        notifications.add(NotificationType.RESERVATION_CONFIRMED,
                "Reservation " + reservation.getId() + " moved",
                "Now at " + newStation.getName() + " · Charger " + newCharger.getId() + " ("
                        + newCharger.getConnectorType() + ", " + Format.power(newCharger.getPowerKw()) + ") · "
                        + Format.date(reservation.getDate()) + ", " + reservation.getTimeSlot() + ".",
                reservation.getId());
        notifications.logActivity("Switched " + reservation.getId() + " to " + newStation.getName(),
                "Alternative charger · simulated");
    }

    /** Removes completed and cancelled reservations. Returns how many were removed. */
    public int clearPastReservations() {
        int before = store.getReservations().size();
        store.getReservations().removeIf(r -> !r.isActive());
        return before - store.getReservations().size();
    }
}
