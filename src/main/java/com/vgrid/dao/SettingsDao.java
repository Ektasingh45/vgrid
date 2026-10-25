package com.vgrid.dao;

import com.vgrid.model.UserSettings;

/** Charging preferences of each user, including their city. */
public interface SettingsDao {

    /** The user's saved settings, or the defaults (Pune) if they never saved any. */
    UserSettings loadSettings(int userId);

    /** Inserts or replaces the user's settings. */
    void saveSettings(int userId, UserSettings settings);
}
