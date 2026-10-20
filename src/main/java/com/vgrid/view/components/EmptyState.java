package com.vgrid.view.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

/** Friendly placeholder shown when a list has nothing to display. */
public class EmptyState extends VBox {

    public EmptyState(String icon, String title, String message, String actionText, Runnable action) {
        super(10);
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);
        setFillWidth(false);

        getChildren().addAll(
                Ui.iconTile(icon, "green", 64, 28),
                Ui.label(title, "empty-title"),
                Ui.wrapLabel(message, "empty-message"));
        getChildren().get(2).setStyle("-fx-max-width: 420;");

        if (actionText != null && action != null) {
            Button button = Ui.primaryButton(actionText, null);
            button.setOnAction(e -> action.run());
            VBox.setMargin(button, new javafx.geometry.Insets(8, 0, 0, 0));
            getChildren().add(button);
        }
    }
}
