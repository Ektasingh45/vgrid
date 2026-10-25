package com.vgrid.dao;

import com.vgrid.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Charger bookings. Reservation ids look like "VG-1001". */
public interface ReservationDao {

    /**
     * All bookings of one user. stationLookup turns a station id into the Station object the app is
     * already using (stations can be in any city).
     */
    List<Reservation> findReservations(int userId, Function<String, Station> stationLookup);

    /** True when ANY user holds an active booking for this charger, date and slot. */
    boolean isSlotTaken(String stationId, String chargerId, LocalDate date, String slot);

    /** Active bookings (any user) for the given date and slot, as "stationId/chargerId" keys. */
    Set<String> findBookedChargers(LocalDate date, LocalTime slotStart);

    /** The active booking (any user) for this charger, date and slot, if there is one. */
    Optional<BookingRef> findBooking(String stationId, String chargerId, LocalDate date, LocalTime slotStart);

    /** Saves a new CONFIRMED booking. Throws ConflictException if the slot was taken meanwhile. */
    Reservation createReservation(int userId, Station station, Charger charger, LocalDate date, String slot,
                                  LocalDateTime createdAt);

    void updateStatus(String reservationId, ReservationStatus status, String note);

    /** Confirmed bookings whose slot has ended become COMPLETED. */
    void completeEndedBookings(LocalDateTime now);

    /**
     * A driver reported the charger as broken. In ONE transaction: the charger is marked out of service
     * and every upcoming active booking on it (any user) needs action. Returns those bookings.
     */
    List<BookingRef> reportChargerProblem(String stationId, String chargerId, LocalDateTime now, String note);

    /**
     * Moves a booking to another charger, keeping its date and slot. Checks that the slot is free
     * at the new charger and updates the booking in one transaction. Throws ConflictException if not free.
     */
    void moveReservation(String reservationId, Station newStation, Charger newCharger, String note);

    /** Deletes the user's completed and cancelled bookings. Returns how many were deleted. */
    int deleteInactive(int userId);
}
