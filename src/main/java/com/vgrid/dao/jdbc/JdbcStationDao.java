package com.vgrid.dao.jdbc;

import com.vgrid.dao.DataAccessException;
import com.vgrid.dao.StationDao;
import com.vgrid.model.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** The stations, station_amenities, chargers and city_imports tables. */
public class JdbcStationDao implements StationDao {
    private final Database db;

    public JdbcStationDao(Database db) {
        this.db = db;
    }

    @Override
    public List<Station> findStations(String cityId) {
        return load("WHERE s.city = ?", cityId);
    }

    @Override
    public Optional<Station> findStation(String stationId) {
        return load("WHERE s.id = ?", stationId).stream().findFirst();
    }

    /** Three queries (stations, chargers, amenities) instead of one query per station. */
    private List<Station> load(String where, String value) {
        try {
            Map<String, List<Charger>> chargers = new HashMap<>();
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT c.station_id, c.code, c.connector, c.power_kw, c.status FROM chargers c "
                            + "JOIN stations s ON s.id = c.station_id " + where + " ORDER BY c.station_id, c.code")) {
                ps.setString(1, value);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        chargers.computeIfAbsent(rs.getString("station_id"), id -> new ArrayList<>())
                                .add(new Charger(rs.getString("code"), ConnectorType.valueOf(rs.getString("connector")),
                                        rs.getDouble("power_kw"), ChargerStatus.valueOf(rs.getString("status"))));
                    }
                }
            }
            Map<String, List<String>> amenities = new HashMap<>();
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT a.station_id, a.amenity FROM station_amenities a "
                            + "JOIN stations s ON s.id = a.station_id " + where + " ORDER BY a.station_id, a.position")) {
                ps.setString(1, value);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        amenities.computeIfAbsent(rs.getString("station_id"), id -> new ArrayList<>())
                                .add(rs.getString("amenity"));
                    }
                }
            }
            List<Station> stations = new ArrayList<>();
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT * FROM stations s " + where + " ORDER BY s.name")) {
                ps.setString(1, value);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        stations.add(new Station(id, rs.getString("city"), rs.getString("name"),
                                rs.getString("operator"), rs.getString("address"), rs.getString("area"),
                                rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getDouble("price_per_kwh"),
                                rs.getDouble("rating"), rs.getString("open_hours"),
                                amenities.getOrDefault(id, List.of()), chargers.getOrDefault(id, new ArrayList<>()),
                                rs.getString("source")));
                    }
                }
            }
            return stations;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load stations", e);
        }
    }

    @Override
    public void saveStations(String cityId, List<Station> stations, String source) {
        try {
            db.inTransaction(c -> {
                upsertStations(c, stations);
                deleteUnused(c, cityId, stations);
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO city_imports (city, source, station_count, "
                        + "imported_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE source = VALUES(source), "
                        + "station_count = VALUES(station_count), imported_at = VALUES(imported_at)")) {
                    ps.setString(1, cityId);
                    ps.setString(2, source);
                    ps.setInt(3, stations.size());
                    ps.setObject(4, LocalDateTime.now());
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the downloaded stations", e);
        }
    }

    /** "Upsert": insert new stations/chargers, update the ones that already exist. */
    private static void upsertStations(Connection c, List<Station> stations) throws SQLException {
        try (PreparedStatement station = c.prepareStatement("INSERT INTO stations (id, city, name, operator, address, "
                + "area, latitude, longitude, price_per_kwh, rating, open_hours, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE city = VALUES(city), "
                + "name = VALUES(name), operator = VALUES(operator), address = VALUES(address), area = VALUES(area), "
                + "latitude = VALUES(latitude), longitude = VALUES(longitude), price_per_kwh = VALUES(price_per_kwh), "
                + "rating = VALUES(rating), open_hours = VALUES(open_hours), source = VALUES(source)");
             PreparedStatement clearAmenities = c.prepareStatement("DELETE FROM station_amenities WHERE station_id = ?");
             PreparedStatement amenity = c.prepareStatement(
                     "INSERT INTO station_amenities (station_id, position, amenity) VALUES (?, ?, ?)");
             PreparedStatement charger = c.prepareStatement("INSERT INTO chargers (station_id, code, connector, "
                     + "power_kw, status) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE connector = VALUES(connector), "
                     + "power_kw = VALUES(power_kw), status = VALUES(status)")) {
            for (Station s : stations) {
                station.setString(1, s.getId());
                station.setString(2, s.getCityId());
                station.setString(3, s.getName());
                station.setString(4, s.getOperator());
                station.setString(5, s.getAddress());
                station.setString(6, s.getArea());
                station.setDouble(7, s.getLatitude());
                station.setDouble(8, s.getLongitude());
                station.setDouble(9, s.getPricePerKwh());
                station.setDouble(10, s.getRating());
                station.setString(11, s.getOpenHours());
                station.setString(12, s.getSource());
                station.addBatch();
                clearAmenities.setString(1, s.getId());
                clearAmenities.addBatch();
                for (int i = 0; i < s.getAmenities().size(); i++) {
                    amenity.setString(1, s.getId());
                    amenity.setInt(2, i);
                    amenity.setString(3, s.getAmenities().get(i));
                    amenity.addBatch();
                }
                for (Charger ch : s.getChargers()) {
                    charger.setString(1, s.getId());
                    charger.setString(2, ch.getId());
                    charger.setString(3, ch.getConnectorType().name());
                    charger.setDouble(4, ch.getPowerKw());
                    charger.setString(5, storedStatus(ch.getStatus()).name());
                    charger.addBatch();
                }
            }
            station.executeBatch();   // parents before children
            clearAmenities.executeBatch();
            amenity.executeBatch();
            charger.executeBatch();
        }
    }

    /** Removes the city's old stations that are gone from the new list and are not used by any booking or check-in. */
    private static void deleteUnused(Connection c, String cityId, List<Station> keep) throws SQLException {
        Set<String> keepIds = new HashSet<>();
        keep.forEach(s -> keepIds.add(s.getId()));
        List<String> candidates = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM stations WHERE city = ? "
                + "AND source <> '" + Station.OCPP_TEST_SOURCE + "' "   // never remove the OCPP test station
                + "AND id NOT IN (SELECT station_id FROM reservations) "
                + "AND id NOT IN (SELECT station_id FROM charging_sessions)")) {
            ps.setString(1, cityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!keepIds.contains(rs.getString(1))) {
                        candidates.add(rs.getString(1));
                    }
                }
            }
        }
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM stations WHERE id = ?")) {
            for (String id : candidates) {
                delete.setString(1, id);   // chargers and amenities go too (ON DELETE CASCADE)
                delete.addBatch();
            }
            delete.executeBatch();
        }
    }

    @Override
    public Optional<CityImport> findImport(String cityId) {
        try (PreparedStatement ps = db.connection().prepareStatement("SELECT * FROM city_imports WHERE city = ?")) {
            ps.setString(1, cityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CityImport(cityId, rs.getString("source"), rs.getInt("station_count"),
                        rs.getObject("imported_at", LocalDateTime.class)));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read the city's import record", e);
        }
    }

    @Override
    public void updateChargerStatus(String stationId, String chargerId, ChargerStatus status) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE chargers SET status = ? WHERE station_id = ? AND code = ?")) {
            ps.setString(1, storedStatus(status).name());
            ps.setString(2, stationId);
            ps.setString(3, chargerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the charger", e);
        }
    }

    /** Reserved / in use are never stored, only worked out live. */
    private static ChargerStatus storedStatus(ChargerStatus status) {
        return status == ChargerStatus.OUT_OF_SERVICE ? ChargerStatus.OUT_OF_SERVICE : ChargerStatus.AVAILABLE;
    }
}
