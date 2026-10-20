package com.vgrid.model;

import com.vgrid.util.Format;

/** One charging point inside a station. */
public class Charger {
    private final String id;
    private final ConnectorType connectorType;
    private final double powerKw;
    private ChargerStatus status;

    public Charger(String id, ConnectorType connectorType, double powerKw, ChargerStatus status) {
        this.id = id;
        this.connectorType = connectorType;
        this.powerKw = powerKw;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public ConnectorType getConnectorType() {
        return connectorType;
    }

    public double getPowerKw() {
        return powerKw;
    }

    public ChargerStatus getStatus() {
        return status;
    }

    public void setStatus(ChargerStatus status) {
        this.status = status;
    }

    public boolean isAvailable() {
        return status == ChargerStatus.AVAILABLE;
    }

    /** A charger that is not out of service can still be reserved for a later slot. */
    public boolean isWorking() {
        return status != ChargerStatus.OUT_OF_SERVICE;
    }

    /** Short label such as "C1 · CCS2 · 60 kW". */
    public String getLabel() {
        return id + " · " + connectorType + " · " + Format.power(powerKw);
    }
}
