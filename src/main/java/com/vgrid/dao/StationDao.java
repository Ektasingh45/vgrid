package com.vgrid.dao;

import com.vgrid.model.ChargerStatus;
import com.vgrid.model.CityImport;
import com.vgrid.model.Station;

import java.util.List;
import java.util.Optional;

/** Stations and their chargers, stored per city. */
public interface StationDao {

    /** Every station of one city with its chargers and amenities, ordered by name. */
    List<Station> findStations(String cityId);

    Optional<Station> findStation(String stationId);

    /**
     * Saves a freshly downloaded set of stations for a city: new stations are added, existing ones updated.
     * Old stations that are no longer in the list are deleted, unless a booking or check-in still uses them.
     * Also records when and where the city's data came from.
     */
    void saveStations(String cityId, List<Station> stations, String source);

    /** When the city's stations were last loaded, or empty if never. */
    Optional<CityImport> findImport(String cityId);

    /** Stored status: AVAILABLE or OUT_OF_SERVICE (reserved / in use are worked out live). */
    void updateChargerStatus(String stationId, String chargerId, ChargerStatus status);
}
