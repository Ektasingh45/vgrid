package com.vgrid.view.components;

import com.vgrid.model.ChargerStatus;
import com.vgrid.model.CongestionLevel;
import com.vgrid.model.ReservationStatus;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/** Rounded coloured labels used for statuses. tone = success | warning | danger | neutral */
public final class StatusBadge {
    private StatusBadge() {
    }

    public static Label of(String text, String tone) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", "badge-" + tone);
        badge.setMinWidth(Region.USE_PREF_SIZE);
        return badge;
    }

    public static Label forCongestion(CongestionLevel level) {
        return of(level + " congestion", switch (level) {
            case LOW -> "success";
            case MODERATE -> "warning";
            case HIGH -> "danger";
        });
    }

    public static Label forReservation(ReservationStatus status) {
        return of(status.toString(), switch (status) {
            case CONFIRMED -> "success";
            case ACTION_REQUIRED -> "warning";
            case COMPLETED -> "neutral";
            case CANCELLED -> "danger";
        });
    }

    public static Label forCharger(ChargerStatus status) {
        return of(status.toString(), switch (status) {
            case AVAILABLE -> "success";
            case RESERVED -> "neutral";
            case IN_USE -> "warning";
            case OUT_OF_SERVICE -> "danger";
        });
    }
}
