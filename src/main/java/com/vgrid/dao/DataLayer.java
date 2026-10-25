package com.vgrid.dao;

/**
 * One complete source of data. There are two implementations:
 *  - MySqlDataLayer: JDBC DAOs that read and write the MySQL database,
 *  - DemoDataStore:  in-memory ArrayLists (offline mode, nothing is saved).
 * The services only see these interfaces, so they work the same with either one.
 */
public interface DataLayer {

    UserDao users();

    StationDao stations();

    ReservationDao reservations();

    ChargingDao charging();

    NotificationDao notifications();

    SettingsDao settings();

    /** Live states of OCPP-connected chargers (always empty in offline mode). */
    OcppDao ocpp();

    /** True for MySQL, false for offline mode. */
    boolean isDatabase();

    /** Short text for the UI, e.g. "MySQL · localhost:3306/vgrid". */
    String description();
}
