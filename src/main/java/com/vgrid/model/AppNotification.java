package com.vgrid.model;

import java.time.LocalDateTime;

/** An alert shown on the Alerts screen. reservationId may be null. */
public class AppNotification {
    private final int id;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final LocalDateTime time;
    private final String reservationId;
    private boolean read;

    public AppNotification(int id, NotificationType type, String title, String message,
                           LocalDateTime time, String reservationId, boolean read) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.message = message;
        this.time = time;
        this.reservationId = reservationId;
        this.read = read;
    }

    public int getId() { return id; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LocalDateTime getTime() { return time; }
    public String getReservationId() { return reservationId; }
    public boolean isRead() { return read; }

    public void setRead(boolean read) { this.read = read; }
}
