package com.vgrid.dao.jdbc;

import com.vgrid.dao.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** All data is read from and written to MySQL through the JDBC DAOs. */
public final class MySqlDataLayer implements DataLayer {
    private static final String SCHEMA_VERSION = "2";

    private final Database db;
    private final JdbcUserDao users;
    private final JdbcStationDao stations;
    private final JdbcReservationDao reservations;
    private final JdbcChargingDao charging;
    private final JdbcNotificationDao notifications;
    private final JdbcSettingsDao settings;
    private final JdbcOcppDao ocpp;

    private MySqlDataLayer(Database db) {
        this.db = db;
        this.users = new JdbcUserDao(db);
        this.stations = new JdbcStationDao(db);
        this.reservations = new JdbcReservationDao(db);
        this.charging = new JdbcChargingDao(db);
        this.notifications = new JdbcNotificationDao(db);
        this.settings = new JdbcSettingsDao(db);
        this.ocpp = new JdbcOcppDao(db);
    }

    /**
     * Connects to MySQL and creates any missing tables (schema.sql).
     * Stations are not loaded here: StationImportService downloads each city when it is first needed.
     * Throws SQLException when MySQL cannot be reached.
     */
    public static MySqlDataLayer connect(String url, String user, String password) throws SQLException {
        Database db = new Database(url, user, password);
        db.connection();
        checkNotOldVersion(db);
        db.runScript("/db/schema.sql");
        return new MySqlDataLayer(db);
    }

    /** A database made by an earlier VGrid version has different tables; say so clearly instead of failing later. */
    private static void checkNotOldVersion(Database db) throws SQLException {
        try (Statement st = db.connection().createStatement()) {
            boolean hasUsers;
            boolean hasMeta;
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name = 'users'")) {
                rs.next();
                hasUsers = rs.getInt(1) > 0;
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name = 'app_meta'")) {
                rs.next();
                hasMeta = rs.getInt(1) > 0;
            }
            String version = null;
            if (hasMeta) {
                try (ResultSet rs = st.executeQuery("SELECT value FROM app_meta WHERE name = 'schema_version'")) {
                    version = rs.next() ? rs.getString(1) : null;
                }
            }
            if ((hasUsers && !hasMeta) || (version != null && !version.equals(SCHEMA_VERSION))) {
                throw new SQLException("The database was created by an older VGrid version. "
                        + "In MySQL Workbench run: DROP DATABASE vgrid;  then start VGrid again.");
            }
        }
    }

    @Override public UserDao users() { return users; }
    @Override public StationDao stations() { return stations; }
    @Override public ReservationDao reservations() { return reservations; }
    @Override public ChargingDao charging() { return charging; }
    @Override public NotificationDao notifications() { return notifications; }
    @Override public SettingsDao settings() { return settings; }
    /** Read and write access to the OCPP tables (the write methods are used by the OCPP server). */
    @Override public JdbcOcppDao ocpp() { return ocpp; }
    @Override public boolean isDatabase() { return true; }
    @Override public String description() { return "MySQL · " + db.displayName(); }
}
