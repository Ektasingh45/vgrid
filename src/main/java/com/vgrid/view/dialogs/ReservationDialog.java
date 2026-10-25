package com.vgrid.view.dialogs;

import com.vgrid.controller.AppController;
import com.vgrid.model.Charger;
import com.vgrid.model.Reservation;
import com.vgrid.model.Station;
import com.vgrid.service.ReservationService;
import com.vgrid.util.Format;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.Ui;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.LocalTime;

/** Booking form: choose charger, date and time slot, review the summary, confirm. */
public class ReservationDialog extends DialogShell {
    private static final double CHARGE_HOURS = 1.0;
    private static final double TYPICAL_BATTERY_KWH = 40.0;

    private final Station station;
    private final ComboBox<Charger> chargerBox = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker();
    private final FlowPane slotPane = new FlowPane(8, 8);
    private final ToggleGroup slotGroup = new ToggleGroup();
    private final Label slotHint = Ui.label("", "small-muted");
    private final Label connectorValue = Ui.label("", "metric-value");
    private final Label powerValue = Ui.label("", "metric-value");
    private final VBox summaryBox = new VBox(10);
    private final Label errorLabel = Ui.wrapLabel("", "form-error");

    public ReservationDialog(AppController app, Station station, Charger preferredCharger) {
        super(app, "Reserve a charger", app.isDatabaseMode()
                ? "Your booking is saved and blocks this slot for every other driver"
                : "Offline mode - this booking is kept only until the app closes", 620);
        this.station = station;

        // Selected station
        VBox stationTexts = new VBox(2, Ui.label(station.getName(), "card-title"),
                Ui.label(station.getAddress() + ", " + station.getArea() + " · "
                        + Format.distance(app.getStationService().distanceFromHome(station)) + " away", "muted"));
        HBox.setHgrow(stationTexts, Priority.ALWAYS);
        HBox stationBox = new HBox(12, Ui.iconTile(Icons.BOLT, "green"), stationTexts);
        stationBox.setAlignment(Pos.CENTER_LEFT);
        stationBox.getStyleClass().add("selected-station");

        // Charger
        chargerBox.getItems().setAll(station.getWorkingChargers());
        chargerBox.setCellFactory(list -> new ChargerCell());
        chargerBox.setButtonCell(new ChargerCell());
        Charger initial = preferredCharger != null && chargerBox.getItems().contains(preferredCharger)
                ? preferredCharger
                : chargerBox.getItems().stream().filter(Charger::isAvailable).findFirst()
                .orElse(chargerBox.getItems().isEmpty() ? null : chargerBox.getItems().get(0));
        chargerBox.setValue(initial);

        GridPane chargerInfo = Ui.twoColumnGrid(12, 12,
                metricBox(Icons.PLUG, "Connector type", connectorValue),
                metricBox(Icons.BOLT, "Charging power", powerValue));

        // Date: today up to 14 days ahead
        LocalDate today = LocalDate.now();
        boolean dayIsOver = LocalTime.now().isAfter(LocalTime.of(20, 0));
        datePicker.setValue(dayIsOver ? today.plusDays(1) : today);
        datePicker.setEditable(false);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(today)
                        || date.isAfter(today.plusDays(ReservationService.MAX_DAYS_AHEAD)));
            }
        });

        HBox legend = new HBox(12, legendDot("legend-free", "Available"), legendDot("legend-booked", "Booked / passed"));
        HBox slotHeader = new HBox(8, Ui.label("Available time slots", "field-label"), Ui.hSpacer(), legend);
        slotHeader.setAlignment(Pos.CENTER_LEFT);

        summaryBox.getStyleClass().add("summary-box");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        body.getChildren().addAll(
                stationBox,
                Ui.field("Charger", chargerBox),
                chargerInfo,
                Ui.field("Reservation date", datePicker),
                new VBox(10, slotHeader, slotPane, slotHint),
                summaryBox,
                errorLabel,
                Ui.notice(Icons.INFO, "This creates a demo reservation in memory only. No real charger is booked.", "info"));

        chargerBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            updateChargerInfo();
            rebuildSlots();
        });
        datePicker.valueProperty().addListener((obs, oldValue, newValue) -> rebuildSlots());
        slotGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> {
            hideError();
            updateSummary();
        });

        updateChargerInfo();
        rebuildSlots();

        Button cancel = Ui.secondaryButton("Cancel", null);
        cancel.setOnAction(e -> app.closeModal());
        Button confirm = Ui.primaryButton("Confirm Reservation", Icons.CHECK);
        confirm.setDefaultButton(true);
        confirm.setDisable(chargerBox.getItems().isEmpty());
        confirm.setOnAction(e -> confirmReservation());
        setFooter(cancel, confirm);
    }

    private void updateChargerInfo() {
        Charger charger = chargerBox.getValue();
        connectorValue.setText(charger == null ? "-" : charger.getConnectorType().getDisplayName());
        powerValue.setText(charger == null ? "-" : Format.power(charger.getPowerKw()));
    }

    /** Recreates the time-slot buttons for the selected charger and date. */
    private void rebuildSlots() {
        slotGroup.getToggles().clear();
        slotPane.getChildren().clear();
        Charger charger = chargerBox.getValue();
        LocalDate date = datePicker.getValue();
        if (charger == null || date == null) {
            slotHint.setText("Choose a charger and a date to see time slots.");
            updateSummary();
            return;
        }

        ReservationService service = app.getReservationService();
        int free = 0;
        for (String slot : ReservationService.TIME_SLOTS) {
            boolean available = service.isSlotAvailable(station, charger, date, slot);
            String state = available ? "Available" : (service.isSlotInPast(date, slot) ? "Passed" : "Booked");
            VBox text = new VBox(0, Ui.label(slot, "slot-time"), Ui.label(state, "slot-state"));
            text.setAlignment(Pos.CENTER);
            ToggleButton button = new ToggleButton();
            button.setGraphic(text);
            button.setUserData(slot);
            button.setToggleGroup(slotGroup);
            button.getStyleClass().add("slot-button");
            button.setDisable(!available);
            slotPane.getChildren().add(button);
            if (available) free++;
        }
        slotHint.setText(free == 0
                ? "No free slots on this date. Please choose another day."
                : free + " of " + ReservationService.TIME_SLOTS.size() + " slots available on " + Format.date(date)
                + " · grey slots are booked by drivers or already over.");
        updateSummary();
    }

    private void updateSummary() {
        Charger charger = chargerBox.getValue();
        LocalDate date = datePicker.getValue();
        String slot = selectedSlot();

        String energyText = "-";
        if (charger != null) {
            double energy = Math.min(charger.getPowerKw() * CHARGE_HOURS, TYPICAL_BATTERY_KWH);
            energyText = String.format("Up to %.0f kWh · approx. %s", energy,
                    Format.rupees(energy * station.getPricePerKwh()));
        }

        summaryBox.getChildren().setAll(
                Ui.label("Booking summary", "subsection-title"),
                Ui.infoGrid(new String[][]{
                        {"Station", station.getName()},
                        {"Charger", charger == null ? "-" : charger.getLabel()},
                        {"Date", date == null ? "-" : Format.longDate(date)},
                        {"Time slot", slot == null ? "Not selected yet" : slot},
                        {"Estimated charge", energyText + " (1 hour, price " + station.priceNote().replaceAll("[()]", "") + ")"}
                }));
    }

    private String selectedSlot() {
        Toggle toggle = slotGroup.getSelectedToggle();
        return toggle == null ? null : (String) toggle.getUserData();
    }

    private void confirmReservation() {
        try {
            Reservation reservation = app.getReservationService().createReservation(
                    station, chargerBox.getValue(), datePicker.getValue(), selectedSlot());
            app.closeModal();
            app.notifyDataChanged();
            app.showModal(new ReservationSuccessDialog(app, reservation));
        } catch (IllegalArgumentException ex) {
            errorLabel.setText(ex.getMessage());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private static HBox metricBox(String icon, String caption, Label value) {
        VBox texts = new VBox(1, Ui.label(caption, "metric-caption"), value);
        HBox box = new HBox(10, Ui.iconTile(icon, "soft", 32, 15), texts);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("metric-box");
        return box;
    }

    private static HBox legendDot(String styleClass, String text) {
        Region dot = new Region();
        dot.getStyleClass().addAll("legend-dot", styleClass);
        HBox box = new HBox(6, dot, Ui.label(text, "small-muted"));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Shows a charger as "C1 · CCS2 · 60 kW  (Available now)" in the drop-down. */
    private static class ChargerCell extends ListCell<Charger> {
        @Override
        protected void updateItem(Charger charger, boolean empty) {
            super.updateItem(charger, empty);
            setText(empty || charger == null ? null
                    : charger.getLabel() + "   (" + (charger.isAvailable() ? "Available now" : "In use now") + ")");
        }
    }
}
