package com.vgrid.view.components;

import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;

/** Factory methods for the small building blocks used on every screen. */
public final class Ui {
    private Ui() {
    }

    // ------------------------------------------------------------------ buttons

    public static Button primaryButton(String text, String icon) {
        return button(text, icon, "btn-primary");
    }

    public static Button secondaryButton(String text, String icon) {
        return button(text, icon, "btn-secondary");
    }

    public static Button ghostButton(String text, String icon) {
        return button(text, icon, "btn-ghost");
    }

    public static Button dangerButton(String text, String icon) {
        return button(text, icon, "btn-danger");
    }

    public static Button button(String text, String icon, String styleClass) {
        Button button = new Button(text);
        if (icon != null) {
            button.setGraphic(Icons.create(icon, 16));
        }
        button.getStyleClass().addAll("btn", styleClass);
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    /** Makes a button stretch to fill the available width of its HBox. */
    public static Button grow(Button button) {
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        return button;
    }

    // ------------------------------------------------------------------ text

    public static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    /** A label that wraps onto several lines instead of being cut off. */
    public static Label wrapLabel(String text, String... styleClasses) {
        Label label = label(text, styleClasses);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    public static Label chip(String text) {
        return label(text, "chip");
    }

    // ------------------------------------------------------------------ layout

    public static Region hSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static VBox card(String... extraStyleClasses) {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.getStyleClass().addAll(extraStyleClasses);
        return card;
    }

    /** Title + optional subtitle on the left, any nodes (usually buttons) on the right. */
    public static HBox sectionHeader(String title, String subtitle, Node... right) {
        VBox titles = new VBox(3, label(title, "section-title"));
        if (subtitle != null) {
            titles.getChildren().add(wrapLabel(subtitle, "section-subtitle"));
        }
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox header = new HBox(12, titles);
        header.getChildren().addAll(right);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /** A form field: small label above a control that fills the width. */
    public static VBox field(String labelText, Node control) {
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return new VBox(6, label(labelText, "field-label"), control);
    }

    /** Coloured rounded square with an icon. tone = green | amber | red | navy | soft | hero */
    public static StackPane iconTile(String icon, String tone) {
        return iconTile(icon, tone, 42, 20);
    }

    public static StackPane iconTile(String icon, String tone, double size, double iconSize) {
        StackPane tile = new StackPane(Icons.create(icon, iconSize));
        tile.getStyleClass().addAll("icon-tile", "tile-" + tone);
        tile.setMinSize(size, size);
        tile.setPrefSize(size, size);
        tile.setMaxSize(size, size);
        return tile;
    }

    public static HBox iconText(String icon, String text) {
        HBox box = new HBox(6, Icons.create(icon, 14), label(text, "icon-text"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("icon-text-box");
        return box;
    }

    /** Small caption + bold value with an icon, used inside cards. */
    public static HBox metric(String icon, String caption, String value) {
        Label valueLabel = label(value, "metric-value");
        VBox texts = new VBox(1, label(caption, "metric-caption"), valueLabel);
        texts.setMinWidth(0);
        HBox box = new HBox(10, iconTile(icon, "soft", 32, 15), texts);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(0);
        return box;
    }

    /** Places cells in a 2-column grid with equal column widths. */
    public static GridPane twoColumnGrid(double hgap, double vgap, Node... cells) {
        GridPane grid = new GridPane();
        grid.setHgap(hgap);
        grid.setVgap(vgap);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            grid.getColumnConstraints().add(column);
        }
        for (int i = 0; i < cells.length; i++) {
            grid.add(cells[i], i % 2, i / 2);
        }
        return grid;
    }

    /** Key/value rows, e.g. {"Address", "Baner Road"}. */
    public static GridPane infoGrid(String[][] rows) {
        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(10);
        ColumnConstraints keyColumn = new ColumnConstraints();
        keyColumn.setMinWidth(130);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyColumn, valueColumn);
        for (int i = 0; i < rows.length; i++) {
            grid.add(label(rows[i][0], "info-key"), 0, i);
            grid.add(wrapLabel(rows[i][1], "info-value"), 1, i);
        }
        return grid;
    }

    /** Coloured message strip. tone = info | warning | danger | success */
    public static HBox notice(String icon, String text, String tone) {
        Label message = wrapLabel(text, "notice-text");
        HBox.setHgrow(message, Priority.ALWAYS);
        HBox box = new HBox(10, Icons.create(icon, 16), message);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().addAll("notice", "notice-" + tone);
        return box;
    }

    // ------------------------------------------------------------------ behaviour

    /** Cards rise slightly when the mouse is over them. */
    public static void hoverLift(Node node) {
        TranslateTransition up = new TranslateTransition(Duration.millis(140), node);
        up.setToY(-3);
        TranslateTransition down = new TranslateTransition(Duration.millis(140), node);
        down.setToY(0);
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            down.stop();
            up.playFromStart();
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            up.stop();
            down.playFromStart();
        });
    }

    /** Prevents a group of tab-like toggle buttons from ending up with nothing selected. */
    public static void keepOneSelected(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
    }
}
