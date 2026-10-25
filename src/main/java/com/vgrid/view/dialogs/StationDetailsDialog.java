package com.vgrid.view.dialogs;

import com.vgrid.controller.AppController;
import com.vgrid.model.Charger;
import com.vgrid.model.ChargerStatus;
import com.vgrid.model.ChargingSession;
import com.vgrid.model.OcppChargerState;
import com.vgrid.model.Station;
import com.vgrid.util.Format;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.StatusBadge;
import com.vgrid.view.components.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Complete information about one station. Every charger has live actions:
 * Start charging / Done (check-in), Report problem, or "It works now" for a reported charger.
 */
public class StationDetailsDialog extends DialogShell {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public StationDetailsDialog(AppController app, Station station) {
        super(app, station.getName(), station.getAddress() + ", " + station.getArea(), 680);

        double distance = app.getStationService().distanceFromHome(station);
        long busy = station.getChargers().stream()
                .filter(c -> c.getStatus() == ChargerStatus.IN_USE || c.getStatus() == ChargerStatus.RESERVED).count();

        FlowPane badges = new FlowPane(8, 8,
                StatusBadge.forCongestion(station.getCongestion()),
                StatusBadge.of(station.getAvailableChargers() + " of " + station.getTotalChargers() + " chargers free",
                        station.hasAvailableCharger() ? "success" : "warning"));
        if (station.hasRating()) {
            Label rating = Ui.label(String.format("%.1f rating", station.getRating()), "chip");
            rating.setGraphic(Icons.create(Icons.STAR, 13, "icon-amber"));
            badges.getChildren().add(rating);
        }
        badges.getChildren().add(Ui.chip(station.getOpenHours()));

        var info = Ui.infoGrid(new String[][]{
                {"Operator", station.getOperator()},
                {"Distance", Format.distance(distance) + " from " + app.getStationService().getHomeLocation().name()},
                {"Price", Format.price(station.getPricePerKwh()) + " " + station.priceNote()},
                {"Waiting now", station.getWorkingChargers().isEmpty() ? "No working charger at the moment"
                        : Format.minutes(station.getAverageWaitMinutes()) + " (estimated from free chargers)"},
                {"Congestion now", station.getCongestion() + " · " + busy + " of "
                        + station.getWorkingChargers().size() + " working chargers reserved or in use"},
                {"Opening hours", station.getOpenHours()},
                {"Station data", station.getSource()}
        });

        Optional<ChargingSession> mine = app.getChargingService().getActiveSession();
        VBox chargerList = new VBox(8);
        for (Charger charger : station.getChargers()) {
            chargerList.getChildren().add(chargerRow(app, station, charger, mine.orElse(null)));
        }

        body.getChildren().addAll(badges, info, new VBox(10, Ui.label("Chargers", "subsection-title"), chargerList));
        if (!station.getAmenities().isEmpty()) {
            FlowPane amenities = new FlowPane(6, 6);
            station.getAmenities().forEach(a -> amenities.getChildren().add(Ui.chip(a)));
            body.getChildren().add(new VBox(10, Ui.label(station.isImported() ? "Access & cost" : "Amenities",
                    "subsection-title"), amenities));
        }
        String sourceNote;
        if (station.isImported()) {
            sourceNote = "Name, address, operator, connectors and power come from Open Charge Map (© Open Charge Map "
                    + "contributors, CC BY 4.0). Live status comes from VGrid bookings, check-ins and driver reports.";
        } else if (station.isOcppTestStation()) {
            sourceNote = "Test station for the OCPP demo: its chargers report their own status (Available, Charging, "
                    + "Faulted...) to the VGrid OCPP server, so nobody has to check in or press Done.";
        } else {
            sourceNote = "Built-in sample station (no Open Charge Map key). Live status comes from VGrid bookings, "
                    + "check-ins and driver reports.";
        }
        body.getChildren().add(Ui.notice(Icons.INFO, sourceNote, "info"));

        Button close = Ui.secondaryButton("Close", null);
        close.setOnAction(e -> app.closeModal());
        Button reserve = Ui.primaryButton("Reserve Charger", Icons.BOLT);
        reserve.setOnAction(e -> {
            app.closeModal();
            app.showReservationForm(station, null);
        });
        setFooter(close, reserve);
    }

    private static Node chargerRow(AppController app, Station station, Charger charger, ChargingSession mine) {
        OcppChargerState ocpp = app.getStationService().ocppStateAt(station, charger);
        if (ocpp != null) {
            return ocppChargerRow(charger, ocpp);
        }
        ChargingSession here = app.getStationService().sessionAt(station, charger);
        boolean mineHere = mine != null && mine.stationId().equals(station.getId()) && mine.chargerId().equals(charger.getId());

        String detail = charger.getConnectorType().getDescription() + " · " + Format.power(charger.getPowerKw());
        if (here != null) {
            detail += mineHere ? " · you started at " + here.startedAt().format(TIME)
                    : " · free by " + here.endsAt().format(TIME) + " at the latest";
        }
        VBox texts = new VBox(1,
                Ui.label("Charger " + charger.getId() + " · " + charger.getConnectorType(), "list-title"),
                Ui.label(detail, "small-muted"));
        HBox.setHgrow(texts, Priority.ALWAYS);
        texts.setMinWidth(0);

        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (charger.getStatus() == ChargerStatus.OUT_OF_SERVICE) {
            Button works = Ui.ghostButton("It works now", Icons.CHECK);
            works.setOnAction(e -> app.markChargerWorking(station, charger));
            actions.getChildren().add(works);
        } else if (mineHere) {
            Button done = Ui.primaryButton("Done charging", Icons.CHECK);
            done.setOnAction(e -> {
                app.closeModal();
                app.stopCharging();
            });
            actions.getChildren().add(done);
        } else if (here == null && mine == null
                && (charger.getStatus() != ChargerStatus.RESERVED || hasBookingNow(app, station, charger))) {
            Button start = Ui.secondaryButton("Start charging", Icons.BOLT);
            start.setOnAction(e -> app.startCharging(station, charger));
            actions.getChildren().add(start);
        }
        if (charger.getStatus() != ChargerStatus.OUT_OF_SERVICE && !mineHere) {
            Button report = Ui.ghostButton("Report problem", Icons.WARNING);
            report.getStyleClass().add("btn-ghost-danger");
            report.setOnAction(e -> {
                app.closeModal();
                app.requestReportProblem(station, charger);
            });
            actions.getChildren().add(report);
        }

        HBox row = new HBox(12, Ui.iconTile(Icons.PLUG, charger.isAvailable() ? "green" : "soft", 36, 18),
                texts, StatusBadge.forCharger(charger.getStatus()), actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        return row;
    }

    /** A charger connected to the OCPP server: its status comes from the charger, so there are no buttons. */
    private static Node ocppChargerRow(Charger charger, OcppChargerState ocpp) {
        boolean online = ocpp.isOnline(LocalDateTime.now());
        String detail = charger.getConnectorType().getDescription() + " · " + Format.power(charger.getPowerKw());
        if (!online) {
            detail += " · charger offline" + (ocpp.lastSeen() == null ? "" : ", last seen " + Format.relative(ocpp.lastSeen()));
        } else {
            detail += " · reports \"" + (ocpp.status() == null ? "connected" : ocpp.status()) + "\"";
            if (ocpp.transactionActive()) {
                detail += " · " + String.format("%.2f kWh delivered", ocpp.energyKwh());
            }
        }
        VBox texts = new VBox(1,
                Ui.label("Charger " + charger.getId() + " · " + charger.getConnectorType(), "list-title"),
                Ui.label(detail, "small-muted"));
        HBox.setHgrow(texts, Priority.ALWAYS);
        texts.setMinWidth(0);

        Label live = StatusBadge.of(online ? "Live · OCPP" : "Offline", online ? "success" : "neutral");
        HBox row = new HBox(12, Ui.iconTile(Icons.PLUG, charger.isAvailable() ? "green" : "soft", 36, 18),
                texts, StatusBadge.forCharger(charger.getStatus()), live);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        return row;
    }

    /** True when the signed-in user holds this charger's booking for the current hour. */
    private static boolean hasBookingNow(AppController app, Station station, Charger charger) {
        LocalDate today = LocalDate.now();
        LocalTime hour = LocalTime.now().truncatedTo(ChronoUnit.HOURS);
        return app.getReservationService().getAllReservations().stream().anyMatch(r -> r.isActive()
                && r.getStation().getId().equals(station.getId()) && r.getCharger().getId().equals(charger.getId())
                && r.getDate().equals(today) && r.getSlotStart().equals(hour));
    }
}
