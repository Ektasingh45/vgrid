package com.vgrid.controller;

import com.vgrid.view.components.Icons;

/** Every screen of the application, with the text shown in the top bar. */
public enum Screen {
    DASHBOARD("Dashboard", "Overview of your charging activity", Icons.DASHBOARD, true),
    FIND_STATIONS("Find Stations", "Search and filter charging stations near you", Icons.SEARCH, true),
    RECOMMENDATIONS("Smart Recommendations", "Let VGrid rank the best station for your trip", Icons.SPARKLE, true),
    RESERVATIONS("My Reservations", "View and manage your charger bookings", Icons.CALENDAR, true),
    ALERTS("Alerts", "Updates about your reservations and chargers", Icons.BELL, true),
    SETTINGS("Settings", "Your charging preferences", Icons.TUNE, true),
    ALTERNATIVES("Alternative Chargers", "Reservation protection · compatible alternatives", Icons.SWAP, false);

    private final String title;
    private final String subtitle;
    private final String icon;
    private final boolean inSidebar;

    Screen(String title, String subtitle, String icon, boolean inSidebar) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.inSidebar = inSidebar;
    }

    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getIcon() { return icon; }
    public boolean isInSidebar() { return inSidebar; }

    /** Which sidebar item should be highlighted while this screen is open. */
    public Screen getSidebarItem() {
        return this == ALTERNATIVES ? RESERVATIONS : this;
    }
}
