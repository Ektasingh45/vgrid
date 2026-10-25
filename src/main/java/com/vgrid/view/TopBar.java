package com.vgrid.view;

import com.vgrid.controller.AppController;
import com.vgrid.controller.Screen;
import com.vgrid.model.UserSettings;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.Ui;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** White bar at the top: page title, data-source label, reload, notifications, the user and sign-out. */
public class TopBar extends HBox {
    private final Label title = Ui.label("", "page-title");
    private final Label subtitle = Ui.label("", "page-subtitle");
    private final Label bellBadge = Ui.label("", "bell-badge");
    private final Label profileDetail = Ui.label("", "profile-detail");
    private final Label cityPill = Ui.label("", "city-pill");


    public TopBar(AppController app) {
        super(16);
        getStyleClass().add("top-bar");
        setAlignment(Pos.CENTER_LEFT);

        VBox titles = new VBox(2, title, subtitle);
        titles.setMinWidth(0);

        Label dataPill;
        if (app.isDatabaseMode()) {
            dataPill = Ui.label("MySQL", "db-pill");
            dataPill.setGraphic(Icons.create(Icons.DATABASE, 14));
            dataPill.setTooltip(new Tooltip("Connected to " + app.getDataDescription()
                    + ". Accounts, bookings, check-ins, alerts and settings are saved."));
        } else {
            dataPill = Ui.label("Offline", "demo-pill");
            dataPill.setGraphic(Icons.create(Icons.INFO, 14));
            dataPill.setTooltip(new Tooltip("Offline mode: MySQL is not connected, so nothing is saved after closing."));
        }
        // Keep the chips readable on narrow windows; the page title shrinks instead.
        cityPill.setMinWidth(Region.USE_PREF_SIZE);
        dataPill.setMinWidth(Region.USE_PREF_SIZE);
        cityPill.setGraphic(Icons.create(Icons.PIN, 14));
        cityPill.setTooltip(new Tooltip("Change your city in Settings"));
        cityPill.setOnMouseClicked(e -> app.navigate(Screen.SETTINGS));

        Button reload = new Button();
        reload.setGraphic(Icons.create(Icons.REFRESH, 20));
        reload.getStyleClass().add("icon-button");
        reload.setTooltip(new Tooltip("Reload data (shows changes made in another VGrid window)"));
        reload.setOnAction(e -> app.reloadFromDatabase());

        Button bell = new Button();
        bell.setGraphic(Icons.create(Icons.BELL, 20));
        bell.getStyleClass().add("icon-button");
        bell.setTooltip(new Tooltip("Open alerts"));
        bell.setOnAction(e -> app.navigate(Screen.ALERTS));
        bellBadge.setMouseTransparent(true);
        StackPane bellBox = new StackPane(bell, bellBadge);
        StackPane.setAlignment(bellBadge, Pos.TOP_RIGHT);

        Label avatar = Ui.label(app.getUser().initials(), "avatar");
        VBox who = new VBox(0, Ui.label(app.getUser().name(), "profile-name"), profileDetail);
        HBox profile = new HBox(10, avatar, who);
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.getStyleClass().add("profile-box");
        Tooltip.install(profile, new Tooltip(app.getUser().email() + " · open settings"));
        profile.setOnMouseClicked(e -> app.navigate(Screen.SETTINGS));

        Button signOut = new Button();
        signOut.setGraphic(Icons.create(Icons.LOGOUT, 19));
        signOut.getStyleClass().add("icon-button");
        signOut.setTooltip(new Tooltip("Sign out"));
        signOut.setOnAction(e -> app.requestSignOut());

        getChildren().addAll(titles, Ui.hSpacer(), cityPill, dataPill, reload, bellBox, new Separator(Orientation.VERTICAL),
                profile, signOut);
    }

    public void setScreen(Screen screen) {
        title.setText(screen.getTitle());
        subtitle.setText(screen.getSubtitle());
    }

    public void refresh(long unreadAlerts, UserSettings settings) {
        bellBadge.setText(String.valueOf(unreadAlerts));
        bellBadge.setVisible(unreadAlerts > 0);
        profileDetail.setText("EV owner · " + settings.getPreferredConnector());
        cityPill.setText(settings.getCity().name());
    }
}
