package com.vgrid.util;

/** Distances on the earth's surface. */
public final class Geo {
    private static final double EARTH_RADIUS_KM = 6371.0;

    private Geo() {
    }

    /**
     * Haversine formula: the straight-line ("as the crow flies") distance in km between two
     * latitude/longitude points, taking the curve of the earth into account.
     */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
