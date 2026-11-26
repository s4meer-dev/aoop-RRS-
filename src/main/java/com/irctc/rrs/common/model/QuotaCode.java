package com.irctc.rrs.common.model;

/**
 * Supported Indian Railways booking quotas mapped polymorphically via Strategy Pattern.
 */
public enum QuotaCode {
    GN("General Quota"),
    TQ("Tatkal Quota"),
    LD("Ladies Quota"),
    SS("Senior Citizen Quota");

    private final String displayName;

    QuotaCode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
