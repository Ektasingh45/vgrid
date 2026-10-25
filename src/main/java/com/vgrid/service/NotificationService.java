package com.vgrid.service;

import com.vgrid.dao.NotificationDao;
import com.vgrid.model.ActivityItem;
import com.vgrid.model.AppNotification;
import com.vgrid.model.NotificationType;
import com.vgrid.model.UserSettings;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Alerts and the recent-activity log of the signed-in user. */
public class NotificationService {
    private final NotificationDao dao;
    private final UserSession session;

    public NotificationService(NotificationDao dao, UserSession session) {
        this.dao = dao;
        this.session = session;
    }

    /** Newest first. */
    public List<AppNotification> getAll() {
        return dao.findNotifications(session.getUserId()).stream()
                .sorted(Comparator.comparing(AppNotification::getTime).reversed())
                .toList();
    }

    public long getUnreadCount() {
        return dao.findNotifications(session.getUserId()).stream().filter(n -> !n.isRead()).count();
    }

    /** Adds an alert, unless the user switched that kind of alert off in Settings. */
    public void add(NotificationType type, String title, String message, String reservationId) {
        if (!isEnabled(type)) {
            return;
        }
        dao.addNotification(session.getUserId(), type, title, message, LocalDateTime.now(), reservationId);
    }

    /** Sends an alert to ANOTHER user, e.g. when the charger they booked is reported broken. */
    public void addFor(int userId, NotificationType type, String title, String message, String reservationId) {
        dao.addNotification(userId, type, title, message, LocalDateTime.now(), reservationId);
    }

    /** First alert and activity of a brand-new account. */
    public void welcome() {
        dao.addNotification(session.getUserId(), NotificationType.INFO, "Welcome to VGrid",
                "Pick your city in Settings, then find a charger and book a one-hour slot.", LocalDateTime.now(), null);
        logActivity("Created your VGrid account", session.getUser().email());
    }

    private boolean isEnabled(NotificationType type) {
        UserSettings s = session.getSettings();
        return switch (type) {
            case RESERVATION_CONFIRMED, RESERVATION_CANCELLED -> s.isNotifyReservationUpdates();
            case CHARGER_UNAVAILABLE -> s.isNotifyChargerAlerts();
            case ALTERNATIVE_SUGGESTION -> s.isNotifyAlternativeSuggestions();
            case INFO -> true;
        };
    }

    public void markAsRead(AppNotification notification) {
        dao.markRead(notification.getId());
        notification.setRead(true);
    }

    public void markAllAsRead() {
        dao.markAllRead(session.getUserId());
    }

    public void dismiss(AppNotification notification) {
        dao.deleteNotification(notification.getId());
    }

    /** Removes every alert that has already been read. Returns how many were removed. */
    public int clearRead() {
        return dao.deleteRead(session.getUserId());
    }

    // ------------------------------------------------------------------ activity

    public void logActivity(String title, String detail) {
        dao.addActivity(session.getUserId(), new ActivityItem(title, detail, LocalDateTime.now()));
    }

    public List<ActivityItem> getRecentActivity(int limit) {
        return dao.findActivity(session.getUserId(), limit);
    }
}
