package com.vgrid.view;

import com.vgrid.controller.AppController;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/** Common parent of all screens: a vertically scrolling page with consistent padding. */
public abstract class BaseView extends ScrollPane {
    protected final AppController app;
    protected final VBox content = new VBox(24);

    protected BaseView(AppController app) {
        this.app = app;
        content.getStyleClass().add("page");
        setContent(content);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("page-scroll");
    }

    /** Scrolls so that the given direct child of the page is near the top. */
    protected void scrollTo(Node child) {
        Platform.runLater(() -> {
            layout();
            double contentHeight = content.getHeight();
            double viewportHeight = getViewportBounds().getHeight();
            if (contentHeight > viewportHeight) {
                double y = child.getBoundsInParent().getMinY() - 12;
                setVvalue(Math.max(0, Math.min(1, y / (contentHeight - viewportHeight))));
            }
        });
    }
}
