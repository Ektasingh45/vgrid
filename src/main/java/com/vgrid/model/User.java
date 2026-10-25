package com.vgrid.model;

/** A signed-in VGrid account. The password hash is never kept in this object. */
public record User(int id, String name, String email) {

    /** "Ekta Singh" -> "ES", used for the avatar in the top bar. */
    public String initials() {
        StringBuilder result = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && result.length() < 2) {
                result.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return result.isEmpty() ? "?" : result.toString();
    }
}
