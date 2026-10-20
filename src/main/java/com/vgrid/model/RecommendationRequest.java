package com.vgrid.model;

import java.time.LocalTime;

/** What the user entered on the Smart Recommendations form. */
public record RecommendationRequest(
        Location startLocation,
        ConnectorType connectorType,
        double maxDistanceKm,
        int maxWaitMinutes,
        double minPowerKw,
        LocalTime arrivalTime) {
}
