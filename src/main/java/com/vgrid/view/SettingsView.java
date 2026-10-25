package com.vgrid.view;

import com.vgrid.controller.AppController;
import com.vgrid.model.City;
import com.vgrid.model.CityImport;
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

/** The signed-in user's preferences, including their city, and where the station data comes from. */
public class SettingsView extends BaseView {
    private static final List<Double> POWER_OPTIONS = List.of(7.4, 22.0, 30.0, 50.0, 60.0, 120.0);

    private final ComboBox<City> cityBox = new ComboBox<>();
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

        cityBox.getItems().setAll(City.ALL);
        // Choosing another city offers that city's areas as starting points.
        cityBox.valueProperty().addListener((obs, oldCity, city) -> {
            if (city != null && !city.equals(oldCity)) {
                homeBox.getItems().setAll(city.areas());
                homeBox.setValue(city.areas().get(0));
            }
        });
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

        content.getChildren().addAll(buildPreferencesCard(), buildNotificationsCard(), buildDataCard(), buildActionBar());
        loadFrom(app.getSettings());
    }

    private Node buildPreferencesCard() {
        ResponsiveGrid grid = new ResponsiveGrid(260, 18);
        grid.getChildren().addAll(
                Ui.field("City", cityBox),
                Ui.field("Home / usual starting location", homeBox),
                Ui.field("Preferred connector", connectorBox),
                Ui.field("Preferred charging power", powerBox),
                sliderField("Maximum travel distance", distanceSlider, "km"),
                sliderField("Maximum waiting time", waitSlider, "min"));
        VBox card = Ui.card();
        card.getChildren().addAll(cardHeader(Icons.TUNE, "Charging preferences",
                "Your city decides which stations you see. A new city's stations are downloaded the first time."), grid);
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

    /** Where the stations and the user's data come from, plus "Refresh stations". */
    private Node buildDataCard() {
        City city = app.getSettings().getCity();
        CityImport loaded = app.getImportService().currentImport(city);
        String stationText;
        if (loaded == null) {
            stationText = "No stations loaded for " + city.name() + " yet.";
        } else if (loaded.fromOpenChargeMap()) {
            stationText = Format.plural(loaded.stationCount(), "real station") + " for " + city.name()
                    + " from Open Charge Map (© contributors, CC BY 4.0), downloaded " + Format.relative(loaded.importedAt())
                    + ". Names, addresses, connectors and power are real.";
        } else {
            stationText = "Built-in sample stations for " + city.name() + ". Add an Open Charge Map API key "
                    + "(ocm.api.key in db.properties) and press Refresh to load real stations.";
        }
        Button refresh = Ui.secondaryButton("Refresh Stations", Icons.REFRESH);
        refresh.setOnAction(e -> app.refreshStations());
        HBox stationRow = new HBox(12, Ui.wrapLabel(stationText, "muted"), Ui.hSpacer(), refresh);
        HBox.setHgrow(stationRow.getChildren().get(0), Priority.ALWAYS);
        stationRow.setAlignment(Pos.CENTER_LEFT);

        boolean database = app.isDatabaseMode();
        Label storage = Ui.wrapLabel(database
                ? "Your account, bookings, check-ins, alerts and settings are saved in " + app.getDataDescription()
                  + ". Charger status comes from VGrid bookings, check-ins and problem reports."
                : "Offline mode: no database is connected, so everything is lost when the app closes.", "muted");

        VBox card = Ui.card();
        card.getChildren().addAll(cardHeader(Icons.DATABASE, "Data sources",
                        "Station details come from Open Charge Map. Live status comes from VGrid users."),
                stationRow, new Separator(), storage);
        return card;
    }

    private Node buildActionBar() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        Button restore = Ui.secondaryButton("Restore Defaults", Icons.REFRESH);
        restore.setOnAction(e -> {
            loadFrom(app.getSettingsService().defaults(cityBox.getValue()));
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
        cityBox.setValue(s.getCity());
        homeBox.getItems().setAll(s.getCity().areas());
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
        if (cityBox.getValue() == null || homeBox.getValue() == null || connectorBox.getValue() == null
                || powerBox.getValue() == null) {
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

        UserSettings s = app.getSettingsService().defaults(cityBox.getValue());
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
        app.saveSettings(s); // downloads the new city's stations first if needed
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
