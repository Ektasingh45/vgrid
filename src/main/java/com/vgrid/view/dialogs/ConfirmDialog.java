package com.vgrid.view.dialogs;

import com.vgrid.controller.AppController;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.Ui;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** "Are you sure?" dialog. danger = true shows a red confirm button. */
public class ConfirmDialog extends VBox {

    public ConfirmDialog(AppController app, String title, String message, String confirmText,
                         boolean danger, Runnable onConfirm) {
        super(14);
        getStyleClass().addAll("modal-card", "confirm-card");
        setPrefWidth(460);
        setMaxWidth(460);

        Button back = Ui.secondaryButton("Go Back", null);
        back.setOnAction(e -> app.closeModal());
        Button confirm = danger ? Ui.dangerButton(confirmText, null) : Ui.primaryButton(confirmText, null);
        confirm.setDefaultButton(true);
        confirm.setOnAction(e -> {
            app.closeModal();
            onConfirm.run();
        });

        HBox buttons = new HBox(10, back, confirm);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new javafx.geometry.Insets(8, 0, 0, 0));

        getChildren().addAll(
                Ui.iconTile(danger ? Icons.WARNING : Icons.INFO, danger ? "red" : "green", 48, 24),
                Ui.wrapLabel(title, "dialog-title"),
                Ui.wrapLabel(message, "dialog-message"),
                buttons);
    }
}
