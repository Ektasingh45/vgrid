package com.vgrid.model;

import java.util.List;

/**
 * A charging station with its chargers, at a real latitude / longitude.
 * source says where it came from, e.g. "Open Charge Map #123456" or "Sample data".
 *
 * Congestion and waiting time are not stored: they are worked out from how many chargers
 * are reserved or in use right now (VGrid bookings and check-ins).
 */
public class Station {
    public static final String SAMPLE_SOURCE = "Sample data";
    /** Source of the demo station whose chargers report over OCPP (driven by a charger simulator). */
    public static final String OCPP_TEST_SOURCE = "VGrid OCPP test station";

    private final String id;
    private final String cityId;
    private final String name;
    private final String operator;
    private final String address;
    private final String area;
    private final double latitude;
    private final double longitude;
    private final double pricePerKwh;
    private final double rating;
    private final String openHours;
    private final List<String> amenities;
    private final List<Charger> chargers;
    private final String source;

    public Station(String id, String cityId, String name, String operator, String address, String area,
                   double latitude, double longitude, double pricePerKwh, double rating, String openHours,
                   List<String> amenities, List<Charger> chargers, String source) {
        this.id = id;
        this.cityId = cityId;
        this.name = name;
        this.operator = operator;
        this.address = address;
        this.area = area;
        this.latitude = latitude;
        this.longitude = longitude;
        this.pricePerKwh = pricePerKwh;
        this.rating = rating;
        this.openHours = openHours;
        this.amenities = amenities;
        this.chargers = chargers;
        this.source = source;
    }

    public String getId() { return id; }
    public String getCityId() { return cityId; }
    public String getName() { return name; }
    public String getOperator() { return operator; }
    public String getAddress() { return address; }
    public String getArea() { return area; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getPricePerKwh() { return pricePerKwh; }
    public double getRating() { return rating; }
    public String getOpenHours() { return openHours; }
    public List<String> getAmenities() { return amenities; }
    public List<Charger> getChargers() { return chargers; }
    public String getSource() { return source; }

    /** True for real stations imported from Open Charge Map. */
    public boolean isImported() {
        return source != null && source.startsWith("Open Charge Map");
    }

    /** True for the test station whose chargers report their status over OCPP. */
    public boolean isOcppTestStation() {
        return OCPP_TEST_SOURCE.equals(source);
    }

    /** Short note shown after the price. */
    public String priceNote() {
        if (isImported()) return "(estimate)";
        if (isOcppTestStation()) return "(test station)";
        return "(sample tariff)";
    }

    /** 0 means "no rating" (Open Charge Map has no ratings). */
    public boolean hasRating() {
        return rating > 0;
    }

    public Charger findCharger(String chargerId) {
        return chargers.stream().filter(c -> c.getId().equals(chargerId)).findFirst().orElse(null);
    }

    public int getTotalChargers() {
        return chargers.size();
    }

    public int getAvailableChargers() {
        return (int) chargers.stream().filter(Charger::isAvailable).count();
    }

    public boolean hasAvailableCharger() {
        return getAvailableChargers() > 0;
    }

    /** Share of working chargers that are reserved or in use right now (0.0 - 1.0). */
    public double getOccupancy() {
        long working = chargers.stream().filter(Charger::isWorking).count();
        if (working == 0) {
            return 1.0;
        }
        long busy = chargers.stream()
                .filter(c -> c.getStatus() == ChargerStatus.IN_USE || c.getStatus() == ChargerStatus.RESERVED).count();
        return (double) busy / working;
    }

    /** Below 50% occupancy is low, up to 80% moderate, above that (or nothing working) high. */
    public CongestionLevel getCongestion() {
        double occupancy = getOccupancy();
        if (occupancy > 0.8) return CongestionLevel.HIGH;
        if (occupancy >= 0.5) return CongestionLevel.MODERATE;
        return CongestionLevel.LOW;
    }

    /** No wait while a charger is free; otherwise about half a charging session (one hour slots). */
    public int getAverageWaitMinutes() {
        if (hasAvailableCharger()) return 0;
        return chargers.stream().anyMatch(Charger::isWorking) ? 30 : 60;
    }

    public List<ConnectorType> getConnectorTypes() {
        return chargers.stream().map(Charger::getConnectorType).distinct().toList();
    }

    public boolean supports(ConnectorType type) {
        return chargers.stream().anyMatch(c -> c.getConnectorType() == type);
    }

    public double getMaxPowerKw() {
        return chargers.stream().mapToDouble(Charger::getPowerKw).max().orElse(0);
    }

    /** Chargers that are not out of service. */
    public List<Charger> getWorkingChargers() {
        return chargers.stream().filter(Charger::isWorking).toList();
    }

    /** Working chargers with the given connector and at least the given power. */
    public List<Charger> getCompatibleChargers(ConnectorType type, double minPowerKw) {
        return chargers.stream()
                .filter(Charger::isWorking)
                .filter(c -> c.getConnectorType() == type)
                .filter(c -> c.getPowerKw() >= minPowerKw)
                .toList();
    }
}
