package com.vgrid.view;

import com.vgrid.controller.AppController;
import com.vgrid.model.ConnectorType;
import com.vgrid.model.Location;
import com.vgrid.model.UserSettings;
import com.vgrid.util.Format;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.ResponsiveGrid;
import com.vgrid.view.components.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;

/** Preferences kept in memory for the current session. */
public class SettingsView extends BaseView {
    private static final List<Double> POWER_OPTIONS = List.of(7.4, 22.0, 30.0, 50.0, 60.0, 120.0);

    private final ComboBox<Location> homeBox = new ComboBox<>();
    private final ComboBox<ConnectorType> connectorBox = new ComboBox<>();
    private final ComboBox<Double> powerBox = new ComboBox<>();
    private final Slider distanceSlider = new Slider(1, 30, 10);
    private final Slider waitSlider = new Slider(5, 60, 20);
    private final CheckBox reservationUpdates = new CheckBox("Reservation confirmations and cancellations");
    private final CheckBox chargerAlerts = new CheckBox("Charger unavailability alerts");
    private final CheckBox alternativeSuggestions = new CheckBox("Alternative charger suggestions");
    private final ToggleGroup deliveryGroup = new ToggleGroup();
    private final RadioButton inAppOnly = new RadioButton("In-app alerts only");
    private final RadioButton inAppAndEmail = new RadioButton("In-app + email updates (simulated - no email is sent)");
    private final Label errorLabel = Ui.wrapLabel("", "form-error");

    public SettingsView(AppController app) {
        super(app);

        homeBox.getItems().setAll(app.getStationService().getLocations());
        connectorBox.getItems().setAll(ConnectorType.values());
        powerBox.getItems().setAll(POWER_OPTIONS);
        powerBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double kw) {
                return kw == null ? "" : Format.power(kw);
            }

            @Override
            public Double fromString(String text) {
                return null;
            }
        });
        inAppOnly.setToggleGroup(deliveryGroup);
        inAppAndEmail.setToggleGroup(deliveryGroup);
        Ui.keepOneSelected(deliveryGroup);

        content.getChildren().addAll(buildPreferencesCard(), buildNotificationsCard(), buildDemoCard(), buildActionBar());
        loadFrom(app.getSettings());
    }

    private Node buildPreferencesCard() {
        ResponsiveGrid grid = new ResponsiveGrid(260, 18);
        grid.getChildren().addAll(
                Ui.field("Home / usual starting location", homeBox),
                Ui.field("Preferred connector", connectorBox),
                Ui.field("Preferred charging power", powerBox),
                sliderField("Maximum travel distance", distanceSlider, "km"),
                sliderField("Maximum waiting time", waitSlider, "min"));
        VBox card = Ui.card();
        card.getChildren().addAll(cardHeader(Icons.TUNE, "Charging preferences",
                "Used by the dashboard, station distances and Smart Recommendations."), grid);
        return card;
    }

    private Node buildNotificationsCard() {
        VBox types = new VBox(12, reservationUpdates, chargerAlerts, alternativeSuggestions);
        VBox delivery = new VBox(12, inAppOnly, inAppAndEmail);
        VBox card = Ui.card();
        card.getChildren().addAll(cardHeader(Icons.BELL, "Notifications", "Choose which alerts VGrid shows you."),
                new VBox(10, Ui.label("Alert types", "field-label"), types),
                new Separator(),
                new VBox(10, Ui.label("Delivery", "field-label"), delivery));
        return card;
    }

    private Node buildDemoCard() {
        Button reset = Ui.dangerButton("Reset Demo Data", Icons.REFRESH);
        reset.setOnAction(e -> app.confirm("Reset all demo data?",
                "Stations, reservations, alerts and settings will return to their original sample values. "
                        + "Anything you created in this session will be removed.",
                "Reset Demo Data", true, app::resetDemoData));
        HBox row = new HBox(12, Ui.wrapLabel("Everything you change in this demo lives only in memory and is lost when "
                + "the app closes. You can also restore the original sample data at any time.", "muted"), Ui.hSpacer(), reset);
        HBox.setHgrow(row.getChildren().get(0), Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = Ui.card();
        card.getChildren().addAll(cardHeader(Icons.INFO, "Demo data", "This frontend runs without a database or server."), row);
        return card;
    }

    private Node buildActionBar() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        Button restore = Ui.secondaryButton("Restore Defaults", Icons.REFRESH);
        restore.setOnAction(e -> {
            loadFrom(UserSettings.defaults(app.getStationService().getLocations().get(0)));
            app.showToast("Defaults restored - select Save Changes to apply", AppController.ToastType.INFO);
        });
        Button save = Ui.primaryButton("Save Changes", Icons.CHECK);
        save.setDefaultButton(true);
        save.setOnAction(e -> save());

        HBox bar = new HBox(12, errorLabel, Ui.hSpacer(), restore, save);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("card", "action-bar");
        return bar;
    }

    private void loadFrom(UserSettings s) {
        homeBox.setValue(s.getHomeLocation());
        connectorBox.setValue(s.getPreferredConnector());
        powerBox.setValue(s.getPreferredPowerKw());
        distanceSlider.setValue(s.getMaxDistanceKm());
        waitSlider.setValue(s.getMaxWaitMinutes());
        reservationUpdates.setSelected(s.isNotifyReservationUpdates());
        chargerAlerts.setSelected(s.isNotifyChargerAlerts());
        alternativeSuggestions.setSelected(s.isNotifyAlternativeSuggestions());
        (s.isEmailUpdates() ? inAppAndEmail : inAppOnly).setSelected(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void save() {
        boolean anyAlerts = reservationUpdates.isSelected() || chargerAlerts.isSelected()
                || alternativeSuggestions.isSelected();
        String error = null;
        if (homeBox.getValue() == null || connectorBox.getValue() == null || powerBox.getValue() == null) {
            error = "Please fill in all charging preferences.";
        } else if (inAppAndEmail.isSelected() && !anyAlerts) {
            error = "Turn on at least one alert type to receive email updates.";
        }
        if (error != null) {
            errorLabel.setText(error);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            app.showToast("Settings not saved", AppController.ToastType.ERROR);
            return;
        }

        UserSettings s = app.getSettings();
        s.setHomeLocation(homeBox.getValue());
        s.setPreferredConnector(connectorBox.getValue());
        s.setPreferredPowerKw(powerBox.getValue());
        s.setMaxDistanceKm((int) Math.round(distanceSlider.getValue()));
        s.setMaxWaitMinutes((int) Math.round(waitSlider.getValue()));
        s.setNotifyReservationUpdates(reservationUpdates.isSelected());
        s.setNotifyChargerAlerts(chargerAlerts.isSelected());
        s.setNotifyAlternativeSuggestions(alternativeSuggestions.isSelected());
        s.setEmailUpdates(inAppAndEmail.isSelected());

        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        app.notifyDataChanged();
        app.showToast("Settings saved for this session", AppController.ToastType.SUCCESS);
    }

    private static HBox cardHeader(String icon, String title, String subtitle) {
        VBox texts = new VBox(2, Ui.label(title, "section-title"), Ui.wrapLabel(subtitle, "section-subtitle"));
        HBox.setHgrow(texts, Priority.ALWAYS);
        HBox header = new HBox(14, Ui.iconTile(icon, "green"), texts);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private static VBox sliderField(String text, Slider slider, String unit) {
        Label value = Ui.label("", "slider-value");
        slider.setBlockIncrement(1);
        slider.valueProperty().addListener((obs, o, n) -> value.setText(Math.round(n.doubleValue()) + " " + unit));
        value.setText(Math.round(slider.getValue()) + " " + unit);
        HBox top = new HBox(8, Ui.label(text, "field-label"), Ui.hSpacer(), value);
        top.setAlignment(Pos.CENTER_LEFT);
        return new VBox(10, top, slider);
    }
}
