package com.vgrid.model;

/** User preferences. Kept in memory for the current session only. */
public class UserSettings {
    private Location homeLocation;
    private ConnectorType preferredConnector;
    private double preferredPowerKw;
    private int maxDistanceKm;
    private int maxWaitMinutes;
    private boolean notifyReservationUpdates;
    private boolean notifyChargerAlerts;
    private boolean notifyAlternativeSuggestions;
    private boolean emailUpdates;

    public static UserSettings defaults(Location home) {
        UserSettings s = new UserSettings();
        s.homeLocation = home;
        s.preferredConnector = ConnectorType.CCS2;
        s.preferredPowerKw = 50;
        s.maxDistanceKm = 10;
        s.maxWaitMinutes = 20;
        s.notifyReservationUpdates = true;
        s.notifyChargerAlerts = true;
        s.notifyAlternativeSuggestions = true;
        s.emailUpdates = false;
        return s;
    }

    public Location getHomeLocation() { return homeLocation; }
    public ConnectorType getPreferredConnector() { return preferredConnector; }
    public double getPreferredPowerKw() { return preferredPowerKw; }
    public int getMaxDistanceKm() { return maxDistanceKm; }
    public int getMaxWaitMinutes() { return maxWaitMinutes; }
    public boolean isNotifyReservationUpdates() { return notifyReservationUpdates; }
    public boolean isNotifyChargerAlerts() { return notifyChargerAlerts; }
    public boolean isNotifyAlternativeSuggestions() { return notifyAlternativeSuggestions; }
    public boolean isEmailUpdates() { return emailUpdates; }

    public void setHomeLocation(Location homeLocation) { this.homeLocation = homeLocation; }
    public void setPreferredConnector(ConnectorType preferredConnector) { this.preferredConnector = preferredConnector; }
    public void setPreferredPowerKw(double preferredPowerKw) { this.preferredPowerKw = preferredPowerKw; }
    public void setMaxDistanceKm(int maxDistanceKm) { this.maxDistanceKm = maxDistanceKm; }
    public void setMaxWaitMinutes(int maxWaitMinutes) { this.maxWaitMinutes = maxWaitMinutes; }
    public void setNotifyReservationUpdates(boolean value) { this.notifyReservationUpdates = value; }
    public void setNotifyChargerAlerts(boolean value) { this.notifyChargerAlerts = value; }
    public void setNotifyAlternativeSuggestions(boolean value) { this.notifyAlternativeSuggestions = value; }
    public void setEmailUpdates(boolean emailUpdates) { this.emailUpdates = emailUpdates; }
}
