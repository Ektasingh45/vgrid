package com.vgrid.controller;

import com.vgrid.data.DemoDataStore;
import com.vgrid.model.*;
import com.vgrid.service.NotificationService;
import com.vgrid.service.RecommendationService;
import com.vgrid.service.ReservationService;
import com.vgrid.service.StationService;
import com.vgrid.util.Format;
import com.vgrid.view.*;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.Ui;
import com.vgrid.view.dialogs.*;
import javafx.animation.*;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The central "glue" of the app:
 *  - creates the services (which read the in-memory demo data),
 *  - switches between screens,
 *  - shows dialogs (modals), confirmations and toast messages.
 * Screens only talk to this class and to the services, never to each other.
 */
public class AppController {

    public enum ToastType { SUCCESS, ERROR, INFO }

    private final DemoDataStore store = DemoDataStore.getInstance();
    private final StationService stationService = new StationService(store);
    private final NotificationService notificationService = new NotificationService(store);
    private final RecommendationService recommendationService = new RecommendationService(stationService);
    private final ReservationService reservationService = new ReservationService(store, notificationService);

    private final StackPane root = new StackPane();
    private final BorderPane contentArea = new BorderPane();
    private final Sidebar sidebar;
    private final TopBar topBar;
    private final Deque<Node> openModals = new ArrayDeque<>();
    private Node currentView;
    private Node currentToast;

    public AppController() {
        sidebar = new Sidebar(this);
        topBar = new TopBar(this);
        contentArea.setTop(topBar);

        BorderPane shell = new BorderPane();
        shell.setLeft(sidebar);
        shell.setCenter(contentArea);
        root.getChildren().add(shell);
        root.getStyleClass().add("app-root");
    }

    public Parent getRoot() { return root; }
    public StationService getStationService() { return stationService; }
    public RecommendationService getRecommendationService() { return recommendationService; }
    public ReservationService getReservationService() { return reservationService; }
    public NotificationService getNotificationService() { return notificationService; }
    public UserSettings getSettings() { return store.getSettings(); }

    /** Escape closes the top-most dialog. */
    public void installShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE && !openModals.isEmpty()) {
                closeModal();
                e.consume();
            }
        });
    }

    // ------------------------------------------------------------------ navigation

    public void navigate(Screen screen) {
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

    /** Asks for confirmation, then cancels the reservation locally. */
    public void requestCancel(Reservation reservation, Runnable afterCancel) {
        confirm("Cancel reservation " + reservation.getId() + "?",
                "Your slot at " + reservation.getStation().getName() + " on "
                        + Format.longDate(reservation.getDate()) + ", " + reservation.getTimeSlot()
                        + " will be released. This only changes demo data for this session.",
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
     * Reservation protection demo. If the charger already failed, go straight to alternatives;
     * otherwise ask whether to simulate a charger failure first.
     */
    public void requestAlternative(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.ACTION_REQUIRED) {
            openAlternatives(reservation);
            return;
        }
        confirm("Simulate a charger failure?",
                "To demonstrate reservation protection, VGrid will pretend that charger "
                        + reservation.getCharger().getId() + " at " + reservation.getStation().getName()
                        + " has become unavailable, then show compatible alternatives. Nothing real is affected.",
                "Simulate & Find Alternatives", false, () -> {
                    reservationService.simulateChargerFailure(reservation);
                    openAlternatives(reservation);
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

    /** Settings screen: reload every sample value. */
    public void resetDemoData() {
        store.resetToSampleData();
        navigate(Screen.DASHBOARD);
        showToast("Demo data has been reset", ToastType.SUCCESS);
    }
}
