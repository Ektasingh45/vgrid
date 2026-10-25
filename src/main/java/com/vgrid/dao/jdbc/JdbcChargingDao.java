package com.vgrid.dao.jdbc;

import com.vgrid.dao.ChargingDao;
import com.vgrid.dao.ConflictException;
import com.vgrid.dao.DataAccessException;
import com.vgrid.model.ChargingSession;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The charging_sessions table. Two UNIQUE keys in schema.sql make sure a charger has only one
 * active session and a user charges at only one charger at a time.
 */
public class JdbcChargingDao implements ChargingDao {
    private static final String COLUMNS = "user_id, station_id, charger_code, reservation_id, started_at";

    private final Database db;

    public JdbcChargingDao(Database db) {
        this.db = db;
    }

    @Override
    public List<ChargingSession> findActiveSessions(LocalDateTime now) {
        endExpired(now);
        List<ChargingSession> sessions = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM charging_sessions WHERE active_flag = 1");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                sessions.add(read(rs));
            }
            return sessions;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load charging sessions", e);
        }
    }

    @Override
    public Optional<ChargingSession> findActiveSession(int userId, LocalDateTime now) {
        endExpired(now);
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM charging_sessions WHERE user_id = ? AND active_flag = 1")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load your charging session", e);
        }
    }

    @Override
    public void startSession(ChargingSession s) {
        endExpired(s.startedAt());
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO charging_sessions (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, s.userId());
            ps.setString(2, s.stationId());
            ps.setString(3, s.chargerId());
            ps.setString(4, s.reservationId());
            ps.setObject(5, s.startedAt());
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ConflictException("Someone just started charging there, or you are already charging elsewhere.");
        } catch (SQLException e) {
            throw new DataAccessException("Could not start charging", e);
        }
    }

    @Override
    public Optional<ChargingSession> endSession(int userId, LocalDateTime now) {
        Optional<ChargingSession> active = findActiveSession(userId, now);
        if (active.isPresent()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "UPDATE charging_sessions SET ended_at = ? WHERE user_id = ? AND active_flag = 1")) {
                ps.setObject(1, now);
                ps.setInt(2, userId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new DataAccessException("Could not finish charging", e);
            }
        }
        return active;
    }

    /** Sessions are one hour at most: older ones end automatically. */
    private void endExpired(LocalDateTime now) {
        try (PreparedStatement ps = db.connection().prepareStatement("UPDATE charging_sessions "
                + "SET ended_at = ADDTIME(started_at, '01:00:00') WHERE active_flag = 1 AND started_at <= ?")) {
            ps.setObject(1, now.minusMinutes(ChargingSession.MAX_MINUTES));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update charging sessions", e);
        }
    }

    private static ChargingSession read(ResultSet rs) throws SQLException {
        return new ChargingSession(rs.getInt("user_id"), rs.getString("station_id"), rs.getString("charger_code"),
                rs.getString("reservation_id"), rs.getObject("started_at", LocalDateTime.class));
    }
}
