package com.vgrid.view.components;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

/**
 * A grid of equal-width cards that adds or removes columns as the window is resized.
 * Each child gets a width of at least minCellWidth; the remaining space is shared equally.
 */
public class ResponsiveGrid extends FlowPane {
    private final double minCellWidth;
    private boolean balanced;

    public ResponsiveGrid(double minCellWidth, double gap) {
        super(gap, gap);
        this.minCellWidth = minCellWidth;
        widthProperty().addListener((obs, oldWidth, newWidth) -> resizeCells());
        getChildren().addListener((ListChangeListener<Node>) change -> resizeCells());
    }

    /** Avoids a lonely last row, e.g. 4 cards become 2 + 2 instead of 3 + 1. */
    public ResponsiveGrid balanced() {
        this.balanced = true;
        return this;
    }

    private void resizeCells() {
        double width = getWidth() - getInsets().getLeft() - getInsets().getRight();
        if (width <= 0) {
            return;
        }
        int columns = Math.max(1, (int) ((width + getHgap()) / (minCellWidth + getHgap())));
        columns = Math.min(columns, Math.max(1, getChildren().size()));
        while (balanced && columns > 1 && getChildren().size() % columns != 0) {
            columns--;
        }
        double cellWidth = Math.floor((width - getHgap() * (columns - 1)) / columns) - 1;
        for (Node child : getChildren()) {
            if (child instanceof Region region) {
                region.setMinWidth(Math.min(cellWidth, minCellWidth));
                region.setPrefWidth(cellWidth);
                region.setMaxWidth(cellWidth);
            }
        }
    }
}
