package com.vgrid.model;

/** Simulated congestion level of a station. The penalty is used by the ranking logic. */
public enum CongestionLevel {
    LOW("Low", 0),
    MODERATE("Moderate", 12),
    HIGH("High", 25);

    private final String displayName;
    private final int scorePenalty;

    CongestionLevel(String displayName, int scorePenalty) {
        this.displayName = displayName;
        this.scorePenalty = scorePenalty;
    }

    public int getScorePenalty() {
        return scorePenalty;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
