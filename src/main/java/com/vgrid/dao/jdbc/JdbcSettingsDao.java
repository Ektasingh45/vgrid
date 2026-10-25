package com.vgrid.dao.jdbc;

import com.vgrid.dao.DataAccessException;
import com.vgrid.dao.SettingsDao;
import com.vgrid.model.City;
import com.vgrid.model.ConnectorType;
import com.vgrid.model.UserSettings;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** The user_settings table (one row per user). */
public class JdbcSettingsDao implements SettingsDao {
    private final Database db;

    public JdbcSettingsDao(Database db) {
        this.db = db;
    }

    @Override
    public UserSettings loadSettings(int userId) {
        try (PreparedStatement ps = db.connection().prepareStatement("SELECT * FROM user_settings WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return UserSettings.defaults(City.PUNE); // never saved: use the defaults
                }
                City city = City.byId(rs.getString("city"));
                UserSettings s = UserSettings.defaults(city);
                s.setHomeLocation(city.area(rs.getString("home_location")));
                s.setPreferredConnector(ConnectorType.valueOf(rs.getString("preferred_connector")));
                s.setPreferredPowerKw(rs.getDouble("preferred_power_kw"));
                s.setMaxDistanceKm(rs.getInt("max_distance_km"));
                s.setMaxWaitMinutes(rs.getInt("max_wait_min"));
                s.setNotifyReservationUpdates(rs.getBoolean("notify_reservation"));
                s.setNotifyChargerAlerts(rs.getBoolean("notify_charger"));
                s.setNotifyAlternativeSuggestions(rs.getBoolean("notify_alternatives"));
                s.setEmailUpdates(rs.getBoolean("email_updates"));
                return s;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load settings", e);
        }
    }

    /** "Upsert": inserts the row the first time, updates it afterwards. */
    @Override
    public void saveSettings(int userId, UserSettings s) {
        String sql = "INSERT INTO user_settings (user_id, city, home_location, preferred_connector, preferred_power_kw, "
                + "max_distance_km, max_wait_min, notify_reservation, notify_charger, notify_alternatives, email_updates) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE city = VALUES(city), home_location = VALUES(home_location), "
                + "preferred_connector = VALUES(preferred_connector), preferred_power_kw = VALUES(preferred_power_kw), "
                + "max_distance_km = VALUES(max_distance_km), max_wait_min = VALUES(max_wait_min), "
                + "notify_reservation = VALUES(notify_reservation), notify_charger = VALUES(notify_charger), "
                + "notify_alternatives = VALUES(notify_alternatives), email_updates = VALUES(email_updates)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, s.getCity().id());
            ps.setString(3, s.getHomeLocation().name());
            ps.setString(4, s.getPreferredConnector().name());
            ps.setDouble(5, s.getPreferredPowerKw());
            ps.setInt(6, s.getMaxDistanceKm());
            ps.setInt(7, s.getMaxWaitMinutes());
            ps.setBoolean(8, s.isNotifyReservationUpdates());
            ps.setBoolean(9, s.isNotifyChargerAlerts());
            ps.setBoolean(10, s.isNotifyAlternativeSuggestions());
            ps.setBoolean(11, s.isEmailUpdates());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save settings", e);
        }
    }
}
