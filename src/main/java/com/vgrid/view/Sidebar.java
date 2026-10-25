package com.vgrid.view;

import com.vgrid.controller.AppController;
import com.vgrid.controller.Screen;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.Ui;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;

/** Dark navigation bar on the left side of the window. */
public class Sidebar extends VBox {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final Map<Screen, Button> navButtons = new EnumMap<>(Screen.class);
    private final Label alertBadge = Ui.label("", "nav-badge");

    public Sidebar(AppController app) {
        super(6);
        getStyleClass().add("sidebar");
        setMinWidth(Region.USE_PREF_SIZE);

        // Brand
        VBox brandText = new VBox(0, Ui.label("VGrid", "brand-name"), Ui.label("Smart EV Charging", "brand-tagline"));
        HBox brand = new HBox(12, Ui.iconTile(Icons.BOLT, "brand", 40, 22), brandText);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand");

        getChildren().addAll(brand, Ui.label("MENU", "nav-section"));

        for (Screen screen : Screen.values()) {
            if (!screen.isInSidebar()) {
                continue;
            }
            HBox content = new HBox(12, Icons.create(screen.getIcon(), 18), Ui.label(screen.getTitle(), "nav-label"));
            content.setAlignment(Pos.CENTER_LEFT);
            if (screen == Screen.ALERTS) {
                content.getChildren().addAll(Ui.hSpacer(), alertBadge);
            }
            Button button = new Button();
            button.setGraphic(content);
            button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            content.prefWidthProperty().bind(button.widthProperty().subtract(28));
            button.setOnAction(e -> app.navigate(screen));
            navButtons.put(screen, button);
            getChildren().add(button);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Where the data comes from, at the bottom
        boolean database = app.isDatabaseMode();
        HBox demoTitle = new HBox(8, Icons.create(database ? Icons.DATABASE : Icons.INFO, 16),
                Ui.label(database ? "Saved in MySQL" : "Offline mode", "demo-card-title"));
        demoTitle.setAlignment(Pos.CENTER_LEFT);
        VBox demoCard = new VBox(6, demoTitle, Ui.wrapLabel(database
                ? "Accounts and bookings are saved. Live charger status comes from VGrid bookings, "
                  + "check-ins and driver reports."
                : "MySQL is not connected, so nothing is saved when the app closes.", "demo-card-text"));
        demoCard.getStyleClass().add("demo-card");
        if (database) {
            demoCard.getStyleClass().add("demo-card-db");
        }

        getChildren().addAll(spacer, demoCard);
    }

    public void setSelected(Screen screen) {
        navButtons.forEach((s, button) -> button.pseudoClassStateChanged(SELECTED, s == screen));
    }

    public void setAlertCount(long count) {
        alertBadge.setText(String.valueOf(count));
        alertBadge.setVisible(count > 0);
    }
}
