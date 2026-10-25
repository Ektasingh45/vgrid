package com.vgrid.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** A (simulated) charger booking made by the user. */
public class Reservation {
    private final String id;
    private Station station;
    private Charger charger;
    private final LocalDate date;
    private final String timeSlot; // e.g. "10:00 – 11:00"
    private ReservationStatus status;
    private String note;
    private final LocalDateTime createdAt;

    public Reservation(String id, Station station, Charger charger, LocalDate date, String timeSlot,
                       ReservationStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.station = station;
        this.charger = charger;
        this.date = date;
        this.timeSlot = timeSlot;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public Station getStation() { return station; }
    public Charger getCharger() { return charger; }
    public LocalDate getDate() { return date; }
    public String getTimeSlot() { return timeSlot; }
    public ReservationStatus getStatus() { return status; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStation(Station station) { this.station = station; }
    public void setCharger(Charger charger) { this.charger = charger; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public void setNote(String note) { this.note = note; }

    /** Confirmed or waiting for the user to pick an alternative. */
    public boolean isActive() {
        return status == ReservationStatus.CONFIRMED || status == ReservationStatus.ACTION_REQUIRED;
    }

    public LocalTime getSlotStart() {
        return slotStart(timeSlot);
    }

    /** "10:00 – 11:00" -> 10:00 */
    public static LocalTime slotStart(String slot) {
        return LocalTime.parse(slot.substring(0, 5));
    }

    /** 10:00 -> "10:00 – 11:00" (every slot is one hour long). */
    public static String slotLabel(LocalTime start) {
        return start + " – " + start.plusHours(1);
    }
}
