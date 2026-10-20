package com.vgrid.view;

import com.vgrid.controller.AppController;
import com.vgrid.controller.Refreshable;
import com.vgrid.controller.Screen;
import com.vgrid.model.Reservation;
import com.vgrid.model.ReservationStatus;
import com.vgrid.util.Format;
import com.vgrid.view.components.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;

import java.util.List;

/** My Reservations: filter tabs and one card per reservation. */
public class ReservationsView extends BaseView implements Refreshable {

    private enum Filter {
        ALL("All"), UPCOMING("Upcoming"), ATTENTION("Needs attention"), PAST("Past & cancelled");

        private final String label;

        Filter(String label) {
            this.label = label;
        }
    }

    private Filter filter = Filter.ALL;

    public ReservationsView(AppController app) {
        super(app);
        refresh();
    }

    @Override
    public void refresh() {
        content.getChildren().clear();

        if (!app.getReservationService().hasAnyReservations()) {
            content.getChildren().add(new EmptyState(Icons.CALENDAR, "No reservations yet",
                    "When you reserve a charger it will appear here. Reservations in this demo are simulated "
                            + "and kept only for the current session.",
                    "Find Stations", () -> app.navigate(Screen.FIND_STATIONS)));
            return;
        }

        content.getChildren().add(buildToolbar());

        List<Reservation> reservations = switch (filter) {
            case ALL -> app.getReservationService().getAllReservations();
            case UPCOMING -> app.getReservationService().getByStatus(ReservationStatus.CONFIRMED);
            case ATTENTION -> app.getReservationService().getByStatus(ReservationStatus.ACTION_REQUIRED);
            case PAST -> app.getReservationService().getByStatus(ReservationStatus.COMPLETED, ReservationStatus.CANCELLED);
        };

        if (reservations.isEmpty()) {
            content.getChildren().add(new EmptyState(Icons.CALENDAR, "Nothing in \"" + filter.label + "\"",
                    "There are no reservations in this list right now.", "Show All Reservations", () -> {
                        filter = Filter.ALL;
                        refresh();
                    }));
            return;
        }

        VBox list = new VBox(14);
        for (Reservation reservation : reservations) {
            list.getChildren().add(buildCard(reservation));
        }
        content.getChildren().add(list);
    }

    private Node buildToolbar() {
        ToggleGroup group = new ToggleGroup();
        HBox tabs = new HBox(4);
        tabs.getStyleClass().add("tab-bar");
        for (Filter f : Filter.values()) {
            int count = switch (f) {
                case ALL -> app.getReservationService().getAllReservations().size();
                case UPCOMING -> app.getReservationService().getByStatus(ReservationStatus.CONFIRMED).size();
                case ATTENTION -> app.getReservationService().getByStatus(ReservationStatus.ACTION_REQUIRED).size();
                case PAST -> app.getReservationService()
                        .getByStatus(ReservationStatus.COMPLETED, ReservationStatus.CANCELLED).size();
            };
            ToggleButton tab = new ToggleButton(f.label + "  " + count);
            tab.getStyleClass().add("tab-button");
            tab.setToggleGroup(group);
            tab.setSelected(f == filter);
            tab.setOnAction(e -> {
                filter = f;
                refresh();
            });
            tabs.getChildren().add(tab);
        }
        Ui.keepOneSelected(group);

        HBox toolbar = new HBox(12, tabs, Ui.hSpacer());
        toolbar.setAlignment(Pos.CENTER_LEFT);

        if (filter == Filter.PAST && !app.getReservationService()
                .getByStatus(ReservationStatus.COMPLETED, ReservationStatus.CANCELLED).isEmpty()) {
            Button clear = Ui.secondaryButton("Clear History", null);
            clear.setOnAction(e -> app.confirm("Clear reservation history?",
                    "Completed and cancelled reservations will be removed from this session.",
                    "Clear History", true, () -> {
                        int removed = app.getReservationService().clearPastReservations();
                        app.showToast(Format.plural(removed, "reservation") + " removed", AppController.ToastType.INFO);
                        app.notifyDataChanged();
                    }));
            toolbar.getChildren().add(clear);
        }

        Button add = Ui.primaryButton("New Reservation", Icons.PLUS);
        add.setOnAction(e -> app.navigate(Screen.FIND_STATIONS));
        toolbar.getChildren().add(add);
        return toolbar;
    }

    private Node buildCard(Reservation r) {
        boolean attention = r.getStatus() == ReservationStatus.ACTION_REQUIRED;
        String tone = switch (r.getStatus()) {
            case CONFIRMED -> "green";
            case ACTION_REQUIRED -> "amber";
            case COMPLETED -> "soft";
            case CANCELLED -> "red";
        };

        HBox idRow = new HBox(10, Ui.label(r.getId(), "res-id"), StatusBadge.forReservation(r.getStatus()));
        idRow.setAlignment(Pos.CENTER_LEFT);
        VBox titles = new VBox(4, idRow, Ui.label(r.getStation().getName(), "card-title"),
                Ui.label(r.getStation().getAddress() + ", " + r.getStation().getArea(), "muted"));
        HBox.setHgrow(titles, Priority.ALWAYS);
        titles.setMinWidth(0);
        HBox header = new HBox(14, Ui.iconTile(Icons.BOLT, tone, 46, 22), titles);
        header.setAlignment(Pos.CENTER_LEFT);

        ResponsiveGrid metrics = new ResponsiveGrid(150, 12);
        metrics.getChildren().addAll(
                Ui.metric(Icons.PLUG, "Charger type", r.getCharger().getConnectorType().getDisplayName()),
                Ui.metric(Icons.BOLT, "Power · Charger", Format.power(r.getCharger().getPowerKw()) + " · " + r.getCharger().getId()),
                Ui.metric(Icons.CALENDAR, "Date", Format.date(r.getDate())),
                Ui.metric(Icons.CLOCK, "Time", r.getTimeSlot()));

        VBox card = Ui.card("card-hover");
        if (attention) {
            card.getStyleClass().add("card-attention");
        }
        card.getChildren().addAll(header, metrics);

        if (attention) {
            card.getChildren().add(Ui.notice(Icons.WARNING,
                    "Your reserved charger is unavailable (simulated event). Choose a compatible alternative.", "warning"));
        } else if (r.getNote() != null) {
            card.getChildren().add(Ui.label(r.getNote(), "small-muted"));
        }

        Button details = Ui.secondaryButton("View Details", null);
        details.setOnAction(e -> app.showReservationDetails(r));
        HBox actions = new HBox(10, details);
        if (r.isActive()) {
            Button alternative = attention
                    ? Ui.primaryButton("Find an Alternative", Icons.SWAP)
                    : Ui.secondaryButton("Find an Alternative", Icons.SWAP);
            alternative.setOnAction(e -> app.requestAlternative(r));
            Button cancel = Ui.dangerButton("Cancel Reservation", null);
            cancel.setOnAction(e -> app.requestCancel(r, null));
            actions.getChildren().addAll(alternative, Ui.hSpacer(), cancel);
        }
        actions.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(actions);
        return card;
    }
}
