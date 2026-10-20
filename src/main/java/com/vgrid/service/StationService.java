package com.vgrid.service;

import com.vgrid.data.DemoDataStore;
import com.vgrid.model.*;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Station look-ups, distance calculation and local search/filtering. */
public class StationService {

    public enum AvailabilityFilter {
        ANY("Any availability"),
        AVAILABLE_NOW("Available now"),
        FULLY_OCCUPIED("Fully occupied");

        private final String label;

        AvailabilityFilter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Straight-line distance is multiplied by this to approximate road distance. */
    private static final double ROAD_FACTOR = 1.25;

    private final DemoDataStore store;

    public StationService(DemoDataStore store) {
        this.store = store;
    }

    public List<Station> getAllStations() {
        return store.getStations();
    }

    public List<Location> getLocations() {
        return store.getLocations();
    }

    public Location getHomeLocation() {
        return store.getSettings().getHomeLocation();
    }

    public double distanceKm(Station station, Location from) {
        return roadDistance(station.getMapX() - from.mapX(), station.getMapY() - from.mapY());
    }

    public double distanceKm(Station a, Station b) {
        return roadDistance(a.getMapX() - b.getMapX(), a.getMapY() - b.getMapY());
    }

    public double distanceFromHome(Station station) {
        return distanceKm(station, getHomeLocation());
    }

    private double roadDistance(double dx, double dy) {
        return Math.round(Math.hypot(dx, dy) * ROAD_FACTOR * 10) / 10.0;
    }

    /** Stations within the user's preferred maximum distance, nearest first. */
    public List<Station> getNearbyStations() {
        int maxDistance = store.getSettings().getMaxDistanceKm();
        return store.getStations().stream()
                .filter(s -> distanceFromHome(s) <= maxDistance)
                .sorted(Comparator.comparingDouble(this::distanceFromHome))
                .toList();
    }

    /**
     * Applies the Find Stations filters. A null value means "no filter".
     * Results are sorted by distance from the user's home location.
     */
    public List<Station> searchStations(String query, Double maxDistanceKm, ConnectorType connector,
                                        AvailabilityFilter availability, CongestionLevel congestion) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return store.getStations().stream()
                .filter(s -> text.isEmpty()
                        || s.getName().toLowerCase(Locale.ROOT).contains(text)
                        || s.getArea().toLowerCase(Locale.ROOT).contains(text)
                        || s.getAddress().toLowerCase(Locale.ROOT).contains(text))
                .filter(s -> maxDistanceKm == null || distanceFromHome(s) <= maxDistanceKm)
                .filter(s -> connector == null || s.supports(connector))
                .filter(s -> availability == null
                        || availability == AvailabilityFilter.ANY
                        || (availability == AvailabilityFilter.AVAILABLE_NOW && s.hasAvailableCharger())
                        || (availability == AvailabilityFilter.FULLY_OCCUPIED && !s.hasAvailableCharger()))
                .filter(s -> congestion == null || s.getCongestion() == congestion)
                .sorted(Comparator.comparingDouble(this::distanceFromHome))
                .toList();
    }

    // ------------------------------------------------------------ simulated forecast

    /** Simple time-of-day rule: mornings and evenings are busier, late nights are quiet. */
    public static boolean isPeakHour(LocalTime time) {
        int hour = time.getHour();
        return (hour >= 8 && hour < 11) || (hour >= 17 && hour < 21);
    }

    private static double timeFactor(LocalTime time) {
        int hour = time.getHour();
        if (isPeakHour(time)) return 1.6;
        if (hour >= 22 || hour < 6) return 0.4;
        return 1.0;
    }

    /** Simulated waiting time at the given arrival time (NOT a real prediction model). */
    public int predictedWaitMinutes(Station station, LocalTime arrival) {
        return (int) Math.round(station.getAverageWaitMinutes() * timeFactor(arrival));
    }

    /** Simulated congestion: one level busier at peak hours, one level quieter late at night. */
    public CongestionLevel predictedCongestion(Station station, LocalTime arrival) {
        int level = station.getCongestion().ordinal();
        double factor = timeFactor(arrival);
        if (factor > 1.5) level++;
        if (factor < 0.5) level--;
        CongestionLevel[] levels = CongestionLevel.values();
        return levels[Math.max(0, Math.min(levels.length - 1, level))];
    }
}
