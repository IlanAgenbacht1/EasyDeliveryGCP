package com.clone.EasyDelivery.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Unified Trip model that works for both API serialization and Room database
 * Reduces duplication between TripDto and TripEntity
 */
@Entity(
        tableName = "trips",
        indices = {
                @Index(value = "client_id"),
                @Index(value = "session_id"),
                @Index(value = "status")
        }
)
public class Trip {

    @PrimaryKey
    @ColumnInfo(name = "trip_id")
    @SerializedName("tripId")
    private String tripId;

    @ColumnInfo(name = "client_id")
    @SerializedName("clientId")
    private String clientId;

    @ColumnInfo(name = "status")
    @SerializedName("status")
    private String status;

    @ColumnInfo(name = "created_at")
    @SerializedName("createdAt")
    private String createdAt; // Use String to handle ISO 8601 format from API

    @ColumnInfo(name = "claimed_at")
    @SerializedName("claimedAt")
    private String claimedAt;

    @ColumnInfo(name = "completed_at")
    @SerializedName("completedAt")
    private String completedAt;

    // Session info (flattened from SessionInfoDto)
    @ColumnInfo(name = "session_id")
    @SerializedName("sessionId")
    private String sessionId;

    @ColumnInfo(name = "driver_id")
    @SerializedName("driverId")
    private String driverId;

    @ColumnInfo(name = "vehicle_id")
    @SerializedName("vehicleId")
    private String vehicleId;

    // Delivery tracking
    @ColumnInfo(name = "total_deliveries")
    @SerializedName("totalDeliveries")
    private int totalDeliveries;

    @ColumnInfo(name = "completed_deliveries")
    @SerializedName("completedDeliveries")
    private int completedDeliveries;

    // Database-only fields (ignored by JSON serialization)
    @ColumnInfo(name = "synced_at")
    @Ignore // Don't serialize to API
    private Long syncedAt;

    @ColumnInfo(name = "sync_status")
    @Ignore // Don't serialize to API
    private String syncStatus = "synced";

    @ColumnInfo(name = "server_version")
    @Ignore // Don't serialize to API
    private String serverVersion;

    // ISO 8601 date format parser
    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    private static final SimpleDateFormat FALLBACK_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    static {
        ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        FALLBACK_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    // Constructors
    public Trip() {
    }

    @Ignore
    public Trip(String tripId, String clientId, String status) {
        this.tripId = tripId;
        this.clientId = clientId;
        this.status = status;
        this.createdAt = ISO_DATE_FORMAT.format(new Date());
        this.totalDeliveries = 0;
        this.completedDeliveries = 0;
        this.syncStatus = "synced";
        this.syncedAt = System.currentTimeMillis();
    }

    // Getters and setters
    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(String claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public int getTotalDeliveries() {
        return totalDeliveries;
    }

    public void setTotalDeliveries(int totalDeliveries) {
        this.totalDeliveries = totalDeliveries;
    }

    public int getCompletedDeliveries() {
        return completedDeliveries;
    }

    public void setCompletedDeliveries(int completedDeliveries) {
        this.completedDeliveries = completedDeliveries;
    }

    // Database-only getters/setters
    public Long getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Long syncedAt) {
        this.syncedAt = syncedAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    // Business logic methods
    public void claimTrip(String sessionId, String driverId, String vehicleId) {
        this.status = "claimed";
        this.sessionId = sessionId;
        this.driverId = driverId;
        this.vehicleId = vehicleId;
        this.claimedAt = ISO_DATE_FORMAT.format(new Date());
        this.syncStatus = "pending";
    }

    public void completeTrip() {
        this.status = "completed";
        this.completedAt = ISO_DATE_FORMAT.format(new Date());
        this.syncStatus = "pending";
    }

    public boolean needsSync() {
        return !"synced".equals(syncStatus);
    }

    public void markAsSynced() {
        this.syncStatus = "synced";
        this.syncedAt = System.currentTimeMillis();
    }

    public float getCompletionPercentage() {
        if (totalDeliveries == 0) return 0f;
        return (float) completedDeliveries / totalDeliveries * 100f;
    }

    // Compatibility methods for Date conversion (when working with APIs that expect Date objects)
    @Ignore
    public Date getCreatedAtAsDate() {
        try {
            return ISO_DATE_FORMAT.parse(createdAt);
        } catch (ParseException e) {
            try {
                return FALLBACK_DATE_FORMAT.parse(createdAt);
            } catch (ParseException ex) {
                return null;
            }
        }
    }

    @Ignore
    public void setCreatedAtFromDate(Date date) {
        this.createdAt = ISO_DATE_FORMAT.format(date);
    }

    @Ignore
    public Date getClaimedAtAsDate() {
        try {
            return ISO_DATE_FORMAT.parse(claimedAt);
        } catch (ParseException e) {
            try {
                return FALLBACK_DATE_FORMAT.parse(claimedAt);
            } catch (ParseException ex) {
                return null;
            }
        }
    }

    @Ignore
    public Date getCompletedAtAsDate() {
        try {
            return ISO_DATE_FORMAT.parse(completedAt);
        } catch (ParseException e) {
            try {
                return FALLBACK_DATE_FORMAT.parse(completedAt);
            } catch (ParseException ex) {
                return null;
            }
        }
    }

    // =================== DATABASE COMPATIBILITY METHODS ===================

    /**
     * Room expects Long timestamps - convert from ISO string to Long for database storage
     */
    @Ignore
    public Long getCreatedAtAsTimestamp() {
        if (createdAt == null || createdAt.isEmpty()) {
            return null;
        }

        try {
            // Try to parse as Long first (for existing database data)
            return Long.parseLong(createdAt);
        } catch (NumberFormatException e) {
            // Parse as ISO 8601 format
            try {
                Date date = ISO_DATE_FORMAT.parse(createdAt);
                return date.getTime();
            } catch (ParseException ex1) {
                try {
                    Date date = FALLBACK_DATE_FORMAT.parse(createdAt);
                    return date.getTime();
                } catch (ParseException ex2) {
                    android.util.Log.w("Trip", "Could not parse createdAt: " + createdAt);
                    return System.currentTimeMillis(); // Fallback to current time
                }
            }
        }
    }

    @Ignore
    public Long getClaimedAtAsTimestamp() {
        if (claimedAt == null || claimedAt.isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(claimedAt);
        } catch (NumberFormatException e) {
            try {
                Date date = ISO_DATE_FORMAT.parse(claimedAt);
                return date.getTime();
            } catch (ParseException ex1) {
                try {
                    Date date = FALLBACK_DATE_FORMAT.parse(claimedAt);
                    return date.getTime();
                } catch (ParseException ex2) {
                    return null;
                }
            }
        }
    }

    @Ignore
    public Long getCompletedAtAsTimestamp() {
        if (completedAt == null || completedAt.isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(completedAt);
        } catch (NumberFormatException e) {
            try {
                Date date = ISO_DATE_FORMAT.parse(completedAt);
                return date.getTime();
            } catch (ParseException ex1) {
                try {
                    Date date = FALLBACK_DATE_FORMAT.parse(completedAt);
                    return date.getTime();
                } catch (ParseException ex2) {
                    return null;
                }
            }
        }
    }
}