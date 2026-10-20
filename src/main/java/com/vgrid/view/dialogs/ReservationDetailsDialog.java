package com.vgrid.view.dialogs;

import com.vgrid.controller.AppController;
import com.vgrid.model.Reservation;
import com.vgrid.model.ReservationStatus;
import com.vgrid.util.Format;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.StatusBadge;
import com.vgrid.view.components.Ui;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Full details of one reservation, with cancel / alternative actions when it is still active. */
public class ReservationDetailsDialog extends DialogShell {

    public ReservationDetailsDialog(AppController app, Reservation r) {
        super(app, "Reservation " + r.getId(), "Booked on " + Format.dateTime(r.getCreatedAt()), 560);

        VBox stationTexts = new VBox(2, Ui.label(r.getStation().getName(), "card-title"),
                Ui.label(r.getStation().getAddress() + ", " + r.getStation().getArea(), "muted"));
        HBox.setHgrow(stationTexts, Priority.ALWAYS);
        HBox header = new HBox(12, Ui.iconTile(Icons.BOLT, "green"), stationTexts, StatusBadge.forReservation(r.getStatus()));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("selected-station");

        body.getChildren().addAll(header, Ui.infoGrid(new String[][]{
                {"Reservation ID", r.getId()},
                {"Status", r.getStatus().toString()},
                {"Charger", "Charger " + r.getCharger().getId()},
                {"Connector type", r.getCharger().getConnectorType().getDisplayName()},
                {"Charging power", Format.power(r.getCharger().getPowerKw())},
                {"Date", Format.longDate(r.getDate())},
                {"Time slot", r.getTimeSlot()},
                {"Price", Format.price(r.getStation().getPricePerKwh()) + " (sample tariff)"}
        }));

        if (r.getStatus() == ReservationStatus.ACTION_REQUIRED) {
            body.getChildren().add(Ui.notice(Icons.WARNING,
                    "The reserved charger is unavailable (simulated). Review compatible alternatives.", "warning"));
        } else if (r.getNote() != null) {
            body.getChildren().add(Ui.notice(Icons.INFO, r.getNote(), "info"));
        }

        Button close = Ui.secondaryButton("Close", null);
        close.setOnAction(e -> app.closeModal());

        if (r.isActive()) {
            Button cancel = Ui.dangerButton("Cancel Reservation", null);
            cancel.setOnAction(e -> app.requestCancel(r, app::closeModal));
            Button alternative = Ui.primaryButton("Find an Alternative", Icons.SWAP);
            alternative.setOnAction(e -> {
                app.closeModal();
                app.requestAlternative(r);
            });
            setFooter(cancel, Ui.hSpacer(), close, alternative);
        } else {
            setFooter(close);
        }
    }
}
