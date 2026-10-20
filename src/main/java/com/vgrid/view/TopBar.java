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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** White bar at the top: page title, demo label, notifications and the demo user. */
public class TopBar extends HBox {
    private final Label title = Ui.label("", "page-title");
    private final Label subtitle = Ui.label("", "page-subtitle");
    private final Label bellBadge = Ui.label("", "bell-badge");
    private final Label profileDetail = Ui.label("", "profile-detail");

    public TopBar(AppController app) {
        super(16);
        getStyleClass().add("top-bar");
        setAlignment(Pos.CENTER_LEFT);

        VBox titles = new VBox(2, title, subtitle);
        titles.setMinWidth(0);

        Label demoPill = Ui.label("Demo data", "demo-pill");
        demoPill.setGraphic(Icons.create(Icons.INFO, 14));
        demoPill.setTooltip(new Tooltip("Everything in this app is simulated sample data."));

        Button bell = new Button();
        bell.setGraphic(Icons.create(Icons.BELL, 20));
        bell.getStyleClass().add("icon-button");
        bell.setTooltip(new Tooltip("Open alerts"));
        bell.setOnAction(e -> app.navigate(Screen.ALERTS));
        bellBadge.setMouseTransparent(true);
        StackPane bellBox = new StackPane(bell, bellBadge);
        StackPane.setAlignment(bellBadge, Pos.TOP_RIGHT);

        Label avatar = Ui.label("DU", "avatar");
        VBox who = new VBox(0, Ui.label("Demo User", "profile-name"), profileDetail);
        HBox profile = new HBox(10, avatar, who);
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.getStyleClass().add("profile-box");
        Tooltip.install(profile, new Tooltip("Open settings"));
        profile.setOnMouseClicked(e -> app.navigate(Screen.SETTINGS));

        getChildren().addAll(titles, Ui.hSpacer(), demoPill, bellBox, new Separator(Orientation.VERTICAL), profile);
    }

    public void setScreen(Screen screen) {
        title.setText(screen.getTitle());
        subtitle.setText(screen.getSubtitle());
    }

    public void refresh(long unreadAlerts, UserSettings settings) {
        bellBadge.setText(String.valueOf(unreadAlerts));
        bellBadge.setVisible(unreadAlerts > 0);
        profileDetail.setText("EV owner · " + settings.getPreferredConnector());
    }
}
