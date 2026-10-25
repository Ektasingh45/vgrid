package com.vgrid.controller;

import com.vgrid.dao.DataLayer;
import com.vgrid.model.*;
import com.vgrid.service.*;
import com.vgrid.util.Format;
import com.vgrid.view.*;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.Ui;
import com.vgrid.view.dialogs.*;
import javafx.animation.*;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * The central "glue" of the app:
 *  - creates the services for the signed-in user (they read data through the DAOs),
 *  - switches between screens,
 *  - shows dialogs (modals), confirmations and toast messages.
 * Screens only talk to this class and to the services, never to each other.
 */
public class AppController {

    public enum ToastType { SUCCESS, ERROR, INFO }

    /** Open windows re-read the database this often, so changes made by other users show up by themselves. */
    private static final Duration LIVE_REFRESH = Duration.seconds(15);

    private final DataLayer data;
    private final UserSession session;
    private final Runnable onSignOut;
    private final StationImportService importService;
    private final StationService stationService;
    private final NotificationService notificationService;
    private final RecommendationService recommendationService;
    private final ReservationService reservationService;
    private final ChargingService chargingService;
    private final SettingsService settingsService;

    private final StackPane root = new StackPane();
    private final BorderPane contentArea = new BorderPane();
    private final Sidebar sidebar;
    private final TopBar topBar;
    private final Deque<Node> openModals = new ArrayDeque<>();
    private final Timeline liveRefresh;
    private Node currentView;
    private Screen currentScreen = Screen.DASHBOARD;
    private Node currentToast;
    private Node busyOverlay;
    private boolean liveRefreshFailing;

    /** onSignOut is called after the user confirms "Sign out" (MainApp then shows the login page). */
    public AppController(DataLayer data, UserSession session, StationImportService importService, Runnable onSignOut) {
        this.data = data;
        this.session = session;
        this.importService = importService;
        stationService = new StationService(data.stations(), data.reservations(), data.charging(), data.ocpp(), session);
        notificationService = new NotificationService(data.notifications(), session);
        recommendationService = new RecommendationService(stationService);
        reservationService = new ReservationService(data.reservations(), stationService, recommendationService,
                notificationService, session);
        chargingService = new ChargingService(data.charging(), data.reservations(), stationService,
                reservationService, notificationService, session);
        settingsService = new SettingsService(data.settings(), session);

        liveRefresh = new Timeline(new KeyFrame(LIVE_REFRESH, e -> liveRefresh()));
        liveRefresh.setCycleCount(Animation.INDEFINITE);
        this.onSignOut = () -> {
            liveRefresh.stop();
            onSignOut.run();
        };

        sidebar = new Sidebar(this);
        topBar = new TopBar(this);
        contentArea.setTop(topBar);

        BorderPane shell = new BorderPane();
        shell.setLeft(sidebar);
        shell.setCenter(contentArea);
        root.getChildren().add(shell);
        root.getStyleClass().add("app-root");

        // Escape closes the top-most dialog.
        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE && !openModals.isEmpty()) {
                closeModal();
                e.consume();
            }
        });
        liveRefresh.play();
    }

    public Parent getRoot() { return root; }
    public StationService getStationService() { return stationService; }
    public RecommendationService getRecommendationService() { return recommendationService; }
    public ReservationService getReservationService() { return reservationService; }
    public NotificationService getNotificationService() { return notificationService; }
    public ChargingService getChargingService() { return chargingService; }
    public SettingsService getSettingsService() { return settingsService; }
    public StationImportService getImportService() { return importService; }
    public UserSettings getSettings() { return session.getSettings(); }
    public User getUser() { return session.getUser(); }
    public boolean isDatabaseMode() { return data.isDatabase(); }
    public String getDataDescription() { return data.description(); }

    // ------------------------------------------------------------------ navigation

    /** Opens a screen with fresh data, so changes made in another app window show up too. */
    public void navigate(Screen screen) {
        reloadData();
        Node view = switch (screen) {
            case DASHBOARD -> new DashboardView(this);
            case FIND_STATIONS -> new FindStationsView(this);
            case RECOMMENDATIONS -> new RecommendationsView(this);
            case RESERVATIONS -> new ReservationsView(this);
            case ALERTS -> new AlertsView(this);
            case SETTINGS -> new SettingsView(this);
            case ALTERNATIVES -> new AlternativesView(this, reservationService
                    .getByStatus(ReservationStatus.ACTION_REQUIRED).stream().findFirst().orElse(null));
        };
        showScreen(screen, view);
    }

    /** Opens the alternative-charger screen for a specific reservation. */
    public void openAlternatives(Reservation reservation) {
        showScreen(Screen.ALTERNATIVES, new AlternativesView(this, reservation));
    }

    private void showScreen(Screen screen, Node view) {
        closeAllModals();
        currentView = view;
        currentScreen = screen;
        contentArea.setCenter(view);
        sidebar.setSelected(screen.getSidebarItem());
        topBar.setScreen(screen);
        refreshChrome();

        FadeTransition fade = new FadeTransition(Duration.millis(220), view);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /** Call after any data change so badges and the visible screen stay up to date. */
    public void notifyDataChanged() {
        refreshChrome();
        if (currentView instanceof Refreshable refreshable) {
            refreshable.refresh();
        }
    }

    /** Stations first, then reservations, because reservations point to the loaded Station objects. */
    private void reloadData() {
        stationService.reload();
        reservationService.reload();
    }

    /**
     * Every 15 seconds: re-read bookings, check-ins and alerts and redraw the open screen, so a booking
     * or check-in made in another window appears without pressing Reload. Skipped while a dialog is open.
     */
    private void liveRefresh() {
        if (!openModals.isEmpty() || busyOverlay != null) {
            return;
        }
        try {
            reloadData();
            refreshChrome();
            if (currentView instanceof Refreshable refreshable) {
                refreshable.refresh();
            }
            liveRefreshFailing = false;
        } catch (RuntimeException e) {
            if (!liveRefreshFailing) { // say it once, not every 15 seconds
                showError(e);
            }
            liveRefreshFailing = true;
        }
    }

    /**
     * Runs slow work (e.g. downloading stations) on a background thread while a "please wait" card
     * covers the app, then continues on the screen thread.
     */
    public <T> void runInBackground(String message, Callable<T> work, Consumer<T> onSuccess) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(34, 34);
        VBox card = new VBox(14, spinner, Ui.wrapLabel(message, "busy-text"));
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("busy-card");
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane overlay = new StackPane(card);
        overlay.getStyleClass().add("busy-overlay");
        busyOverlay = overlay;
        root.getChildren().add(overlay);

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> {
            hideBusy();
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            hideBusy();
            showError(task.getException());
        });
        Thread thread = new Thread(task, "vgrid-background");
        thread.setDaemon(true);
        thread.start();
    }

    private void hideBusy() {
        root.getChildren().remove(busyOverlay);
        busyOverlay = null;
    }

    // ------------------------------------------------------------------ city and stations

    /** Saves new settings; if the city changed and was never loaded, its stations are downloaded first. */
    public void saveSettings(UserSettings changed) {
        City newCity = changed.getCity();
        boolean cityChanged = !newCity.equals(getSettings().getCity());
        if (!cityChanged) {
            settingsService.save(changed);
            notifyDataChanged();
            showToast(isDatabaseMode() ? "Settings saved to your account" : "Settings saved for this session",
                    ToastType.SUCCESS);
            return;
        }
        runInBackground("Loading charging stations for " + newCity.name() + "…",
                () -> importService.ensureLoaded(newCity),
                outcome -> {
                    settingsService.save(changed);
                    navigate(Screen.SETTINGS);
                    showImportOutcome(outcome, "City changed to " + newCity.name());
                });
    }

    /** Settings "Refresh stations": downloads the selected city again from Open Charge Map. */
    public void refreshStations() {
        City city = getSettings().getCity();
        runInBackground("Downloading the latest " + city.name() + " stations from Open Charge Map…",
                () -> importService.refresh(city),
                outcome -> {
                    navigate(currentScreen == Screen.ALTERNATIVES ? Screen.RESERVATIONS : currentScreen);
                    showImportOutcome(outcome, "Stations updated");
                });
    }

    private void showImportOutcome(StationImportService.Outcome outcome, String headline) {
        if (outcome.warning() != null) {
            showToast(outcome.warning(), ToastType.INFO);
        } else {
            showToast(headline + " · " + Format.plural(outcome.stationCount(), "station") + " from "
                    + outcome.source(), ToastType.SUCCESS);
        }
    }

    // ------------------------------------------------------------------ charging and problem reports

    public void startCharging(Station station, Charger charger) {
        try {
            chargingService.startCharging(station, charger);
            closeAllModals();
            navigate(currentScreen == Screen.ALTERNATIVES ? Screen.DASHBOARD : currentScreen);
            showToast("Charging started at " + station.getName() + " · charger " + charger.getId(), ToastType.SUCCESS);
        } catch (IllegalArgumentException e) {
            showToast(e.getMessage(), ToastType.ERROR);
        }
    }

    public void stopCharging() {
        String summary = chargingService.stopCharging();
        navigate(currentScreen == Screen.ALTERNATIVES ? Screen.DASHBOARD : currentScreen);
        showToast(summary == null ? "You were not charging" : summary, ToastType.SUCCESS);
    }

    /** Confirms, then marks the charger out of service for everyone and alerts every driver booked on it. */
    public void requestReportProblem(Station station, Charger charger) {
        confirm("Report charger " + charger.getId() + " as not working?",
                "Charger " + charger.getId() + " at " + station.getName() + " will be shown as out of service to "
                        + "everyone, and every driver with an upcoming booking on it will get an alert with alternatives.",
                "Report Problem", true, () -> {
                    int affected = reservationService.reportChargerProblem(station, charger);
                    navigate(currentScreen == Screen.ALTERNATIVES ? Screen.RESERVATIONS : currentScreen);
                    showToast("Charger " + charger.getId() + " reported"
                            + (affected == 0 ? "" : " · " + Format.plural(affected, "booking") + " alerted"),
                            ToastType.SUCCESS);
                });
    }

    /** A driver confirms a reported charger works again. */
    public void markChargerWorking(Station station, Charger charger) {
        stationService.markWorking(station, charger);
        closeAllModals();
        navigate(currentScreen == Screen.ALTERNATIVES ? Screen.RESERVATIONS : currentScreen);
        showToast("Charger " + charger.getId() + " marked as working again", ToastType.SUCCESS);
    }

    /** Top-bar "Reload" button: reads everything again from the database and redraws the open screen. */
    public void reloadFromDatabase() {
        if (currentScreen == Screen.ALTERNATIVES) {
            navigate(Screen.RESERVATIONS);
        } else {
            navigate(currentScreen);
        }
        showToast(isDatabaseMode() ? "Latest data loaded from MySQL" : "Screen refreshed", ToastType.INFO);
    }

    /** Asks for confirmation, then returns to the login page. */
    public void requestSignOut() {
        confirm("Sign out of VGrid?", "You are signed in as " + getUser().name() + " (" + getUser().email() + ").",
                "Sign Out", false, onSignOut);
    }

    /** Shown when something unexpected fails, e.g. MySQL was stopped while the app was open. */
    public void showError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        boolean database = error instanceof com.vgrid.dao.DataAccessException;
        showToast((database ? "Database problem: " : "") + message, ToastType.ERROR);
    }

    private void refreshChrome() {
        long unread = notificationService.getUnreadCount();
        sidebar.setAlertCount(unread);
        topBar.refresh(unread, getSettings());
    }

    // ------------------------------------------------------------------ dialogs

    public void showStationDetails(Station station) {
        showModal(new StationDetailsDialog(this, station));
    }

    public void showReservationForm(Station station, Charger preferredCharger) {
        showModal(new ReservationDialog(this, station, preferredCharger));
    }

    public void showReservationDetails(Reservation reservation) {
        showModal(new ReservationDetailsDialog(this, reservation));
    }

    public void confirm(String title, String message, String confirmText, boolean danger, Runnable onConfirm) {
        showModal(new ConfirmDialog(this, title, message, confirmText, danger, onConfirm));
    }

    /** Asks for confirmation, then cancels the reservation. */
    public void requestCancel(Reservation reservation, Runnable afterCancel) {
        confirm("Cancel reservation " + reservation.getId() + "?",
                "Your slot at " + reservation.getStation().getName() + " on "
                        + Format.longDate(reservation.getDate()) + ", " + reservation.getTimeSlot()
                        + " will be released so other drivers can book it.",
                "Cancel Reservation", true, () -> {
                    reservationService.cancelReservation(reservation);
                    showToast("Reservation " + reservation.getId() + " cancelled", ToastType.INFO);
                    notifyDataChanged();
                    if (afterCancel != null) {
                        afterCancel.run();
                    }
                });
    }

    /**
     * Reservation protection. If the charger was already reported broken, go straight to alternatives;
     * otherwise the driver reports the problem first (this alerts everyone booked on that charger).
     */
    public void requestAlternative(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.ACTION_REQUIRED) {
            openAlternatives(reservation);
            return;
        }
        Station station = reservation.getStation();
        Charger charger = reservation.getCharger();
        confirm("Is charger " + charger.getId() + " not working?",
                "VGrid will report charger " + charger.getId() + " at " + station.getName() + " as out of service "
                        + "for everyone, alert every driver booked on it, and show you compatible alternatives "
                        + "for " + reservation.getId() + ".",
                "Report & Find Alternatives", true, () -> {
                    reservationService.reportChargerProblem(station, charger);
                    openAlternatives(reservationService.findById(reservation.getId()));
                });
    }

    /** Shows a node centred above the app on a dimmed background. */
    public void showModal(Region dialog) {
        StackPane wrapper = new StackPane(dialog);
        wrapper.setPadding(new Insets(40));
        dialog.setMaxHeight(Region.USE_PREF_SIZE);

        ScrollPane overlay = new ScrollPane(wrapper);
        overlay.setFitToWidth(true);
        overlay.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        overlay.getStyleClass().add("modal-overlay");
        wrapper.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> overlay.getViewportBounds().getHeight(), overlay.viewportBoundsProperty()));
        wrapper.setOnMouseClicked(e -> {
            if (e.getTarget() == wrapper) {
                closeModal();
            }
        });

        root.getChildren().add(overlay);
        openModals.push(overlay);

        overlay.setOpacity(0);
        dialog.setScaleX(0.96);
        dialog.setScaleY(0.96);
        FadeTransition fade = new FadeTransition(Duration.millis(160), overlay);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(180), dialog);
        scale.setToX(1);
        scale.setToY(1);
        new ParallelTransition(fade, scale).play();
    }

    public void closeModal() {
        if (openModals.isEmpty()) {
            return;
        }
        Node overlay = openModals.pop();
        overlay.setMouseTransparent(true);
        FadeTransition fade = new FadeTransition(Duration.millis(120), overlay);
        fade.setToValue(0);
        fade.setOnFinished(e -> root.getChildren().remove(overlay));
        fade.play();
    }

    private void closeAllModals() {
        root.getChildren().removeAll(openModals);
        openModals.clear();
    }

    // ------------------------------------------------------------------ toasts

    /** Small message that appears at the bottom of the window for a few seconds. */
    public void showToast(String message, ToastType type) {
        String icon = switch (type) {
            case SUCCESS -> Icons.CHECK_CIRCLE;
            case ERROR -> Icons.WARNING;
            case INFO -> Icons.INFO;
        };
        HBox toast = new HBox(10, Icons.create(icon, 18), Ui.label(message, "toast-label"));
        toast.getStyleClass().addAll("toast", "toast-" + type.name().toLowerCase());
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 32, 0));
        root.getChildren().remove(currentToast);
        currentToast = toast;
        root.getChildren().add(toast);

        toast.setOpacity(0);
        toast.setTranslateY(14);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), toast);
        fadeIn.setToValue(1);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(180), toast);
        slideIn.setToY(0);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(260), toast);
        fadeOut.setToValue(0);
        SequentialTransition sequence = new SequentialTransition(
                new ParallelTransition(fadeIn, slideIn), new PauseTransition(Duration.seconds(2.8)), fadeOut);
        sequence.setOnFinished(e -> root.getChildren().remove(toast));
        sequence.play();
    }
}
