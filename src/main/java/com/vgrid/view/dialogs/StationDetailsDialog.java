package com.vgrid.view.dialogs;

import com.vgrid.controller.AppController;
import com.vgrid.model.Charger;
import com.vgrid.model.Station;
import com.vgrid.util.Format;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.StatusBadge;
import com.vgrid.view.components.Ui;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Complete information about one station, including every charger. */
public class StationDetailsDialog extends DialogShell {

    public StationDetailsDialog(AppController app, Station station) {
        super(app, station.getName(), station.getAddress() + ", " + station.getArea(), 640);

        double distance = app.getStationService().distanceFromHome(station);

        Label rating = Ui.label(String.format("%.1f rating", station.getRating()), "chip");
        rating.setGraphic(Icons.create(Icons.STAR, 13, "icon-amber"));
        FlowPane badges = new FlowPane(8, 8,
                StatusBadge.forCongestion(station.getCongestion()),
                StatusBadge.of(station.getAvailableChargers() + " of " + station.getTotalChargers() + " chargers free",
                        station.hasAvailableCharger() ? "success" : "warning"),
                rating,
                Ui.chip(station.getOpenHours()));

        var info = Ui.infoGrid(new String[][]{
                {"Operator", station.getOperator()},
                {"Distance", Format.distance(distance) + " from " + app.getStationService().getHomeLocation().name()},
                {"Price", Format.price(station.getPricePerKwh()) + " (sample tariff)"},
                {"Average waiting", Format.minutes(station.getAverageWaitMinutes()) + " (simulated)"},
                {"Congestion", station.getCongestion() + " (simulated)"},
                {"Opening hours", station.getOpenHours()}
        });

        VBox chargerList = new VBox(8);
        for (Charger charger : station.getChargers()) {
            VBox texts = new VBox(1,
                    Ui.label("Charger " + charger.getId() + " · " + charger.getConnectorType(), "list-title"),
                    Ui.label(charger.getConnectorType().getDescription() + " · " + Format.power(charger.getPowerKw()),
                            "small-muted"));
            HBox.setHgrow(texts, Priority.ALWAYS);
            HBox row = new HBox(12, Ui.iconTile(Icons.PLUG, charger.isAvailable() ? "green" : "soft", 36, 18),
                    texts, StatusBadge.forCharger(charger.getStatus()));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("list-row");
            chargerList.getChildren().add(row);
        }

        FlowPane amenities = new FlowPane(6, 6);
        station.getAmenities().forEach(a -> amenities.getChildren().add(Ui.chip(a)));

        body.getChildren().addAll(
                badges,
                info,
                new VBox(10, Ui.label("Chargers", "subsection-title"), chargerList),
                new VBox(10, Ui.label("Amenities", "subsection-title"), amenities),
                Ui.notice(Icons.INFO, "Availability, waiting time and congestion are simulated values for this demo.", "info"));

        Button close = Ui.secondaryButton("Close", null);
        close.setOnAction(e -> app.closeModal());
        Button reserve = Ui.primaryButton("Reserve Charger", Icons.BOLT);
        reserve.setOnAction(e -> {
            app.closeModal();
            app.showReservationForm(station, null);
        });
        setFooter(close, reserve);
    }
}
