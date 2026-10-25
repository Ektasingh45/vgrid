package com.vgrid.model;

import java.time.LocalDateTime;

/** When a city's stations were loaded, where from ("Open Charge Map" / "Sample data") and how many. */
public record CityImport(String cityId, String source, int stationCount, LocalDateTime importedAt) {

    public boolean fromOpenChargeMap() {
        return !Station.SAMPLE_SOURCE.equals(source);
    }
}
