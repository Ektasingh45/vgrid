package com.vgrid.view;

import com.vgrid.controller.AppController;
import com.vgrid.controller.Refreshable;
import com.vgrid.controller.Screen;
import com.vgrid.model.*;
import com.vgrid.util.Format;
import com.vgrid.view.components.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Home screen: hero banner, summary numbers, recommended stations, next reservation and activity. */
public class DashboardView extends BaseView implements Refreshable {

    public DashboardView(AppController app) {
        super(app);
        refresh();
    }

    @Override
    public void refresh() {
        content.getChildren().setAll(buildHero(), buildSummaryCards(), buildRecommended(), buildBottomRow());
        app.getChargingService().getActiveSession()
                .ifPresent(session -> content.getChildren().add(1, buildChargingNow(session)));
    }

    /** Shown while the user is checked in at a charger. */
    private Node buildChargingNow(ChargingSession session) {
        Station station = app.getStationService().findStation(session.stationId());
        String where = (station == null ? session.stationId() : station.getName()) + " · charger " + session.chargerId();
        VBox texts = new VBox(2, Ui.label("You are charging at " + where, "banner-title-green"),
                Ui.label("Started " + Format.relative(session.startedAt()) + " · the charger shows as In use to "
                        + "everyone until you press Done (at most one hour)"
                        + (session.reservationId() == null ? "" : " · booking " + session.reservationId()), "banner-text-green"));
        HBox.setHgrow(texts, Priority.ALWAYS);
        Button done = Ui.primaryButton("Done Charging", Icons.CHECK);
        done.setOnAction(e -> app.stopCharging());
        HBox banner = new HBox(14, Ui.iconTile(Icons.BOLT, "success", 44, 22), texts, done);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add("banner-charging");
        return banner;
    }

    private Node buildHero() {
        UserSettings settings = app.getSettings();

        Button find = Ui.primaryButton("Find a Charging Station", Icons.SEARCH);
        find.getStyleClass().add("btn-lg");
        find.setOnAction(e -> app.navigate(Screen.FIND_STATIONS));
        Button smart = Ui.button("Smart Recommendation", Icons.SPARKLE, "btn-hero-ghost");
        smart.getStyleClass().add("btn-lg");
        smart.setOnAction(e -> app.navigate(Screen.RECOMMENDATIONS));

        VBox left = new VBox(12,
                Ui.label(app.getStationService().getCity().name().toUpperCase() + "  ·  "
                        + Format.plural(app.getStationService().getAllStations().size(), "station").toUpperCase()
                        + (app.isDatabaseMode() ? "  ·  SAVED IN MYSQL" : "  ·  OFFLINE MODE"), "hero-chip"),
                Ui.wrapLabel("Hi " + app.getUser().name().split(" ")[0] + ", ready for your next charge?", "hero-title"),
                Ui.wrapLabel("Find the right charger with less waiting.", "hero-subtitle"),
                new HBox(12, find, smart));
        HBox.setHgrow(left, Priority.ALWAYS);
        VBox.setMargin(left.getChildren().get(3), new javafx.geometry.Insets(10, 0, 0, 0));

        VBox widget = new VBox(10,
                Ui.iconTile(Icons.CAR, "hero", 46, 24),
                Ui.label("Charging from " + settings.getHomeLocation().name(), "hero-widget-title"),
                Ui.label(settings.getPreferredConnector() + " · " + Format.power(settings.getPreferredPowerKw()) + " or more",
                        "hero-widget-text"),
                Ui.label("Within " + settings.getMaxDistanceKm() + " km · max " + settings.getMaxWaitMinutes() + " min wait",
                        "hero-widget-text"));
        widget.getStyleClass().add("hero-widget");
        widget.setMinWidth(240);

        HBox hero = new HBox(24, left, widget);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("hero");
        widget.visibleProperty().bind(hero.widthProperty().greaterThan(840));
        widget.managedProperty().bind(widget.visibleProperty());
        return hero;
    }

    private Node buildSummaryCards() {
        UserSettings settings = app.getSettings();
        List<Station> nearby = app.getStationService().getNearbyStations();
        int available = nearby.stream().mapToInt(Station::getAvailableChargers).sum();
        int total = nearby.stream().mapToInt(Station::getTotalChargers).sum();
        long averageWait = Math.round(nearby.stream().mapToInt(Station::getAverageWaitMinutes).average().orElse(0));
        long active = app.getReservationService().countActive();
        long attention = app.getReservationService().countNeedingAttention();

        ResponsiveGrid grid = new ResponsiveGrid(200, 16).balanced();
        grid.getChildren().addAll(
                new SummaryCard(Icons.PIN, "green", String.valueOf(nearby.size()), "Nearby Stations",
                        "Within " + settings.getMaxDistanceKm() + " km of " + settings.getHomeLocation().name())
                        .onClick(() -> app.navigate(Screen.FIND_STATIONS)),
                new SummaryCard(Icons.PLUG, "green", available + " / " + total, "Available Chargers",
                        "Free right now at nearby stations")
                        .onClick(() -> app.navigate(Screen.FIND_STATIONS)),
                new SummaryCard(Icons.CLOCK, "amber", averageWait + " min", "Average Waiting Time",
                        "Estimated from free chargers nearby")
                        .onClick(() -> app.navigate(Screen.RECOMMENDATIONS)),
                new SummaryCard(Icons.CALENDAR, "navy", String.valueOf(active), "Active Reservations",
                        attention > 0 ? attention + " need your attention" : "Everything is on track")
                        .onClick(() -> app.navigate(Screen.RESERVATIONS)));
        return grid;
    }

    private Node buildRecommended() {
        UserSettings settings = app.getSettings();
        RecommendationRequest request = new RecommendationRequest(settings.getHomeLocation(),
                settings.getPreferredConnector(), settings.getMaxDistanceKm(), 120, 0, LocalTime.now());
        List<Station> stations = app.getRecommendationService().findBestStations(request).stream()
                .map(RecommendationResult::station).limit(3).toList();
        if (stations.isEmpty()) {
            stations = app.getStationService().getNearbyStations().stream().limit(3).toList();
        }

        Button viewAll = Ui.ghostButton("Get smart ranking", Icons.ARROW_RIGHT);
        viewAll.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        viewAll.setOnAction(e -> app.navigate(Screen.RECOMMENDATIONS));

        ResponsiveGrid grid = new ResponsiveGrid(300, 16);
        for (Station station : stations) {
            grid.getChildren().add(new StationCard(app, station));
        }

        VBox section = new VBox(14, Ui.sectionHeader("Recommended stations",
                "Best " + settings.getPreferredConnector() + " options near " + settings.getHomeLocation().name()
                        + " right now · based on current bookings and check-ins", viewAll), grid);
        if (stations.isEmpty()) {
            section.getChildren().set(1, new EmptyState(Icons.SEARCH, "No stations nearby",
                    "Try increasing your maximum travel distance in Settings.", "Open Settings",
                    () -> app.navigate(Screen.SETTINGS)));
        }
        return section;
    }

    private Node buildBottomRow() {
        ResponsiveGrid grid = new ResponsiveGrid(380, 16);
        grid.getChildren().addAll(buildUpcomingCard(), buildActivityCard());
        return grid;
    }

    private Node buildUpcomingCard() {
        VBox card = Ui.card();
        Button all = Ui.ghostButton("All reservations", null);
        all.setOnAction(e -> app.navigate(Screen.RESERVATIONS));
        card.getChildren().add(Ui.sectionHeader("Upcoming reservation", "Your next booked charging slot", all));

        Optional<Reservation> next = app.getReservationService().getNextReservation();
        if (next.isEmpty()) {
            card.getChildren().add(new EmptyState(Icons.CALENDAR, "No upcoming reservations",
                    "Reserve a charger and it will show up here.", "Find Stations",
                    () -> app.navigate(Screen.FIND_STATIONS)));
            return card;
        }

        Reservation r = next.get();
        VBox titles = new VBox(2, Ui.label(r.getStation().getName(), "card-title"),
                Ui.label(r.getId() + " · " + r.getStation().getArea(), "muted"));
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox top = new HBox(12, Ui.iconTile(Icons.BOLT, r.getStatus() == ReservationStatus.ACTION_REQUIRED ? "amber" : "green"),
                titles, StatusBadge.forReservation(r.getStatus()));
        top.setAlignment(Pos.CENTER_LEFT);

        var metrics = Ui.twoColumnGrid(12, 12,
                Ui.metric(Icons.CALENDAR, "Date", Format.date(r.getDate())),
                Ui.metric(Icons.CLOCK, "Time slot", r.getTimeSlot()),
                Ui.metric(Icons.PLUG, "Charger", r.getCharger().getId() + " · " + r.getCharger().getConnectorType()),
                Ui.metric(Icons.BOLT, "Power", Format.power(r.getCharger().getPowerKw())));

        Button details = Ui.grow(Ui.secondaryButton("View Details", null));
        details.setOnAction(e -> app.showReservationDetails(r));
        Button main;
        if (r.getStatus() == ReservationStatus.ACTION_REQUIRED) {
            main = Ui.grow(Ui.primaryButton("Find Alternative", Icons.SWAP));
            main.setOnAction(e -> app.requestAlternative(r));
        } else {
            main = Ui.grow(Ui.primaryButton("Manage", Icons.CALENDAR));
            main.setOnAction(e -> app.navigate(Screen.RESERVATIONS));
        }

        card.getChildren().addAll(top, metrics);
        if (r.getStatus() == ReservationStatus.ACTION_REQUIRED) {
            card.getChildren().add(Ui.notice(Icons.WARNING,
                    "Your reserved charger was reported out of service. Choose a compatible alternative.", "warning"));
        }
        card.getChildren().add(new HBox(10, details, main));
        return card;
    }

    private Node buildActivityCard() {
        VBox card = Ui.card();
        card.getChildren().add(Ui.sectionHeader("Recent activity", "Your latest bookings, check-ins and reports"));

        List<ActivityItem> items = app.getNotificationService().getRecentActivity(5);
        if (items.isEmpty()) {
            card.getChildren().add(Ui.label("No activity yet.", "muted"));
            return card;
        }
        VBox list = new VBox(0);
        for (int i = 0; i < items.size(); i++) {
            ActivityItem item = items.get(i);
            VBox texts = new VBox(1, Ui.label(item.title(), "list-title"), Ui.label(item.detail(), "small-muted"));
            HBox.setHgrow(texts, Priority.ALWAYS);
            texts.setMinWidth(0);
            HBox row = new HBox(12, Ui.iconTile(Icons.CHECK, "soft", 30, 14), texts,
                    Ui.label(Format.relative(item.time()), "small-muted"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("activity-row");
            list.getChildren().add(row);
            if (i < items.size() - 1) {
                list.getChildren().add(new Separator());
            }
        }
        card.getChildren().add(list);
        return card;
    }
}
