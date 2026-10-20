package com.vgrid.view;

import com.vgrid.controller.AppController;
import com.vgrid.controller.Refreshable;
import com.vgrid.controller.Screen;
import com.vgrid.model.AppNotification;
import com.vgrid.model.Reservation;
import com.vgrid.util.Format;
import com.vgrid.view.components.EmptyState;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** Alerts list. Each alert can open the screen it relates to. */
public class AlertsView extends BaseView implements Refreshable {

    public AlertsView(AppController app) {
        super(app);
        refresh();
    }

    @Override
    public void refresh() {
        List<AppNotification> alerts = app.getNotificationService().getAll();
        long unread = app.getNotificationService().getUnreadCount();

        Button markAll = Ui.secondaryButton("Mark All as Read", Icons.CHECK);
        markAll.setDisable(unread == 0);
        markAll.setOnAction(e -> {
            app.getNotificationService().markAllAsRead();
            app.notifyDataChanged();
            app.showToast("All alerts marked as read", AppController.ToastType.SUCCESS);
        });
        Button clearRead = Ui.secondaryButton("Clear Read", Icons.CLOSE);
        clearRead.setDisable(alerts.stream().noneMatch(AppNotification::isRead));
        clearRead.setOnAction(e -> {
            int removed = app.getNotificationService().clearRead();
            app.notifyDataChanged();
            app.showToast(Format.plural(removed, "alert") + " cleared", AppController.ToastType.INFO);
        });

        content.getChildren().setAll(Ui.sectionHeader(
                unread == 0 ? "You're all caught up" : "You have " + Format.plural(unread, "unread alert"),
                "Reservation confirmations, charger issues and alternative suggestions (simulated events)",
                markAll, clearRead));

        if (alerts.isEmpty()) {
            content.getChildren().add(new EmptyState(Icons.BELL, "No alerts",
                    "New reservation updates and charger alerts will appear here.",
                    "Go to Dashboard", () -> app.navigate(Screen.DASHBOARD)));
            return;
        }

        VBox list = new VBox(12);
        for (AppNotification alert : alerts) {
            list.getChildren().add(buildAlert(alert));
        }
        content.getChildren().add(list);
    }

    private Node buildAlert(AppNotification alert) {
        String icon;
        String tone;
        String actionText;
        switch (alert.getType()) {
            case RESERVATION_CONFIRMED -> { icon = Icons.CHECK_CIRCLE; tone = "green"; actionText = "View Reservation"; }
            case RESERVATION_CANCELLED -> { icon = Icons.CLOSE; tone = "soft"; actionText = "View Reservation"; }
            case CHARGER_UNAVAILABLE -> { icon = Icons.WARNING; tone = "red"; actionText = "Review Alternatives"; }
            case ALTERNATIVE_SUGGESTION -> { icon = Icons.SWAP; tone = "amber"; actionText = "Compare Alternatives"; }
            default -> { icon = Icons.INFO; tone = "navy"; actionText = "Find Stations"; }
        }

        HBox titleRow = new HBox(8, Ui.label(alert.getTitle(), "list-title"));
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (!alert.isRead()) {
            Region dot = new Region();
            dot.getStyleClass().add("unread-dot");
            titleRow.getChildren().add(dot);
        }
        VBox texts = new VBox(4, titleRow, Ui.wrapLabel(alert.getMessage(), "muted"),
                Ui.label(Format.relative(alert.getTime()), "small-muted"));
        HBox.setHgrow(texts, Priority.ALWAYS);
        texts.setMinWidth(0);

        Button open = Ui.primaryButton(actionText, null);
        open.setOnAction(e -> openAlert(alert));

        HBox buttons = new HBox(6, open);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        if (!alert.isRead()) {
            Button read = new Button();
            read.setGraphic(Icons.create(Icons.CHECK, 18));
            read.getStyleClass().add("icon-button");
            read.setTooltip(new Tooltip("Mark as read"));
            read.setOnAction(e -> {
                app.getNotificationService().markAsRead(alert);
                app.notifyDataChanged();
            });
            buttons.getChildren().add(read);
        }
        Button dismiss = new Button();
        dismiss.setGraphic(Icons.create(Icons.CLOSE, 18));
        dismiss.getStyleClass().add("icon-button");
        dismiss.setTooltip(new Tooltip("Dismiss"));
        dismiss.setOnAction(e -> {
            app.getNotificationService().dismiss(alert);
            app.notifyDataChanged();
        });
        buttons.getChildren().add(dismiss);

        HBox card = new HBox(14, Ui.iconTile(icon, tone), texts, buttons);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("card", "alert-card");
        if (!alert.isRead()) {
            card.getStyleClass().add("alert-unread");
        }
        return card;
    }

    /** Marks the alert as read and opens the screen it relates to. */
    private void openAlert(AppNotification alert) {
        app.getNotificationService().markAsRead(alert);
        Reservation reservation = alert.getReservationId() == null ? null
                : app.getReservationService().findById(alert.getReservationId());

        switch (alert.getType()) {
            case CHARGER_UNAVAILABLE, ALTERNATIVE_SUGGESTION -> {
                if (reservation != null) {
                    app.openAlternatives(reservation);
                } else {
                    app.navigate(Screen.RESERVATIONS);
                }
            }
            case RESERVATION_CONFIRMED, RESERVATION_CANCELLED -> {
                app.navigate(Screen.RESERVATIONS);
                if (reservation != null) {
                    app.showReservationDetails(reservation);
                } else {
                    app.showToast("That reservation is no longer in your history", AppController.ToastType.INFO);
                }
            }
            default -> app.navigate(Screen.FIND_STATIONS);
        }
    }
}
