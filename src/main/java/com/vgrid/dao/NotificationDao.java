package com.vgrid.dao;

import com.vgrid.model.ActivityItem;
import com.vgrid.model.AppNotification;
import com.vgrid.model.NotificationType;

import java.time.LocalDateTime;
import java.util.List;

/** Alerts and the "Recent activity" log of each user. */
public interface NotificationDao {

    List<AppNotification> findNotifications(int userId);

    void addNotification(int userId, NotificationType type, String title, String message,
                         LocalDateTime time, String reservationId);

    void markRead(int notificationId);

    void markAllRead(int userId);

    void deleteNotification(int notificationId);

    /** Deletes the user's read alerts. Returns how many were deleted. */
    int deleteRead(int userId);

    void addActivity(int userId, ActivityItem item);

    /** Newest first. */
    List<ActivityItem> findActivity(int userId, int limit);
}
