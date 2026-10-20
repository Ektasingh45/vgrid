package com.vgrid.data;

import com.vgrid.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.vgrid.model.ChargerStatus.*;
import static com.vgrid.model.ConnectorType.*;

/**
 * Holds ALL demo data in memory using ArrayLists.
 *
 * This is the only class that "owns" data. In a later phase it can be replaced by
 * DAO classes that read/write MySQL through JDBC, while the services and screens stay the same.
 * Nothing here is real: stations, congestion and reservations are sample values.
 */
public final class DemoDataStore {
    private static DemoDataStore instance;

    private final List<Location> locations = new ArrayList<>();
    private final List<Station> stations = new ArrayList<>();
    private final List<Reservation> reservations = new ArrayList<>();
    private final List<AppNotification> notifications = new ArrayList<>();
    private final List<ActivityItem> activities = new ArrayList<>();
    private UserSettings settings;
    private int nextReservationNumber;
    private int nextNotificationId;

    private DemoDataStore() {
        loadSampleData();
    }

    public static DemoDataStore getInstance() {
        if (instance == null) {
            instance = new DemoDataStore();
        }
        return instance;
    }

    public List<Location> getLocations() { return locations; }
    public List<Station> getStations() { return stations; }
    public List<Reservation> getReservations() { return reservations; }
    public List<AppNotification> getNotifications() { return notifications; }
    public List<ActivityItem> getActivities() { return activities; }
    public UserSettings getSettings() { return settings; }

    public String nextReservationId() {
        return "VG-" + (nextReservationNumber++);
    }

    public int nextNotificationId() {
        return nextNotificationId++;
    }

    /** Throws away every change made during the session and reloads the sample data. */
    public void resetToSampleData() {
        locations.clear();
        stations.clear();
        reservations.clear();
        notifications.clear();
        activities.clear();
        loadSampleData();
    }

    // ------------------------------------------------------------------ sample data

    private void loadSampleData() {
        nextReservationNumber = 1043;
        nextNotificationId = 1;

        locations.add(new Location("Baner", 2.0, 8.0));
        locations.add(new Location("Hinjewadi", -3.0, 10.0));
        locations.add(new Location("Kothrud", 3.0, 3.0));
        locations.add(new Location("Shivajinagar", 6.0, 6.0));
        locations.add(new Location("Koregaon Park", 10.0, 6.0));
        locations.add(new Location("Viman Nagar", 13.0, 9.0));
        locations.add(new Location("Hadapsar", 12.0, 1.0));

        settings = UserSettings.defaults(locations.get(0));

        stations.add(new Station("ST01", "GreenPoint Charging Hub", "GreenPoint Energy",
                "Plot 12, Baner Road", "Baner", 3.0, 8.5, CongestionLevel.LOW, 5, 18, 4.7,
                "Open 24 hours", List.of("Café", "Wi-Fi", "Restroom", "Covered parking"),
                chargers(charger("C1", CCS2, 60, AVAILABLE), charger("C2", CCS2, 60, IN_USE),
                        charger("C3", TYPE_2, 22, AVAILABLE), charger("C4", TYPE_2, 22, AVAILABLE))));

        stations.add(new Station("ST02", "VoltWay Express", "VoltWay",
                "ITI Road, near Parihar Chowk", "Aundh", 4.5, 7.0, CongestionLevel.MODERATE, 12, 21, 4.5,
                "6:00 AM – 11:00 PM", List.of("Restroom", "Lounge", "Snacks"),
                chargers(charger("C1", CCS2, 120, IN_USE), charger("C2", CCS2, 120, AVAILABLE),
                        charger("C3", CHADEMO, 50, AVAILABLE))));

        stations.add(new Station("ST03", "ChargeNest Kothrud", "ChargeNest",
                "Paud Road, opposite City Pride", "Kothrud", 3.2, 2.8, CongestionLevel.LOW, 8, 14, 4.3,
                "7:00 AM – 10:00 PM", List.of("Restroom", "Shaded parking"),
                chargers(charger("C1", TYPE_2, 7.4, AVAILABLE), charger("C2", TYPE_2, 7.4, AVAILABLE),
                        charger("C3", TYPE_2, 7.4, IN_USE), charger("C4", CCS2, 30, IN_USE))));

        stations.add(new Station("ST04", "Urban Plug Point", "UrbanPlug",
                "FC Road, near Goodluck Chowk", "Shivajinagar", 6.2, 5.8, CongestionLevel.HIGH, 35, 20, 3.9,
                "Open 24 hours", List.of("Food court", "Restroom"),
                chargers(charger("C1", CCS2, 60, OUT_OF_SERVICE), charger("C2", CCS2, 60, IN_USE),
                        charger("C3", TYPE_2, 22, IN_USE), charger("C4", BHARAT_DC001, 15, OUT_OF_SERVICE))));

        stations.add(new Station("ST05", "EcoCharge Station", "EcoCharge",
                "Rajiv Gandhi Infotech Park, Phase 1", "Hinjewadi", -2.5, 10.5, CongestionLevel.MODERATE, 10, 22, 4.6,
                "Open 24 hours", List.of("Café", "Wi-Fi", "Restroom", "Security"),
                chargers(charger("C1", CCS2, 150, AVAILABLE), charger("C2", CCS2, 150, AVAILABLE),
                        charger("C3", CCS2, 150, IN_USE), charger("C4", TYPE_2, 22, AVAILABLE))));

        stations.add(new Station("ST06", "PowerUp Mall Chargers", "PowerUp",
                "North Main Road, Phoenix Plaza basement", "Koregaon Park", 10.2, 6.3, CongestionLevel.HIGH, 25, 24, 4.1,
                "10:00 AM – 10:00 PM", List.of("Shopping", "Food court", "Restroom"),
                chargers(charger("C1", TYPE_2, 22, AVAILABLE), charger("C2", TYPE_2, 22, IN_USE),
                        charger("C3", CCS2, 60, IN_USE), charger("C4", CHADEMO, 50, OUT_OF_SERVICE))));

        stations.add(new Station("ST07", "BlueBolt FastCharge", "BlueBolt",
                "Airport Road, near Symbiosis", "Viman Nagar", 13.4, 9.2, CongestionLevel.LOW, 4, 19, 4.8,
                "Open 24 hours", List.of("Lounge", "Wi-Fi", "Restroom", "Café"),
                chargers(charger("C1", CCS2, 120, AVAILABLE), charger("C2", CCS2, 60, AVAILABLE),
                        charger("C3", CHADEMO, 50, IN_USE))));

        stations.add(new Station("ST08", "Sunrise EV Point", "Sunrise Mobility",
                "Magarpatta Road, Gate 3", "Hadapsar", 12.3, 1.4, CongestionLevel.LOW, 3, 15, 4.2,
                "6:00 AM – 11:00 PM", List.of("Restroom", "Tea stall"),
                chargers(charger("C1", BHARAT_DC001, 15, AVAILABLE), charger("C2", BHARAT_DC001, 15, AVAILABLE),
                        charger("C3", TYPE_2, 7.4, AVAILABLE))));

        stations.add(new Station("ST09", "MetroCharge Pashan", "MetroCharge",
                "Pashan–Sus Road, near NCL Colony", "Pashan", 1.0, 6.5, CongestionLevel.MODERATE, 15, 17, 4.0,
                "7:00 AM – 11:00 PM", List.of("Restroom"),
                chargers(charger("C1", CCS2, 30, AVAILABLE), charger("C2", TYPE_2, 22, IN_USE))));

        stations.add(new Station("ST10", "Highway Hub Wakad", "Highway Hub",
                "Mumbai–Bengaluru Highway service road", "Wakad", -0.5, 9.8, CongestionLevel.MODERATE, 14, 20, 4.4,
                "Open 24 hours", List.of("Restaurant", "Restroom", "Wi-Fi"),
                chargers(charger("C1", CCS2, 60, IN_USE), charger("C2", CCS2, 60, IN_USE),
                        charger("C3", CCS2, 60, AVAILABLE), charger("C4", TYPE_2, 22, AVAILABLE))));

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Reservation upcoming = new Reservation("VG-1042", stations.get(0), stations.get(0).getChargers().get(0),
                today.plusDays(1), "10:00 – 11:00", ReservationStatus.CONFIRMED, now.minusHours(2));
        Reservation needsAction = new Reservation("VG-1038", stations.get(3), stations.get(3).getChargers().get(0),
                today.plusDays(2), "18:00 – 19:00", ReservationStatus.ACTION_REQUIRED, now.minusDays(1));
        needsAction.setNote("Charger C1 reported out of service (simulated event).");
        Reservation completed = new Reservation("VG-1021", stations.get(2), stations.get(2).getChargers().get(0),
                today.minusDays(5), "14:00 – 15:00", ReservationStatus.COMPLETED, now.minusDays(6));
        completed.setNote("Charged 6.8 kWh (sample data).");
        Reservation cancelled = new Reservation("VG-1017", stations.get(1), stations.get(1).getChargers().get(0),
                today.minusDays(9), "09:00 – 10:00", ReservationStatus.CANCELLED, now.minusDays(10));
        reservations.add(upcoming);
        reservations.add(needsAction);
        reservations.add(completed);
        reservations.add(cancelled);

        notifications.add(new AppNotification(nextNotificationId(), NotificationType.CHARGER_UNAVAILABLE,
                "Reserved charger unavailable",
                "Charger C1 at Urban Plug Point is out of service for reservation VG-1038 (simulated event).",
                now.minusMinutes(25), "VG-1038", false));
        notifications.add(new AppNotification(nextNotificationId(), NotificationType.ALTERNATIVE_SUGGESTION,
                "Compatible alternatives found",
                "CCS2 chargers are free at nearby stations for VG-1038. Compare and switch in one step.",
                now.minusMinutes(24), "VG-1038", false));
        notifications.add(new AppNotification(nextNotificationId(), NotificationType.RESERVATION_CONFIRMED,
                "Reservation VG-1042 confirmed",
                "GreenPoint Charging Hub · Tomorrow, 10:00 – 11:00 · Charger C1 (CCS2, 60 kW).",
                now.minusHours(2), "VG-1042", true));
        notifications.add(new AppNotification(nextNotificationId(), NotificationType.INFO,
                "Low congestion near you",
                "Stations around Baner are quieter than usual this afternoon (simulated forecast).",
                now.minusDays(1), null, true));

        activities.add(new ActivityItem("Charger issue on VG-1038", "Urban Plug Point · simulated", now.minusMinutes(25)));
        activities.add(new ActivityItem("Reserved GreenPoint Charging Hub", "VG-1042 · CCS2 · 60 kW", now.minusHours(2)));
        activities.add(new ActivityItem("Searched stations near Baner", "7 stations within 10 km", now.minusDays(1)));
        activities.add(new ActivityItem("Completed charging at ChargeNest Kothrud", "VG-1021 · 6.8 kWh", now.minusDays(5)));
        activities.add(new ActivityItem("Cancelled reservation VG-1017", "VoltWay Express", now.minusDays(9)));
    }

    private static Charger charger(String id, ConnectorType type, double powerKw, ChargerStatus status) {
        return new Charger(id, type, powerKw, status);
    }

    private static List<Charger> chargers(Charger... chargers) {
        return new ArrayList<>(List.of(chargers));
    }
}
