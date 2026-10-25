package com.vgrid.model;

/** A named starting point (latitude / longitude in degrees). */
public record Location(String name, double latitude, double longitude) {
    @Override
    public String toString() {
        return name;
    }
}
