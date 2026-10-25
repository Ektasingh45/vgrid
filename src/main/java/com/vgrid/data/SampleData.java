package com.vgrid.data;

import com.vgrid.model.*;

import java.util.ArrayList;
import java.util.List;

import static com.vgrid.model.ChargerStatus.AVAILABLE;
import static com.vgrid.model.ChargerStatus.OUT_OF_SERVICE;
import static com.vgrid.model.ConnectorType.*;

/**
 * Ten built-in stations for Pune, used ONLY when real stations cannot be downloaded from
 * Open Charge Map (no API key, or no internet). There are no built-in users or bookings:
 * every account and booking is created by the people using the app.
 */
public final class SampleData {

    /** Bookings are numbered from here: VG-1001, VG-1002, ... */
    public static final int FIRST_RESERVATION = 1001;

    // The sample stations were designed on a km grid where Baner is at (2, 8).
    // These constants place that grid on the real map, with Baner at its real position.
    private static final double GRID_ORIGIN_LAT = 18.5590 - 8 / 110.574;
    private static final double GRID_ORIGIN_LON = 73.7868 - 2 / 105.53;

    private SampleData() {
    }

    public static List<Station> puneStations() {
        List<Station> stations = new ArrayList<>();

        stations.add(station("ST01", "GreenPoint Charging Hub", "GreenPoint Energy",
                "Plot 12, Baner Road", "Baner", 3.0, 8.5, 18, 4.7,
                "Open 24 hours", List.of("Café", "Wi-Fi", "Restroom", "Covered parking"),
                charger("C1", CCS2, 60), charger("C2", CCS2, 60),
                charger("C3", TYPE_2, 22), charger("C4", TYPE_2, 22)));

        stations.add(station("ST02", "VoltWay Express", "VoltWay",
                "ITI Road, near Parihar Chowk", "Aundh", 4.5, 7.0, 21, 4.5,
                "6:00 AM – 11:00 PM", List.of("Restroom", "Lounge", "Snacks"),
                charger("C1", CCS2, 120), charger("C2", CCS2, 120), charger("C3", CHADEMO, 50)));

        stations.add(station("ST03", "ChargeNest Kothrud", "ChargeNest",
                "Paud Road, opposite City Pride", "Kothrud", 3.2, 2.8, 14, 4.3,
                "7:00 AM – 10:00 PM", List.of("Restroom", "Shaded parking"),
                charger("C1", TYPE_2, 7.4), charger("C2", TYPE_2, 7.4),
                charger("C3", TYPE_2, 7.4), charger("C4", CCS2, 30)));

        stations.add(station("ST04", "Urban Plug Point", "UrbanPlug",
                "FC Road, near Goodluck Chowk", "Shivajinagar", 6.2, 5.8, 20, 3.9,
                "Open 24 hours", List.of("Food court", "Restroom"),
                broken("C1", CCS2, 60), charger("C2", CCS2, 60),
                charger("C3", TYPE_2, 22), broken("C4", BHARAT_DC001, 15)));

        stations.add(station("ST05", "EcoCharge Station", "EcoCharge",
                "Rajiv Gandhi Infotech Park, Phase 1", "Hinjewadi", -2.5, 10.5, 22, 4.6,
                "Open 24 hours", List.of("Café", "Wi-Fi", "Restroom", "Security"),
                charger("C1", CCS2, 150), charger("C2", CCS2, 150),
                charger("C3", CCS2, 150), charger("C4", TYPE_2, 22)));

        stations.add(station("ST06", "PowerUp Mall Chargers", "PowerUp",
                "North Main Road, Phoenix Plaza basement", "Koregaon Park", 10.2, 6.3, 24, 4.1,
                "10:00 AM – 10:00 PM", List.of("Shopping", "Food court", "Restroom"),
                charger("C1", TYPE_2, 22), charger("C2", TYPE_2, 22),
                charger("C3", CCS2, 60), broken("C4", CHADEMO, 50)));

        stations.add(station("ST07", "BlueBolt FastCharge", "BlueBolt",
                "Airport Road, near Symbiosis", "Viman Nagar", 13.4, 9.2, 19, 4.8,
                "Open 24 hours", List.of("Lounge", "Wi-Fi", "Restroom", "Café"),
                charger("C1", CCS2, 120), charger("C2", CCS2, 60), charger("C3", CHADEMO, 50)));

        stations.add(station("ST08", "Sunrise EV Point", "Sunrise Mobility",
                "Magarpatta Road, Gate 3", "Hadapsar", 12.3, 1.4, 15, 4.2,
                "6:00 AM – 11:00 PM", List.of("Restroom", "Tea stall"),
                charger("C1", BHARAT_DC001, 15), charger("C2", BHARAT_DC001, 15), charger("C3", TYPE_2, 7.4)));

        stations.add(station("ST09", "MetroCharge Pashan", "MetroCharge",
                "Pashan–Sus Road, near NCL Colony", "Pashan", 1.0, 6.5, 17, 4.0,
                "7:00 AM – 11:00 PM", List.of("Restroom"),
                charger("C1", CCS2, 30), charger("C2", TYPE_2, 22)));

        stations.add(station("ST10", "Highway Hub Wakad", "Highway Hub",
                "Mumbai–Bengaluru Highway service road", "Wakad", -0.5, 9.8, 20, 4.4,
                "Open 24 hours", List.of("Restaurant", "Restroom", "Wi-Fi"),
                charger("C1", CCS2, 60), charger("C2", CCS2, 60),
                charger("C3", CCS2, 60), charger("C4", TYPE_2, 22)));

        return stations;
    }

    private static Station station(String id, String name, String operator, String address, String area,
                                   double gridX, double gridY, double price, double rating, String hours,
                                   List<String> amenities, Charger... chargers) {
        double latitude = round(GRID_ORIGIN_LAT + gridY / 110.574);
        double longitude = round(GRID_ORIGIN_LON + gridX / 105.53);
        return new Station(id, City.PUNE.id(), name, operator, address, area, latitude, longitude, price, rating,
                hours, amenities, new ArrayList<>(List.of(chargers)), Station.SAMPLE_SOURCE);
    }

    private static Charger charger(String id, ConnectorType type, double powerKw) {
        return new Charger(id, type, powerKw, AVAILABLE);
    }

    private static Charger broken(String id, ConnectorType type, double powerKw) {
        return new Charger(id, type, powerKw, OUT_OF_SERVICE);
    }

    private static double round(double degrees) {
        return Math.round(degrees * 1_000_000) / 1_000_000.0;
    }
}
