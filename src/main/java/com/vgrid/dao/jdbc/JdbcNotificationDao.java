package com.vgrid.dao.jdbc;

import com.vgrid.dao.DataAccessException;
import com.vgrid.dao.NotificationDao;
import com.vgrid.model.ActivityItem;
import com.vgrid.model.AppNotification;
import com.vgrid.model.NotificationType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** The notifications and activities tables. */
public class JdbcNotificationDao implements NotificationDao {
    private final Database db;

    public JdbcNotificationDao(Database db) {
        this.db = db;
    }

    @Override
    public List<AppNotification> findNotifications(int userId) {
        String sql = "SELECT id, type, title, message, created_at, reservation_id, is_read "
                + "FROM notifications WHERE user_id = ?";
        List<AppNotification> result = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new AppNotification(rs.getInt("id"), NotificationType.valueOf(rs.getString("type")),
                            rs.getString("title"), rs.getString("message"),
                            rs.getObject("created_at", LocalDateTime.class), rs.getString("reservation_id"),
                            rs.getBoolean("is_read")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load alerts", e);
        }
    }

    @Override
    public void addNotification(int userId, NotificationType type, String title, String message,
                                LocalDateTime time, String reservationId) {
        String sql = "INSERT INTO notifications (user_id, type, title, message, created_at, reservation_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, type.name());
            ps.setString(3, title);
            ps.setString(4, message);
            ps.setObject(5, time);
            ps.setString(6, reservationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the alert", e);
        }
    }

    @Override
    public void markRead(int notificationId) {
        update("UPDATE notifications SET is_read = TRUE WHERE id = ?", notificationId);
    }

    @Override
    public void markAllRead(int userId) {
        update("UPDATE notifications SET is_read = TRUE WHERE user_id = ?", userId);
    }

    @Override
    public void deleteNotification(int notificationId) {
        update("DELETE FROM notifications WHERE id = ?", notificationId);
    }

    @Override
    public int deleteRead(int userId) {
        return update("DELETE FROM notifications WHERE user_id = ? AND is_read = TRUE", userId);
    }

    @Override
    public void addActivity(int userId, ActivityItem item) {
        String sql = "INSERT INTO activities (user_id, title, detail, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, item.title());
            ps.setString(3, item.detail());
            ps.setObject(4, item.time());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the activity", e);
        }
    }

    @Override
    public List<ActivityItem> findActivity(int userId, int limit) {
        String sql = "SELECT title, detail, created_at FROM activities WHERE user_id = ? "
                + "ORDER BY created_at DESC, id DESC LIMIT ?";
        List<ActivityItem> result = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ActivityItem(rs.getString("title"), rs.getString("detail"),
                            rs.getObject("created_at", LocalDateTime.class)));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load recent activity", e);
        }
    }

    /** Runs an UPDATE/DELETE with one int parameter and returns the number of rows changed. */
    private int update(String sql, int value) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, value);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update alerts", e);
        }
    }
}
