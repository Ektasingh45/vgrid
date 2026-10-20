package com.vgrid.model;

/** A named starting point on the simulated city map (coordinates in km). */
public record Location(String name, double mapX, double mapY) {
    @Override
    public String toString() {
        return name;
    }
}
