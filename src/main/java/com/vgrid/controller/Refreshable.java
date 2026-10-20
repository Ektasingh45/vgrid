package com.vgrid.controller;

/** Screens that redraw themselves when the demo data changes (e.g. after a reservation). */
public interface Refreshable {
    void refresh();
}
