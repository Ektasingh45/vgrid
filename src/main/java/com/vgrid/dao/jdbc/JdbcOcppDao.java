package com.vgrid.dao.jdbc;

import com.vgrid.dao.DataAccessException;
import com.vgrid.dao.OcppDao;
import com.vgrid.model.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The ocpp_chargers and ocpp_transactions tables.
 * The VGrid app only reads them (findChargerStates); the OCPP server also writes them.
 */
public class JdbcOcppDao implements OcppDao {
    private final Database db;

    public JdbcOcppDao(Database db) {
        this.db = db;
    }

    /** Which VGrid charger an OCPP charge point is: (stationId, chargerCode). */
    public record Link(String ocppId, String stationId, String chargerCode) {
    }

    // ------------------------------------------------------------------ read (used by the app)

    @Override
    public Map<String, OcppChargerState> findChargerStates() {
        String sql = "SELECT oc.*, t.id AS tx_id, t.meter_start, t.meter_last FROM ocpp_chargers oc "
                + "LEFT JOIN ocpp_transactions t ON t.ocpp_id = oc.ocpp_id AND t.stopped_at IS NULL";
        Map<String, OcppChargerState> states = new HashMap<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                boolean active = rs.getObject("tx_id") != null;
                double kwh = active ? Math.max(0, rs.getInt("meter_last") - rs.getInt("meter_start")) / 1000.0 : 0;
                OcppChargerState state = new OcppChargerState(rs.getString("ocpp_id"), rs.getString("station_id"),
                        rs.getString("charger_code"), rs.getBoolean("connected"), rs.getString("status"),
                        rs.getString("error_code"), rs.getObject("last_seen", LocalDateTime.class), active, kwh);
                states.put(state.stationId() + "/" + state.chargerId(), state);
            }
            return states;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load OCPP charger states", e);
        }
    }

    // ------------------------------------------------------------------ write (used by the OCPP server)

    public Optional<Link> findLink(String ocppId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT station_id, charger_code FROM ocpp_chargers WHERE ocpp_id = ?")) {
            ps.setString(1, ocppId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Link(ocppId, rs.getString(1), rs.getString(2))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not look up charge point " + ocppId, e);
        }
    }

    public void setConnected(String ocppId, boolean connected, LocalDateTime now) {
        update("UPDATE ocpp_chargers SET connected = ?, last_seen = ? WHERE ocpp_id = ?", connected, now, ocppId);
    }

    /** When the server starts, nobody is connected yet. */
    public void markAllDisconnected() {
        try (Statement st = db.connection().createStatement()) {
            st.executeUpdate("UPDATE ocpp_chargers SET connected = FALSE");
        } catch (SQLException e) {
            throw new DataAccessException("Could not reset OCPP connections", e);
        }
    }

    public void recordBoot(String ocppId, String vendor, String model, LocalDateTime now) {
        update("UPDATE ocpp_chargers SET vendor = ?, model = ?, last_seen = ? WHERE ocpp_id = ?", vendor, model, now, ocppId);
    }

    public void touch(String ocppId, LocalDateTime now) {
        update("UPDATE ocpp_chargers SET last_seen = ? WHERE ocpp_id = ?", now, ocppId);
    }

    public void updateStatus(String ocppId, String status, String errorCode, LocalDateTime now) {
        update("UPDATE ocpp_chargers SET status = ?, error_code = ?, last_seen = ? WHERE ocpp_id = ?",
                status, errorCode, now, ocppId);
    }

    /** Opens a transaction (any older open one on the same charger is closed first). Returns its id. */
    public int startTransaction(String ocppId, String idTag, int meterStartWh, LocalDateTime now) {
        try {
            return db.inTransaction(c -> {
                try (PreparedStatement close = c.prepareStatement(
                        "UPDATE ocpp_transactions SET stopped_at = ? WHERE ocpp_id = ? AND stopped_at IS NULL")) {
                    close.setObject(1, now);
                    close.setString(2, ocppId);
                    close.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO ocpp_transactions (ocpp_id, id_tag, "
                        + "meter_start, meter_last, started_at) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, ocppId);
                    ps.setString(2, idTag);
                    ps.setInt(3, meterStartWh);
                    ps.setInt(4, meterStartWh);
                    ps.setObject(5, now);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        return keys.getInt(1);
                    }
                }
            });
        } catch (SQLException e) {
            throw new DataAccessException("Could not start the OCPP transaction", e);
        }
    }

    /** Updates the energy meter of the charger's open transaction. */
    public void updateMeter(String ocppId, int meterWh) {
        update("UPDATE ocpp_transactions SET meter_last = ? WHERE ocpp_id = ? AND stopped_at IS NULL", meterWh, ocppId);
    }

    /** Closes the transaction. Returns the energy delivered in Wh, or -1 if the id is unknown. */
    public int stopTransaction(int transactionId, int meterStopWh, LocalDateTime now) {
        try (PreparedStatement find = db.connection().prepareStatement(
                "SELECT meter_start FROM ocpp_transactions WHERE id = ?")) {
            find.setInt(1, transactionId);
            int start;
            try (ResultSet rs = find.executeQuery()) {
                if (!rs.next()) return -1;
                start = rs.getInt(1);
            }
            update("UPDATE ocpp_transactions SET meter_last = ?, stopped_at = COALESCE(stopped_at, ?) WHERE id = ?",
                    meterStopWh, now, transactionId);
            return Math.max(0, meterStopWh - start);
        } catch (SQLException e) {
            throw new DataAccessException("Could not stop the OCPP transaction", e);
        }
    }

    /**
     * Creates the "VGrid OCPP Test Station" in the city (if missing) with four chargers, each linked to an
     * OCPP charge point id such as VGRID-PUNE-C1. Returns the linked charge points.
     */
    public List<Link> ensureTestStation(City city) {
        String stationId = "OCPP-" + city.id().toUpperCase(Locale.ROOT);
        // Next to the city's first area (the default home), so it is "nearby" for new users.
        Location home = city.areas().get(0);
        double lat = home.latitude() + 0.004;
        double lon = home.longitude() + 0.004;
        String area = home.name();
        Object[][] chargers = {{"C1", "CCS2", 60.0}, {"C2", "CCS2", 60.0}, {"C3", "TYPE_2", 22.0}, {"C4", "CHADEMO", 50.0}};
        List<Link> links = new ArrayList<>();
        try {
            db.inTransaction(c -> {
                try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO stations (id, city, name, operator, "
                        + "address, area, latitude, longitude, price_per_kwh, rating, open_hours, source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'Open 24 hours', ?)")) {
                    ps.setString(1, stationId);
                    ps.setString(2, city.id());
                    ps.setString(3, "VGrid OCPP Test Station");
                    ps.setString(4, "VGrid (charger simulator)");
                    ps.setString(5, "Demo station for the OCPP server, " + city.name());
                    ps.setString(6, area);
                    ps.setDouble(7, lat);
                    ps.setDouble(8, lon);
                    ps.setDouble(9, 20);
                    ps.setString(10, Station.OCPP_TEST_SOURCE);
                    ps.executeUpdate();
                }
                try (PreparedStatement amenity = c.prepareStatement(
                        "INSERT IGNORE INTO station_amenities (station_id, position, amenity) VALUES (?, 0, ?)");
                     PreparedStatement charger = c.prepareStatement("INSERT IGNORE INTO chargers (station_id, code, "
                             + "connector, power_kw, status) VALUES (?, ?, ?, ?, 'AVAILABLE')");
                     PreparedStatement link = c.prepareStatement(
                             "INSERT IGNORE INTO ocpp_chargers (ocpp_id, station_id, charger_code) VALUES (?, ?, ?)")) {
                    amenity.setString(1, stationId);
                    amenity.setString(2, "Live status over OCPP 1.6");
                    amenity.executeUpdate();
                    for (Object[] ch : chargers) {
                        charger.setString(1, stationId);
                        charger.setString(2, (String) ch[0]);
                        charger.setString(3, (String) ch[1]);
                        charger.setDouble(4, (Double) ch[2]);
                        charger.addBatch();
                        String ocppId = "VGRID-" + city.id().toUpperCase(Locale.ROOT) + "-" + ch[0];
                        link.setString(1, ocppId);
                        link.setString(2, stationId);
                        link.setString(3, (String) ch[0]);
                        link.addBatch();
                        links.add(new Link(ocppId, stationId, (String) ch[0]));
                    }
                    charger.executeBatch();
                    link.executeBatch();
                }
                return null;
            });
            return links;
        } catch (SQLException e) {
            throw new DataAccessException("Could not create the OCPP test station", e);
        }
    }

    private void update(String sql, Object... values) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update OCPP data", e);
        }
    }
}
