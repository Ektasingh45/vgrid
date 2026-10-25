package com.vgrid.view;

import com.vgrid.controller.AppController;
import com.vgrid.model.*;
import com.vgrid.service.StationService;
import com.vgrid.util.Format;
import com.vgrid.view.components.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.util.List;

/**
 * Smart Recommendations: the user describes the trip, and RecommendationService ranks
 * the stations of the user's city with simple, explainable rules.
 */
public class RecommendationsView extends BaseView {
    private static final List<Double> POWER_OPTIONS = List.of(0.0, 7.4, 22.0, 50.0, 100.0);

    private final ComboBox<Location> startBox = new ComboBox<>();
    private final ComboBox<ConnectorType> connectorBox = new ComboBox<>();
    private final ComboBox<Double> powerBox = new ComboBox<>();
    private final ComboBox<String> arrivalBox = new ComboBox<>();
    private final Slider distanceSlider = new Slider(1, 30, 10);
    private final Slider waitSlider = new Slider(5, 60, 20);
    private final Label errorLabel = Ui.wrapLabel("", "form-error");
    private final VBox resultsArea = new VBox(16);

    public RecommendationsView(AppController app) {
        super(app);

        startBox.getItems().setAll(app.getStationService().getLocations());
        connectorBox.getItems().setAll(ConnectorType.values());
        powerBox.getItems().setAll(POWER_OPTIONS);
        powerBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double kw) {
                if (kw == null) return "";
                return kw == 0 ? "Any power" : "At least " + Format.power(kw);
            }

            @Override
            public Double fromString(String text) {
                return null;
            }
        });
        for (int minutes = 6 * 60; minutes < 24 * 60; minutes += 30) {
            arrivalBox.getItems().add(LocalTime.of(minutes / 60, minutes % 60).toString());
        }
        arrivalBox.setVisibleRowCount(8);

        content.getChildren().addAll(buildForm(), resultsArea);
        loadDefaults();
        showPlaceholder();
    }

    private Node buildForm() {
        VBox titles = new VBox(3, Ui.label("Plan your charge", "section-title"),
                Ui.wrapLabel("Tell VGrid what you need. Stations in " + app.getStationService().getCity().name()
                        + " are ranked with simple, transparent rules using current bookings and check-ins.",
                        "section-subtitle"));
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox header = new HBox(14, Ui.iconTile(Icons.SPARKLE, "green", 44, 22), titles);
        header.setAlignment(Pos.CENTER_LEFT);

        ResponsiveGrid fields = new ResponsiveGrid(240, 16);
        fields.getChildren().addAll(
                Ui.field("Starting location", startBox),
                Ui.field("Preferred connector type", connectorBox),
                Ui.field("Required charging power", powerBox),
                sliderField("Maximum travel distance", distanceSlider, "km"),
                sliderField("Maximum acceptable waiting time", waitSlider, "min"),
                Ui.field("Preferred arrival time", arrivalBox));

        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button reset = Ui.secondaryButton("Use My Preferences", Icons.REFRESH);
        reset.setOnAction(e -> {
            loadDefaults();
            showPlaceholder();
        });
        Button find = Ui.primaryButton("Find Best Station", Icons.SPARKLE);
        find.getStyleClass().add("btn-lg");
        find.setDefaultButton(true);
        find.setOnAction(e -> findBestStation());

        HBox actions = new HBox(12, errorLabel, Ui.hSpacer(), reset, find);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = Ui.card();
        card.setSpacing(20);
        card.getChildren().addAll(header, fields, new Separator(), actions);
        return card;
    }

    private VBox sliderField(String text, Slider slider, String unit) {
        Label value = Ui.label("", "slider-value");
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(5);
        slider.valueProperty().addListener((obs, o, n) -> value.setText(Math.round(n.doubleValue()) + " " + unit));
        value.setText(Math.round(slider.getValue()) + " " + unit);
        HBox top = new HBox(8, Ui.label(text, "field-label"), Ui.hSpacer(), value);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(10, top, slider);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Fills the form from the user's Settings. */
    private void loadDefaults() {
        UserSettings settings = app.getSettings();
        startBox.setValue(settings.getHomeLocation());
        connectorBox.setValue(settings.getPreferredConnector());
        powerBox.setValue(POWER_OPTIONS.stream().filter(p -> p <= settings.getPreferredPowerKw())
                .reduce((a, b) -> b).orElse(0.0));
        distanceSlider.setValue(settings.getMaxDistanceKm());
        waitSlider.setValue(settings.getMaxWaitMinutes());

        LocalTime now = LocalTime.now();
        int minutes = now.getHour() * 60 + now.getMinute();
        int next = ((minutes / 30) + 1) * 30;
        String time = next >= 24 * 60 || next < 6 * 60 ? "06:00" : LocalTime.of(next / 60, next % 60).toString();
        arrivalBox.setValue(time);
        hideError();
    }

    private void showPlaceholder() {
        resultsArea.getChildren().setAll(new EmptyState(Icons.SPARKLE, "Your ranked stations will appear here",
                "Adjust the options above and select Find Best Station. VGrid will compare compatibility, "
                        + "availability, distance, waiting time and congestion.", null, null));
    }

    private void findBestStation() {
        if (startBox.getValue() == null) {
            showError("Please choose a starting location.");
            return;
        }
        if (connectorBox.getValue() == null) {
            showError("Please choose a connector type.");
            return;
        }
        if (powerBox.getValue() == null) {
            showError("Please choose the required charging power.");
            return;
        }
        if (arrivalBox.getValue() == null) {
            showError("Please choose a preferred arrival time.");
            return;
        }
        hideError();

        RecommendationRequest request = new RecommendationRequest(startBox.getValue(), connectorBox.getValue(),
                Math.round(distanceSlider.getValue()), (int) Math.round(waitSlider.getValue()),
                powerBox.getValue(), LocalTime.parse(arrivalBox.getValue()));
        showResults(app.getRecommendationService().findBestStations(request), request);
        scrollTo(resultsArea);
    }

    private void showResults(List<RecommendationResult> results, RecommendationRequest request) {
        if (results.isEmpty()) {
            resultsArea.getChildren().setAll(new EmptyState(Icons.SEARCH, "No station meets all your requirements",
                    "Try a longer travel distance, a longer waiting time or a lower charging power.",
                    "Relax Limits & Search Again", () -> {
                        distanceSlider.setValue(Math.min(30, distanceSlider.getValue() + 10));
                        waitSlider.setValue(Math.min(60, waitSlider.getValue() + 20));
                        findBestStation();
                    }));
            return;
        }

        boolean peak = StationService.isPeakHour(request.arrivalTime());
        Label live = StatusBadge.of("Live occupancy + time-of-day rule", "neutral");
        HBox header = Ui.sectionHeader(Format.plural(results.size(), "station") + " ranked for you",
                "Arriving at " + request.arrivalTime() + " from " + request.startLocation().name() + " · "
                        + (peak ? "peak hour, so stations are busier" : "off-peak hour"), live);

        resultsArea.getChildren().setAll(header, buildTopCard(results.get(0)));

        if (results.size() > 1) {
            VBox others = new VBox(12);
            for (int i = 1; i < results.size(); i++) {
                others.getChildren().add(buildRankedCard(results.get(i), i + 1));
            }
            resultsArea.getChildren().addAll(Ui.label("Other good options", "subsection-title"), others);
        }

        resultsArea.getChildren().add(Ui.notice(Icons.INFO,
                "How ranking works: stations without a compatible working charger, beyond your distance limit or "
                        + "above your waiting-time limit are excluded. The rest get a score out of 100 - shorter "
                        + "distance and wait score higher, congestion (chargers reserved or in use now, raised at "
                        + "peak hours) lowers the score, and free chargers and higher power add a bonus. "
                        + "No machine learning is used.", "info"));
    }

    private Node buildTopCard(RecommendationResult result) {
        Station station = result.station();

        Label best = Ui.label("BEST MATCH", "best-badge");
        best.setGraphic(Icons.create(Icons.STAR, 13));
        HBox topLine = new HBox(10, best, Ui.hSpacer(), Ui.label("Match score " + result.score() + "/100", "score-text"));
        topLine.setAlignment(Pos.CENTER_LEFT);

        VBox titles = new VBox(2, Ui.label(station.getName(), "card-title-lg"),
                Ui.label(station.getAddress() + ", " + station.getArea(), "muted"));
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox titleRow = new HBox(14, Ui.iconTile(Icons.BOLT, "green", 52, 26), titles,
                StatusBadge.forCongestion(result.predictedCongestion()));
        titleRow.setAlignment(Pos.CENTER_LEFT);

        ResponsiveGrid metrics = new ResponsiveGrid(160, 12);
        metrics.getChildren().addAll(
                metricTile(Icons.PIN, "Distance", Format.distance(result.distanceKm())),
                metricTile(Icons.CLOCK, "Waiting time", Format.minutes(result.predictedWaitMinutes())),
                metricTile(Icons.TRENDING, "Congestion", result.predictedCongestion().toString()),
                metricTile(Icons.PLUG, "Compatible charger", "Yes · " + result.bestCharger().getConnectorType()
                        + " " + Format.power(result.bestCharger().getPowerKw())));

        VBox reasonBox = new VBox(8, Ui.label("Why this station?", "reason-title"),
                Ui.wrapLabel(result.summary(), "reason-text"));
        for (String reason : result.reasons()) {
            Label text = Ui.wrapLabel(reason, "reason-item");
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(8, Icons.create(Icons.CHECK, 16, "icon-green"), text);
            row.setAlignment(Pos.TOP_LEFT);
            reasonBox.getChildren().add(row);
        }
        reasonBox.getStyleClass().add("reason-box");

        Button details = Ui.secondaryButton("View Details", null);
        details.setOnAction(e -> app.showStationDetails(station));
        Button reserve = Ui.primaryButton("Reserve Charger", Icons.BOLT);
        reserve.setOnAction(e -> app.showReservationForm(station, result.bestCharger()));
        HBox actions = new HBox(10, Ui.hSpacer(), details, reserve);

        VBox card = Ui.card("top-card");
        card.getChildren().addAll(topLine, titleRow, metrics, reasonBox, actions);
        return card;
    }

    private Node buildRankedCard(RecommendationResult result, int rank) {
        Station station = result.station();

        Label rankLabel = Ui.label("#" + rank, "rank-circle");
        rankLabel.setMinSize(38, 38);
        rankLabel.setAlignment(Pos.CENTER);

        HBox nameRow = new HBox(10, Ui.label(station.getName(), "card-title"), Ui.hSpacer(),
                StatusBadge.forCongestion(result.predictedCongestion()));
        nameRow.setAlignment(Pos.CENTER_LEFT);

        FlowPane facts = new FlowPane(16, 8,
                Ui.iconText(Icons.PIN, Format.distance(result.distanceKm())),
                Ui.iconText(Icons.CLOCK, Format.minutes(result.predictedWaitMinutes()) + " wait"),
                Ui.iconText(Icons.PLUG, result.bestCharger().getConnectorType() + " · "
                        + Format.power(result.bestCharger().getPowerKw())),
                Ui.iconText(Icons.CHECK_CIRCLE, result.compatibleAvailable() > 0
                        ? result.compatibleAvailable() + " free now" : "Busy now"));

        ProgressBar score = new ProgressBar(result.score() / 100.0);
        score.setMaxWidth(Double.MAX_VALUE);
        score.getStyleClass().add("bar-green");
        HBox.setHgrow(score, Priority.ALWAYS);
        HBox scoreRow = new HBox(10, score, Ui.label(result.score() + "/100", "score-text"));
        scoreRow.setAlignment(Pos.CENTER_LEFT);

        Button details = Ui.secondaryButton("View Details", null);
        details.setOnAction(e -> app.showStationDetails(station));
        Button reserve = Ui.primaryButton("Reserve Charger", Icons.BOLT);
        reserve.setOnAction(e -> app.showReservationForm(station, result.bestCharger()));
        HBox actions = new HBox(10, details, reserve);

        VBox center = new VBox(10, nameRow, Ui.label(station.getArea() + " · " + station.getOperator(), "muted"),
                facts, Ui.wrapLabel(result.summary(), "muted"), scoreRow, actions);
        HBox.setHgrow(center, Priority.ALWAYS);

        HBox card = new HBox(16, rankLabel, center);
        card.getStyleClass().addAll("card", "card-hover");
        Ui.hoverLift(card);
        return card;
    }

    private static Node metricTile(String icon, String caption, String value) {
        HBox metric = Ui.metric(icon, caption, value);
        metric.getStyleClass().add("metric-tile");
        return metric;
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
