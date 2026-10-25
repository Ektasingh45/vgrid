package com.vgrid.service;

import com.vgrid.dao.SettingsDao;
import com.vgrid.model.City;
import com.vgrid.model.UserSettings;

/** Loads and saves the signed-in user's charging preferences (including their city). */
public class SettingsService {
    private final SettingsDao settings;
    private final UserSession session;

    public SettingsService(SettingsDao settings, UserSession session) {
        this.settings = settings;
        this.session = session;
    }

    public UserSettings getSettings() {
        return session.getSettings();
    }

    public void save(UserSettings changed) {
        settings.saveSettings(session.getUserId(), changed);
        session.setSettings(changed);
    }

    public UserSettings defaults(City city) {
        return UserSettings.defaults(city);
    }
}
