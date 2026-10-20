package com.vgrid.view.components;

import com.vgrid.controller.AppController;
import com.vgrid.model.ConnectorType;
import com.vgrid.model.Station;
import com.vgrid.util.Format;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;

/** Reusable card that summarises one station, with View Details and Reserve Charger buttons. */
public class StationCard extends VBox {

    public StationCard(AppController app, Station station) {
        super(16);
        getStyleClass().addAll("card", "card-hover", "station-card");

        double distance = app.getStationService().distanceFromHome(station);
        int available = station.getAvailableChargers();
        int total = station.getTotalChargers();

        // Header: icon, name, area, congestion badge
        Label name = Ui.label(station.getName(), "card-title");
        Label area = Ui.label(station.getArea() + " · " + station.getOperator(), "muted");
        VBox titles = new VBox(2, name, area);
        titles.setMinWidth(0);
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox header = new HBox(12, Ui.iconTile(Icons.BOLT, available > 0 ? "green" : "amber"), titles);
        header.setAlignment(Pos.CENTER_LEFT);

        // Key numbers
        GridPane metrics = Ui.twoColumnGrid(12, 12,
                Ui.metric(Icons.PIN, "Distance", Format.distance(distance)),
                Ui.metric(Icons.PLUG, "Available", available + " of " + total),
                Ui.metric(Icons.BOLT, "Charging power", "Up to " + Format.power(station.getMaxPowerKw())),
                Ui.metric(Icons.CLOCK, "Est. waiting", Format.minutes(station.getAverageWaitMinutes())));

        // Connector chips + congestion badge
        FlowPane chips = new FlowPane(6, 6);
        for (ConnectorType type : station.getConnectorTypes()) {
            chips.getChildren().add(Ui.chip(type.getDisplayName()));
        }
        HBox.setHgrow(chips, Priority.ALWAYS);
        HBox connectors = new HBox(8, chips, StatusBadge.forCongestion(station.getCongestion()));
        connectors.setAlignment(Pos.CENTER_LEFT);

        // Availability bar
        ProgressBar bar = new ProgressBar(total == 0 ? 0 : (double) available / total);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add(available == 0 ? "bar-amber" : "bar-green");
        Label barText = Ui.label(available > 0
                ? Format.plural(available, "charger") + " free now"
                : "All chargers busy - join the queue", "small-muted");
        Label congestion = Ui.label("Predicted congestion: " + station.getCongestion() + " (simulated)", "small-muted");
        VBox availability = new VBox(6, bar, new HBox(8, barText), congestion);

        Button details = Ui.grow(Ui.secondaryButton("View Details", null));
        details.setOnAction(e -> app.showStationDetails(station));
        Button reserve = Ui.grow(Ui.primaryButton("Reserve Charger", Icons.BOLT));
        reserve.setOnAction(e -> app.showReservationForm(station, null));
        HBox actions = new HBox(10, details, reserve);

        Region push = new Region();
        VBox.setVgrow(push, Priority.ALWAYS);
        getChildren().addAll(header, metrics, connectors, availability, push, actions);
        Ui.hoverLift(this);
    }
}
