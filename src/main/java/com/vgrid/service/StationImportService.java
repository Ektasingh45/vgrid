package com.vgrid.service;

import com.vgrid.dao.StationDao;
import com.vgrid.data.AppConfig;
import com.vgrid.data.OpenChargeMapClient;
import com.vgrid.data.SampleData;
import com.vgrid.model.City;
import com.vgrid.model.CityImport;
import com.vgrid.model.Station;

import java.io.IOException;
import java.util.List;

/**
 * Makes sure a city's stations are in the database.
 * The first time a city is used, its real stations are downloaded from Open Charge Map and saved;
 * after that they are read from the database (fast, and works without internet).
 * Without an API key, Pune falls back to the built-in sample stations; other cities need the key.
 *
 * Downloads can take a few seconds, so call these methods from a background thread.
 */
public class StationImportService {
    public static final String OPEN_CHARGE_MAP = "Open Charge Map";

    /** What happened, for a message in the UI. warning explains why sample data was used (or null). */
    public record Outcome(City city, String source, int stationCount, String warning) {
    }

    private final StationDao stations;
    private final AppConfig config;

    public StationImportService(StationDao stations, AppConfig config) {
        this.stations = stations;
        this.config = config;
    }

    /** Downloads the city only if it was never loaded. Returns the current state. */
    public Outcome ensureLoaded(City city) throws IOException, InterruptedException {
        var existing = stations.findImport(city.id());
        if (existing.isPresent() && (existing.get().fromOpenChargeMap() || !config.hasOcmKey())) {
            CityImport done = existing.get();
            return new Outcome(city, done.source(), done.stationCount(), null);
        }
        // Never loaded, or only sample data so far and a key is now available: (re)load.
        return refresh(city);
    }

    /** Downloads the city's stations again ("Refresh stations"). */
    public Outcome refresh(City city) throws IOException, InterruptedException {
        if (!config.hasOcmKey()) {
            if (city.equals(City.PUNE)) {
                return saveSample("No Open Charge Map API key in db.properties, so built-in sample stations are used.");
            }
            throw new IOException("Real " + city.name() + " stations need an Open Charge Map API key. "
                    + "Add ocm.api.key=... to db.properties and restart VGrid.");
        }
        try {
            List<Station> downloaded = new OpenChargeMapClient(config.ocmApiKey(), config.ocmMaxResults())
                    .fetchStations(city);
            if (downloaded.isEmpty()) {
                throw new IOException("Open Charge Map has no supported chargers listed near " + city.name() + ".");
            }
            stations.saveStations(city.id(), downloaded, OPEN_CHARGE_MAP);
            return new Outcome(city, OPEN_CHARGE_MAP, downloaded.size(), null);
        } catch (IOException e) {
            // Pune can still be shown with the sample stations if it has nothing yet.
            if (city.equals(City.PUNE) && stations.findImport(city.id()).isEmpty()) {
                return saveSample(e.getMessage() + " Built-in sample stations are used for now.");
            }
            throw e;
        }
    }

    public CityImport currentImport(City city) {
        return stations.findImport(city.id()).orElse(null);
    }

    private Outcome saveSample(String warning) {
        List<Station> sample = SampleData.puneStations();
        stations.saveStations(City.PUNE.id(), sample, Station.SAMPLE_SOURCE);
        return new Outcome(City.PUNE, Station.SAMPLE_SOURCE, sample.size(), warning);
    }
}
