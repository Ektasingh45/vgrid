package com.vgrid.view;

import com.vgrid.controller.AppController;
import com.vgrid.controller.Screen;
import com.vgrid.model.RecommendationResult;
import com.vgrid.model.Reservation;
import com.vgrid.model.ReservationStatus;
import com.vgrid.model.Station;
import com.vgrid.util.Format;
import com.vgrid.view.components.*;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reservation protection: the reserved charger was reported out of service and the user
 * picks a compatible alternative station for the same date and time slot.
 */
public class AlternativesView extends BaseView {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final Reservation reservation;
    private final Map<RecommendationResult, Node> cards = new HashMap<>();
    private final Label selectionLabel = Ui.wrapLabel("Select an alternative station to continue", "muted");
    private final Button confirmButton = Ui.primaryButton("Confirm Change", Icons.CHECK);
    private RecommendationResult selected;

    public AlternativesView(AppController app, Reservation reservation) {
        super(app);
        this.reservation = reservation;

        Button back = Ui.ghostButton("Back to My Reservations", Icons.ARROW_LEFT);
        back.setOnAction(e -> app.navigate(Screen.RESERVATIONS));
        content.getChildren().add(new HBox(back));

        if (reservation == null) {
            content.getChildren().add(new EmptyState(Icons.CHECK_CIRCLE, "No reservation needs an alternative",
                    "All your reservations are on track. Use \"Find an Alternative\" on a reservation to try the demo.",
                    "Go to My Reservations", () -> app.navigate(Screen.RESERVATIONS)));
            return;
        }
        if (reservation.getStatus() != ReservationStatus.ACTION_REQUIRED) {
            content.getChildren().add(new EmptyState(Icons.CHECK_CIRCLE, "Reservation " + reservation.getId() + " is on track",
                    "It is " + reservation.getStatus().toString().toLowerCase() + " at " + reservation.getStation().getName()
                            + ". There is nothing to change.",
                    "Go to My Reservations", () -> app.navigate(Screen.RESERVATIONS)));
            return;
        }

        List<RecommendationResult> alternatives = app.getReservationService().findAlternatives(reservation, 5);

        content.getChildren().addAll(buildBanner(), Ui.sectionHeader("Compatible alternatives",
                "Same connector (" + reservation.getCharger().getConnectorType() + "), same date and time slot · "
                        + "distances measured from " + reservation.getStation().getName()));

        if (alternatives.isEmpty()) {
            content.getChildren().add(new EmptyState(Icons.SEARCH, "No compatible alternatives found",
                    "No other station has a working " + reservation.getCharger().getConnectorType()
                            + " charger nearby. You can cancel this reservation instead.",
                    "Cancel Reservation", () -> app.requestCancel(reservation, () -> app.navigate(Screen.RESERVATIONS))));
            return;
        }

        VBox list = new VBox(12);
        for (int i = 0; i < alternatives.size(); i++) {
            list.getChildren().add(buildOption(alternatives.get(i), i == 0));
        }
        content.getChildren().addAll(list, buildActionBar());
    }

    private Node buildBanner() {
        VBox texts = new VBox(4,
                Ui.wrapLabel("Your reserved charger is unavailable. Review these compatible alternatives.", "banner-title"),
                Ui.wrapLabel("Reservation " + reservation.getId() + " · " + reservation.getStation().getName()
                        + " · Charger " + reservation.getCharger().getLabel() + " · "
                        + Format.date(reservation.getDate()) + ", " + reservation.getTimeSlot(), "banner-text"));
        HBox.setHgrow(texts, Priority.ALWAYS);
        HBox banner = new HBox(14, Ui.iconTile(Icons.WARNING, "amber", 46, 22), texts,
                StatusBadge.of("SIMULATED EVENT", "warning"));
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add("banner-warning");
        return banner;
    }

    private Node buildOption(RecommendationResult option, boolean recommended) {
        Station station = option.station();

        Region radio = new Region();
        radio.getStyleClass().add("radio-dot");

        HBox nameRow = new HBox(10, Ui.label(station.getName(), "card-title"));
        if (recommended) {
            nameRow.getChildren().add(StatusBadge.of("Recommended", "success"));
        }
        nameRow.setAlignment(Pos.CENTER_LEFT);

        FlowPane facts = new FlowPane(16, 8,
                Ui.iconText(Icons.PIN, Format.distance(option.distanceKm()) + " away"),
                Ui.iconText(Icons.CHECK_CIRCLE, option.bestCharger().getConnectorType() + " compatible"),
                Ui.iconText(Icons.PLUG, option.compatibleAvailable() > 0
                        ? option.compatibleAvailable() + " free now" : "Busy now"),
                Ui.iconText(Icons.BOLT, Format.power(option.bestCharger().getPowerKw())),
                Ui.iconText(Icons.CLOCK, Format.minutes(option.predictedWaitMinutes()) + " wait"));

        VBox center = new VBox(6, nameRow, Ui.label(station.getArea() + " · Charger " + option.bestCharger().getId(), "muted"), facts);
        HBox.setHgrow(center, Priority.ALWAYS);

        Button details = Ui.ghostButton("Details", null);
        details.setOnAction(e -> app.showStationDetails(station));
        VBox right = new VBox(8, StatusBadge.forCongestion(option.predictedCongestion()), details);
        right.setAlignment(Pos.TOP_RIGHT);

        HBox card = new HBox(16, radio, center, right);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("card", "alt-card");
        card.setOnMouseClicked(e -> select(option));
        cards.put(option, card);
        return card;
    }

    private Node buildActionBar() {
        confirmButton.setDisable(true);
        confirmButton.setOnAction(e -> confirmChange());

        Button cancelInstead = Ui.dangerButton("Cancel Reservation Instead", null);
        cancelInstead.setOnAction(e -> app.requestCancel(reservation, () -> app.navigate(Screen.RESERVATIONS)));

        HBox.setHgrow(selectionLabel, Priority.ALWAYS);
        selectionLabel.setMaxWidth(Double.MAX_VALUE);
        HBox bar = new HBox(12, selectionLabel, cancelInstead, confirmButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("card", "action-bar");
        return bar;
    }

    private void select(RecommendationResult option) {
        selected = option;
        cards.forEach((result, node) -> node.pseudoClassStateChanged(SELECTED, result == option));
        selectionLabel.setText("Selected: " + option.station().getName() + " · Charger " + option.bestCharger().getLabel());
        selectionLabel.getStyleClass().setAll("label", "selection-text");
        confirmButton.setDisable(false);
    }

    private void confirmChange() {
        if (selected == null) {
            app.showToast("Please select an alternative first", AppController.ToastType.ERROR);
            return;
        }
        RecommendationResult choice = selected;
        app.confirm("Switch to " + choice.station().getName() + "?",
                "Reservation " + reservation.getId() + " will move to charger " + choice.bestCharger().getLabel()
                        + " on " + Format.longDate(reservation.getDate()) + ", " + reservation.getTimeSlot()
                        + ". VGrid checks that this slot is still free before moving it.",
                "Confirm Change", false, () -> {
                    try {
                        app.getReservationService().switchToAlternative(reservation, choice.station(), choice.bestCharger());
                    } catch (IllegalArgumentException ex) {
                        app.showToast(ex.getMessage(), AppController.ToastType.ERROR);
                        app.openAlternatives(app.getReservationService().findById(reservation.getId()));
                        return;
                    }
                    app.navigate(Screen.RESERVATIONS);
                    app.showToast(reservation.getId() + " moved to " + choice.station().getName(),
                            AppController.ToastType.SUCCESS);
                });
    }
}
