package com.vgrid.view.dialogs;

import com.vgrid.controller.AppController;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Standard dialog layout: header with close button, body, and a footer for buttons. */
public class DialogShell extends VBox {
    protected final AppController app;
    protected final VBox body = new VBox(18);
    private final HBox footer = new HBox(10);

    public DialogShell(AppController app, String title, String subtitle, double width) {
        this.app = app;
        getStyleClass().add("modal-card");
        setPrefWidth(width);
        setMaxWidth(width);

        VBox titles = new VBox(3, Ui.label(title, "dialog-title"));
        if (subtitle != null) {
            titles.getChildren().add(Ui.wrapLabel(subtitle, "dialog-subtitle"));
        }
        HBox.setHgrow(titles, Priority.ALWAYS);

        Button close = new Button();
        close.setGraphic(Icons.create(Icons.CLOSE, 18));
        close.getStyleClass().add("icon-button");
        close.setOnAction(e -> app.closeModal());

        HBox header = new HBox(12, titles, close);
        header.setAlignment(Pos.TOP_LEFT);
        header.getStyleClass().add("dialog-header");

        body.getStyleClass().add("dialog-body");
        footer.getStyleClass().add("dialog-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(header, body, footer);
    }

    protected void setFooter(Node... nodes) {
        footer.getChildren().setAll(nodes);
    }
}
