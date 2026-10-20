package com.vgrid.model;

/** Charging connector standards used in the demo data. */
public enum ConnectorType {
    CCS2("CCS2", "DC fast charging"),
    TYPE_2("Type 2", "AC charging"),
    CHADEMO("CHAdeMO", "DC fast charging"),
    BHARAT_DC001("Bharat DC-001", "DC charging");

    private final String displayName;
    private final String description;

    ConnectorType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** Finds a connector by the name shown in the UI, or returns null. */
    public static ConnectorType fromDisplayName(String name) {
        for (ConnectorType type : values()) {
            if (type.displayName.equals(name)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
