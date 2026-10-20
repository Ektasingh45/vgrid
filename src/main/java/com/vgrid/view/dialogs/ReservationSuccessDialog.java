package com.vgrid.view.dialogs;

import com.vgrid.controller.AppController;
import com.vgrid.controller.Screen;
import com.vgrid.model.Reservation;
import com.vgrid.util.Format;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Shown after a successful (simulated) booking. */
public class ReservationSuccessDialog extends VBox {

    public ReservationSuccessDialog(AppController app, Reservation reservation) {
        super(12);
        getStyleClass().addAll("modal-card", "success-card");
        setAlignment(Pos.TOP_CENTER);
        setPrefWidth(500);
        setMaxWidth(500);

        Label id = Ui.label(reservation.getId(), "success-id");

        VBox summary = new VBox(10, Ui.infoGrid(new String[][]{
                {"Station", reservation.getStation().getName()},
                {"Charger", reservation.getCharger().getLabel()},
                {"Date", Format.longDate(reservation.getDate())},
                {"Time slot", reservation.getTimeSlot()},
                {"Status", reservation.getStatus().toString()}
        }));
        summary.getStyleClass().add("summary-box");
        summary.setMaxWidth(Double.MAX_VALUE);

        Button done = Ui.grow(Ui.secondaryButton("Done", null));
        done.setOnAction(e -> app.closeModal());
        Button view = Ui.grow(Ui.primaryButton("View My Reservations", Icons.CALENDAR));
        view.setOnAction(e -> app.navigate(Screen.RESERVATIONS));
        HBox buttons = new HBox(10, done, view);
        VBox.setMargin(buttons, new Insets(8, 0, 0, 0));

        getChildren().addAll(
                Ui.iconTile(Icons.CHECK, "success", 64, 32),
                Ui.label("Reservation confirmed!", "dialog-title"),
                Ui.wrapLabel("Your demo reservation ID is", "dialog-message"),
                id,
                summary,
                Ui.wrapLabel("This is a simulated reservation for demonstration only - no real charger has been booked.",
                        "small-muted", "center-text"),
                buttons);
    }
}
