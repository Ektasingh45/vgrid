package com.vgrid.model;

import java.util.List;

/** One ranked station produced by the recommendation logic. */
public record RecommendationResult(
        Station station,
        Charger bestCharger,
        double distanceKm,
        int predictedWaitMinutes,
        CongestionLevel predictedCongestion,
        int compatibleAvailable,
        int score,
        String summary,
        List<String> reasons) {

    public RecommendationResult withSummary(String newSummary) {
        return new RecommendationResult(station, bestCharger, distanceKm, predictedWaitMinutes,
                predictedCongestion, compatibleAvailable, score, newSummary, reasons);
    }
}
