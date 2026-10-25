package com.vgrid.service;

import com.vgrid.model.*;
import com.vgrid.util.Format;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks stations using simple, explainable rules (no machine learning).
 *
 * 1. Exclude stations without a working compatible charger of the required power.
 * 2. Exclude stations beyond the distance limit or above the waiting-time limit.
 * 3. Score the rest out of 100:
 *      85 - 2.5 x distance - 0.8 x wait - congestion penalty
 *      + 8 if a compatible charger is free now (otherwise -10)
 *      + up to 10 points for higher charging power.
 */
public class RecommendationService {
    private final StationService stationService;

    public RecommendationService(StationService stationService) {
        this.stationService = stationService;
    }

    public List<RecommendationResult> findBestStations(RecommendationRequest request) {
        List<RecommendationResult> results = new ArrayList<>();
        for (Station station : stationService.getAllStations()) {
            RecommendationResult result = evaluate(station, request);
            if (result != null) {
                results.add(result);
            }
        }
        results.sort(Comparator.comparingInt(RecommendationResult::score).reversed());
        if (!results.isEmpty()) {
            results.set(0, results.get(0).withSummary(topReason(results, request)));
        }
        return results;
    }

    /**
     * Compatible alternatives for a reservation whose charger became unavailable.
     * Distances are measured from the originally reserved station.
     */
    public List<RecommendationResult> findAlternatives(Reservation reservation, int limit) {
        Station original = reservation.getStation();
        Location origin = new Location(original.getName(), original.getLatitude(), original.getLongitude());
        RecommendationRequest request = new RecommendationRequest(origin,
                reservation.getCharger().getConnectorType(), 20, 90, 0, reservation.getSlotStart());

        List<RecommendationResult> results = new ArrayList<>();
        for (Station station : stationService.stationsIn(original.getCityId())) {
            if (station.getId().equals(original.getId())) continue;
            RecommendationResult result = evaluate(station, request);
            if (result != null) {
                results.add(result);
            }
        }
        results.sort(Comparator.comparingInt(RecommendationResult::score).reversed());
        return results.size() > limit ? new ArrayList<>(results.subList(0, limit)) : results;
    }

    /** Returns null when the station does not satisfy the hard requirements. */
    private RecommendationResult evaluate(Station station, RecommendationRequest request) {
        ConnectorType connector = request.connectorType();
        List<Charger> compatible = station.getCompatibleChargers(connector, request.minPowerKw());
        if (compatible.isEmpty()) return null;

        double distance = stationService.distanceKm(station, request.startLocation());
        if (distance > request.maxDistanceKm()) return null;

        LocalTime arrival = request.arrivalTime();
        int wait = stationService.predictedWaitMinutes(station, arrival);
        if (wait > request.maxWaitMinutes()) return null;

        CongestionLevel congestion = stationService.predictedCongestion(station, arrival);
        int freeNow = (int) compatible.stream().filter(Charger::isAvailable).count();

        // Prefer a free charger; among those, the most powerful one.
        Charger best = compatible.stream()
                .max(Comparator.comparing(Charger::isAvailable).thenComparingDouble(Charger::getPowerKw))
                .orElse(compatible.get(0));

        double score = 85
                - distance * 2.5
                - wait * 0.8
                - congestion.getScorePenalty()
                + (freeNow > 0 ? 8 : -10)
                + Math.min(best.getPowerKw(), 150) / 15.0;
        int finalScore = (int) Math.max(1, Math.min(99, Math.round(score)));

        List<String> reasons = new ArrayList<>();
        reasons.add("Compatible " + connector + " charger available up to " + Format.power(best.getPowerKw()));
        reasons.add(freeNow > 0
                ? Format.plural(freeNow, "compatible charger") + " free right now"
                : "Compatible chargers are busy right now - a short queue is likely");
        reasons.add("Predicted wait of " + Format.minutes(wait) + " at " + arrival
                + " (your limit: " + request.maxWaitMinutes() + " min)");
        reasons.add(Format.distance(distance) + " from " + request.startLocation().name()
                + " (your limit: " + Format.distance(request.maxDistanceKm()) + ")");
        reasons.add("Congestion: " + congestion + " (from chargers in use or reserved now"
                + (StationService.isPeakHour(arrival) ? ", raised for peak hour)" : ")"));

        String summary = Format.distance(distance) + " away with a " + Format.minutes(wait)
                + " predicted wait and " + congestion.toString().toLowerCase() + " congestion.";

        return new RecommendationResult(station, best, distance, wait, congestion, freeNow, finalScore,
                summary, reasons);
    }

    /** Builds the plain-English explanation shown for the top recommendation. */
    private String topReason(List<RecommendationResult> results, RecommendationRequest request) {
        RecommendationResult top = results.get(0);
        boolean shortestWait = results.stream().allMatch(r -> top.predictedWaitMinutes() <= r.predictedWaitMinutes());
        boolean closest = results.stream().allMatch(r -> top.distanceKm() <= r.distanceKm());

        String text = "This station matches your " + request.connectorType() + " connector requirement";
        if (shortestWait && closest) {
            text += " and is both the closest and the quickest option.";
        } else if (shortestWait) {
            text += " and has the shortest predicted wait.";
        } else if (closest) {
            text += " and is the closest compatible station.";
        } else {
            text += " and offers the best overall balance of distance, waiting time and congestion.";
        }
        return text;
    }
}
