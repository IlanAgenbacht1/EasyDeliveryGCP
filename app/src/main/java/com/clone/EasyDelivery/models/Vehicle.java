package com.clone.EasyDelivery.models;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Unified Vehicle model for both API and potential database usage
 * Replaces VehicleDto to reduce model duplication
 */
public class Vehicle {

    @SerializedName("vehicleId")
    private String vehicleId;

    @SerializedName("registrationNumber")
    private String registrationNumber;

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
    public Vehicle() {
    }

    public Vehicle(String vehicleId, String registrationNumber, String createdAt) {
        this.vehicleId = vehicleId;
        this.registrationNumber = registrationNumber;
        this.createdAt = createdAt;
    }

    // Legacy constructor for backward compatibility
    public Vehicle(String vehicleId, String registrationNumber, Long createdAt) {
        this.vehicleId = vehicleId;
        this.registrationNumber = registrationNumber;
        this.createdAt = createdAt != null ? new Date(createdAt).toString() : null;
    }

    // Getters and setters
    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
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
                    android.util.Log.w("Vehicle", "Could not parse createdAt date: " + createdAt);
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
        return registrationNumber; // Display vehicle registration number in dropdown
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Vehicle vehicle = (Vehicle) obj;
        return vehicleId != null ? vehicleId.equals(vehicle.vehicleId) : vehicle.vehicleId == null;
    }

    @Override
    public int hashCode() {
        return vehicleId != null ? vehicleId.hashCode() : 0;
    }
}