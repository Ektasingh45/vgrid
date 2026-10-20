package com.vgrid.view.components;

import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Dashboard statistic card: icon, big number, title and caption. */
public class SummaryCard extends VBox {

    public SummaryCard(String icon, String tone, String value, String title, String caption) {
        super(14);
        getStyleClass().addAll("card", "summary-card");

        HBox top = new HBox(Ui.iconTile(icon, tone), Ui.hSpacer(), Icons.create(Icons.ARROW_RIGHT, 16, "icon-faint"));
        top.setAlignment(Pos.CENTER_LEFT);

        VBox texts = new VBox(2, Ui.label(value, "summary-value"), Ui.label(title, "summary-title"));
        getChildren().addAll(top, texts, Ui.wrapLabel(caption, "small-muted"));
    }

    /** Makes the whole card clickable. */
    public SummaryCard onClick(Runnable action) {
        getStyleClass().addAll("clickable", "card-hover");
        setOnMouseClicked(e -> action.run());
        Ui.hoverLift(this);
        return this;
    }
}
