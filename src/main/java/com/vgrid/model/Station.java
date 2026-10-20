package com.vgrid.model;

import java.util.List;

/**
 * A charging station with its chargers.
 * mapX / mapY are coordinates (in km) on a simulated city map, used to calculate distances.
 */
public class Station {
    private final String id;
    private final String name;
    private final String operator;
    private final String address;
    private final String area;
    private final double mapX;
    private final double mapY;
    private final CongestionLevel congestion;
    private final int averageWaitMinutes;
    private final double pricePerKwh;
    private final double rating;
    private final String openHours;
    private final List<String> amenities;
    private final List<Charger> chargers;

    public Station(String id, String name, String operator, String address, String area,
                   double mapX, double mapY, CongestionLevel congestion, int averageWaitMinutes,
                   double pricePerKwh, double rating, String openHours,
                   List<String> amenities, List<Charger> chargers) {
        this.id = id;
        this.name = name;
        this.operator = operator;
        this.address = address;
        this.area = area;
        this.mapX = mapX;
        this.mapY = mapY;
        this.congestion = congestion;
        this.averageWaitMinutes = averageWaitMinutes;
        this.pricePerKwh = pricePerKwh;
        this.rating = rating;
        this.openHours = openHours;
        this.amenities = amenities;
        this.chargers = chargers;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getOperator() { return operator; }
    public String getAddress() { return address; }
    public String getArea() { return area; }
    public double getMapX() { return mapX; }
    public double getMapY() { return mapY; }
    public CongestionLevel getCongestion() { return congestion; }
    public int getAverageWaitMinutes() { return averageWaitMinutes; }
    public double getPricePerKwh() { return pricePerKwh; }
    public double getRating() { return rating; }
    public String getOpenHours() { return openHours; }
    public List<String> getAmenities() { return amenities; }
    public List<Charger> getChargers() { return chargers; }

    public int getTotalChargers() {
        return chargers.size();
    }

    public int getAvailableChargers() {
        return (int) chargers.stream().filter(Charger::isAvailable).count();
    }

    public boolean hasAvailableCharger() {
        return getAvailableChargers() > 0;
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
