package com.vgrid.service;

import com.vgrid.data.DemoDataStore;
import com.vgrid.model.ActivityItem;
import com.vgrid.model.AppNotification;
import com.vgrid.model.NotificationType;
import com.vgrid.model.UserSettings;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Alerts and the recent-activity log. */
public class NotificationService {
    private final DemoDataStore store;

    public NotificationService(DemoDataStore store) {
        this.store = store;
    }

    /** Newest first. */
    public List<AppNotification> getAll() {
        return store.getNotifications().stream()
                .sorted(Comparator.comparing(AppNotification::getTime).reversed())
                .toList();
    }

    public long getUnreadCount() {
        return store.getNotifications().stream().filter(n -> !n.isRead()).count();
    }

    /** Adds an alert, unless the user switched that kind of alert off in Settings. */
    public void add(NotificationType type, String title, String message, String reservationId) {
        if (!isEnabled(type)) {
            return;
        }
        store.getNotifications().add(new AppNotification(store.nextNotificationId(), type, title, message,
                LocalDateTime.now(), reservationId, false));
    }

    private boolean isEnabled(NotificationType type) {
        UserSettings s = store.getSettings();
        return switch (type) {
            case RESERVATION_CONFIRMED, RESERVATION_CANCELLED -> s.isNotifyReservationUpdates();
            case CHARGER_UNAVAILABLE -> s.isNotifyChargerAlerts();
            case ALTERNATIVE_SUGGESTION -> s.isNotifyAlternativeSuggestions();
            case INFO -> true;
        };
    }

    public void markAsRead(AppNotification notification) {
        notification.setRead(true);
    }

    public void markAllAsRead() {
        store.getNotifications().forEach(n -> n.setRead(true));
    }

    public void dismiss(AppNotification notification) {
        store.getNotifications().remove(notification);
    }

    /** Removes every alert that has already been read. Returns how many were removed. */
    public int clearRead() {
        int before = store.getNotifications().size();
        store.getNotifications().removeIf(AppNotification::isRead);
        return before - store.getNotifications().size();
    }

    // ------------------------------------------------------------------ activity

    public void logActivity(String title, String detail) {
        store.getActivities().add(new ActivityItem(title, detail, LocalDateTime.now()));
    }

    public List<ActivityItem> getRecentActivity(int limit) {
        return store.getActivities().stream()
                .sorted(Comparator.comparing(ActivityItem::time).reversed())
                .limit(limit)
                .toList();
    }
}
