package com.vgrid.service;

import com.vgrid.model.User;
import com.vgrid.model.UserSettings;

/** Who is signed in, and their settings. Created at login and thrown away at sign-out. */
public class UserSession {
    private final User user;
    private UserSettings settings;

    public UserSession(User user, UserSettings settings) {
        this.user = user;
        this.settings = settings;
    }

    public User getUser() { return user; }
    public int getUserId() { return user.id(); }
    public UserSettings getSettings() { return settings; }

    void setSettings(UserSettings settings) { this.settings = settings; }
}
