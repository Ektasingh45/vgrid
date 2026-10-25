package com.vgrid.service;

import com.vgrid.dao.ChargingDao;
import com.vgrid.dao.OcppDao;
import com.vgrid.dao.ReservationDao;
import com.vgrid.dao.StationDao;
import com.vgrid.model.*;
import com.vgrid.util.Geo;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Station look-ups, distances and search/filtering.
 *
 * Stations are loaded per city through the StationDao and kept in memory until reload() is called.
 * Each charger's live status is worked out on every reload:
 *   stored OUT_OF_SERVICE        -> out of service (Open Charge Map, a driver's report, or an OCPP fault)
 *   OCPP charger reports busy    -> IN_USE   (the charger itself says Charging / Preparing / ...)
 *   someone checked in           -> IN_USE
 *   booked for this hour         -> RESERVED
 *   otherwise                    -> AVAILABLE
 */
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

    private final StationDao stationDao;
    private final ReservationDao reservationDao;
    private final ChargingDao chargingDao;
    private final OcppDao ocppDao;
    private final UserSession session;
    private final Map<String, List<Station>> stationsByCity = new HashMap<>();
    private final Map<String, Station> stationsById = new HashMap<>();
    private Set<String> bookedNow = Set.of();
    private Map<String, ChargingSession> sessionsByCharger = Map.of();
    private Map<String, OcppChargerState> ocppStates = Map.of();

    public StationService(StationDao stationDao, ReservationDao reservationDao, ChargingDao chargingDao,
                          OcppDao ocppDao, UserSession session) {
        this.stationDao = stationDao;
        this.reservationDao = reservationDao;
        this.chargingDao = chargingDao;
        this.ocppDao = ocppDao;
        this.session = session;
        reload();
    }

    /** Forgets loaded stations and reads the live status again (bookings this hour, check-ins). */
    public void reload() {
        stationsByCity.clear();
        stationsById.clear();
        LocalDateTime now = LocalDateTime.now();
        bookedNow = reservationDao.findBookedChargers(now.toLocalDate(), now.toLocalTime().truncatedTo(ChronoUnit.HOURS));
        Map<String, ChargingSession> sessions = new HashMap<>();
        for (ChargingSession s : chargingDao.findActiveSessions(now)) {
            sessions.put(key(s.stationId(), s.chargerId()), s);
        }
        sessionsByCharger = sessions;
        ocppStates = ocppDao.findChargerStates();
    }

    // ------------------------------------------------------------------ stations

    /** Stations of the user's selected city. */
    public List<Station> getAllStations() {
        return stationsIn(getCity().id());
    }

    public List<Station> stationsIn(String cityId) {
        return stationsByCity.computeIfAbsent(cityId, id -> {
            List<Station> list = stationDao.findStations(id);
            list.forEach(this::remember);
            return list;
        });
    }

    /** Any station by id (any city), or null. Bookings use this to find their station. */
    public Station findStation(String stationId) {
        Station known = stationsById.get(stationId);
        if (known != null) {
            return known;
        }
        return stationDao.findStation(stationId).map(s -> {
            remember(s);
            return s;
        }).orElse(null);
    }

    private void remember(Station station) {
        for (Charger charger : station.getChargers()) {
            if (charger.getStatus() == ChargerStatus.OUT_OF_SERVICE) continue;
            String key = key(station.getId(), charger.getId());
            OcppChargerState ocpp = ocppStates.get(key);
            if ((ocpp != null && ocpp.isOnline(LocalDateTime.now()) && ocpp.isBusy()) || sessionsByCharger.containsKey(key)) {
                charger.setStatus(ChargerStatus.IN_USE);
            } else if (bookedNow.contains(key)) {
                charger.setStatus(ChargerStatus.RESERVED);
            } else {
                charger.setStatus(ChargerStatus.AVAILABLE);
            }
        }
        stationsById.put(station.getId(), station);
    }

    /** A driver confirms a charger that was reported broken works again. */
    public void markWorking(Station station, Charger charger) {
        stationDao.updateChargerStatus(station.getId(), charger.getId(), ChargerStatus.AVAILABLE);
        reload();
    }

    /** What this charger last reported over OCPP, or null for chargers that are not connected to OCPP. */
    public OcppChargerState ocppStateAt(Station station, Charger charger) {
        return ocppStates.get(key(station.getId(), charger.getId()));
    }

    /** The active check-in at this charger, if someone is charging there. */
    public ChargingSession sessionAt(Station station, Charger charger) {
        return sessionsByCharger.get(key(station.getId(), charger.getId()));
    }

    private static String key(String stationId, String chargerId) {
        return stationId + "/" + chargerId;
    }

    // ------------------------------------------------------------------ city and locations

    public City getCity() {
        return session.getSettings().getCity();
    }

    /** Starting points: the areas of the selected city. */
    public List<Location> getLocations() {
        return getCity().areas();
    }

    public Location getHomeLocation() {
        return session.getSettings().getHomeLocation();
    }

    // ------------------------------------------------------------------ distances

    public double distanceKm(Station station, Location from) {
        return roadDistance(Geo.haversineKm(station.getLatitude(), station.getLongitude(), from.latitude(), from.longitude()));
    }

    public double distanceKm(Station a, Station b) {
        return roadDistance(Geo.haversineKm(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude()));
    }

    public double distanceFromHome(Station station) {
        return distanceKm(station, getHomeLocation());
    }

    private static double roadDistance(double straightLineKm) {
        return Math.round(straightLineKm * ROAD_FACTOR * 10) / 10.0;
    }

    // ------------------------------------------------------------------ search

    /** Stations within the user's preferred maximum distance, nearest first. */
    public List<Station> getNearbyStations() {
        int maxDistance = session.getSettings().getMaxDistanceKm();
        return getAllStations().stream()
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
        return getAllStations().stream()
                .filter(s -> text.isEmpty()
                        || s.getName().toLowerCase(Locale.ROOT).contains(text)
                        || s.getArea().toLowerCase(Locale.ROOT).contains(text)
                        || s.getAddress().toLowerCase(Locale.ROOT).contains(text)
                        || s.getOperator().toLowerCase(Locale.ROOT).contains(text))
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

    // ------------------------------------------------------------ forecast for a later arrival time

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

    /** Current estimated wait, adjusted for the arrival time (rule-based, not a prediction model). */
    public int predictedWaitMinutes(Station station, LocalTime arrival) {
        return (int) Math.round(station.getAverageWaitMinutes() * timeFactor(arrival));
    }

    /** Current congestion: one level busier at peak hours, one level quieter late at night. */
    public CongestionLevel predictedCongestion(Station station, LocalTime arrival) {
        int level = station.getCongestion().ordinal();
        double factor = timeFactor(arrival);
        if (factor > 1.5) level++;
        if (factor < 0.5) level--;
        CongestionLevel[] levels = CongestionLevel.values();
        return levels[Math.max(0, Math.min(levels.length - 1, level))];
    }
}
