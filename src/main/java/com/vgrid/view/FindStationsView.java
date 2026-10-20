package com.vgrid.view;

import com.vgrid.controller.AppController;
import com.vgrid.model.ConnectorType;
import com.vgrid.model.CongestionLevel;
import com.vgrid.model.Station;
import com.vgrid.service.StationService.AvailabilityFilter;
import com.vgrid.util.Format;
import com.vgrid.view.components.*;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** Station discovery: search box + filters that update the results instantly. */
public class FindStationsView extends BaseView {
    private static final String ANY_DISTANCE = "Any distance";
    private static final String ALL_CONNECTORS = "All connectors";
    private static final String ANY_CONGESTION = "Any congestion";
    private static final int[] DISTANCE_OPTIONS = {2, 5, 10, 20};

    private final TextField searchField = new TextField();
    private final ComboBox<String> distanceBox = new ComboBox<>();
    private final ComboBox<String> connectorBox = new ComboBox<>();
    private final ComboBox<AvailabilityFilter> availabilityBox = new ComboBox<>();
    private final ComboBox<String> congestionBox = new ComboBox<>();
    private final Label resultCount = Ui.label("", "section-title");
    private final Label resultHint = Ui.label("", "section-subtitle");
    private final VBox resultsArea = new VBox(16);

    public FindStationsView(AppController app) {
        super(app);

        searchField.setPromptText("Search by station name or area, e.g. \"GreenPoint\" or \"Baner\"");
        HBox searchBox = new HBox(10, Icons.create(Icons.SEARCH, 18), searchField);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-box");

        distanceBox.getItems().add(ANY_DISTANCE);
        for (int km : DISTANCE_OPTIONS) {
            distanceBox.getItems().add("Within " + km + " km");
        }
        connectorBox.getItems().add(ALL_CONNECTORS);
        for (ConnectorType type : ConnectorType.values()) {
            connectorBox.getItems().add(type.getDisplayName());
        }
        availabilityBox.getItems().setAll(AvailabilityFilter.values());
        congestionBox.getItems().add(ANY_CONGESTION);
        for (CongestionLevel level : CongestionLevel.values()) {
            congestionBox.getItems().add(level + " congestion");
        }

        Button reset = Ui.secondaryButton("Reset Filters", Icons.REFRESH);
        reset.setOnAction(e -> resetFilters());

        HBox searchRow = new HBox(12, searchBox, reset);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        ResponsiveGrid filters = new ResponsiveGrid(180, 12);
        filters.getChildren().addAll(
                Ui.field("Distance from " + app.getStationService().getHomeLocation().name(), distanceBox),
                Ui.field("Connector type", connectorBox),
                Ui.field("Availability", availabilityBox),
                Ui.field("Congestion (simulated)", congestionBox));

        VBox filterCard = Ui.card("filter-card");
        filterCard.getChildren().addAll(searchRow, filters);

        HBox resultsHeader = Ui.sectionHeader("", null);
        resultsHeader.getChildren().set(0, new VBox(3, resultCount, resultHint));

        content.getChildren().addAll(filterCard, resultsHeader, resultsArea);

        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        distanceBox.valueProperty().addListener((obs, o, n) -> applyFilters());
        connectorBox.valueProperty().addListener((obs, o, n) -> applyFilters());
        availabilityBox.valueProperty().addListener((obs, o, n) -> applyFilters());
        congestionBox.valueProperty().addListener((obs, o, n) -> applyFilters());

        resetFilters();
    }

    private void resetFilters() {
        searchField.clear();
        distanceBox.setValue(ANY_DISTANCE);
        connectorBox.setValue(ALL_CONNECTORS);
        availabilityBox.setValue(AvailabilityFilter.ANY);
        congestionBox.setValue(ANY_CONGESTION);
        applyFilters();
    }

    private void applyFilters() {
        Double maxDistance = null;
        int distanceIndex = distanceBox.getSelectionModel().getSelectedIndex();
        if (distanceIndex > 0) {
            maxDistance = (double) DISTANCE_OPTIONS[distanceIndex - 1];
        }
        ConnectorType connector = ConnectorType.fromDisplayName(connectorBox.getValue());
        int congestionIndex = congestionBox.getSelectionModel().getSelectedIndex();
        CongestionLevel congestion = congestionIndex > 0 ? CongestionLevel.values()[congestionIndex - 1] : null;

        List<Station> stations = app.getStationService().searchStations(searchField.getText(), maxDistance,
                connector, availabilityBox.getValue(), congestion);

        resultCount.setText(Format.plural(stations.size(), "station") + " found");
        resultHint.setText("Sorted by distance from " + app.getStationService().getHomeLocation().name()
                + " · availability and congestion are simulated");

        if (stations.isEmpty()) {
            resultsArea.getChildren().setAll(new EmptyState(Icons.SEARCH, "No stations match your filters",
                    "Try a wider distance, a different connector type or clear the search text.",
                    "Reset Filters", this::resetFilters));
            return;
        }
        ResponsiveGrid grid = new ResponsiveGrid(330, 16);
        for (Station station : stations) {
            grid.getChildren().add(new StationCard(app, station));
        }
        resultsArea.getChildren().setAll(grid);
    }
}
