package com.clone.EasyDelivery.models;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Unified Driver model for both API and potential database usage
 * Replaces DriverDto to reduce model duplication
 */
public class Driver {

    @SerializedName("driverId")
    private String driverId;

    @SerializedName("name")
    private String name;

    @SerializedName("isActive")
    private boolean isActive;

    @SerializedName("hasPinSetup")
    private boolean hasPinSetup;

    @SerializedName("createdAt")
    private String createdAt; // Use String to handle ISO 8601 format from API

    // ISO 8601 date format parser
    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    private static final SimpleDateFormat FALLBACK_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    static {
        ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        FALLBACK_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    // Constructors
    public Driver() {
    }

    public Driver(String driverId, String name, boolean isActive, boolean hasPinSetup, String createdAt) {
        this.driverId = driverId;
        this.name = name;
        this.isActive = isActive;
        this.hasPinSetup = hasPinSetup;
        this.createdAt = createdAt;
    }

    // Legacy constructor for backward compatibility
    public Driver(String driverId, String name, boolean isActive, boolean hasPinSetup, Long createdAt) {
        this.driverId = driverId;
        this.name = name;
        this.isActive = isActive;
        this.hasPinSetup = hasPinSetup;
        this.createdAt = createdAt != null ? new Date(createdAt).toString() : null;
    }

    // Getters and setters
    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public boolean getHasPinSetup() {
        return hasPinSetup;
    }

    public void setHasPinSetup(boolean hasPinSetup) {
        this.hasPinSetup = hasPinSetup;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    // Conversion methods for different date formats
    public Long getCreatedAtAsTimestamp() {
        if (createdAt == null || createdAt.isEmpty()) {
            return null;
        }

        try {
            // Try ISO 8601 format with milliseconds first
            Date date = ISO_DATE_FORMAT.parse(createdAt);
            return date.getTime();
        } catch (ParseException e1) {
            try {
                // Try ISO 8601 format without milliseconds
                Date date = FALLBACK_DATE_FORMAT.parse(createdAt);
                return date.getTime();
            } catch (ParseException e2) {
                // If both fail, try to parse as timestamp string
                try {
                    return Long.parseLong(createdAt);
                } catch (NumberFormatException e3) {
                    android.util.Log.w("Driver", "Could not parse createdAt date: " + createdAt);
                    return null;
                }
            }
        }
    }

    public Date getCreatedAtAsDate() {
        Long timestamp = getCreatedAtAsTimestamp();
        return timestamp != null ? new Date(timestamp) : null;
    }

    public void setCreatedAtFromTimestamp(Long timestamp) {
        if (timestamp != null) {
            Date date = new Date(timestamp);
            this.createdAt = ISO_DATE_FORMAT.format(date);
        } else {
            this.createdAt = null;
        }
    }

    public void setCreatedAtFromDate(Date date) {
        if (date != null) {
            this.createdAt = ISO_DATE_FORMAT.format(date);
        } else {
            this.createdAt = null;
        }
    }

    // Formatted display methods
    public String getFormattedCreatedAt() {
        Date date = getCreatedAtAsDate();
        if (date != null) {
            SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            return displayFormat.format(date);
        }
        return "Unknown";
    }

    @Override
    public String toString() {
        return name; // Display driver name in dropdown
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Driver driver = (Driver) obj;
        return driverId != null ? driverId.equals(driver.driverId) : driver.driverId == null;
    }

    @Override
    public int hashCode() {
        return driverId != null ? driverId.hashCode() : 0;
    }
}