package com.clone.EasyDelivery.data.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity for trip data
 */
@Entity(
    tableName = "trips",
    foreignKeys = {
        @ForeignKey(
            entity = SessionEntity.class,
            parentColumns = "session_id",
            childColumns = "session_id",
            onDelete = ForeignKey.SET_NULL
        )
    },
    indices = {
        @Index(value = "client_id"),
        @Index(value = "session_id"),
        @Index(value = "status"),
        @Index(value = "driver_id")
    }
)
public class TripEntity {
    
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "trip_id")
    private String tripId;
    
    @ColumnInfo(name = "client_id")
    private String clientId;
    
    @ColumnInfo(name = "status") // "available", "claimed", "in_progress", "completed"
    private String status;
    
    @ColumnInfo(name = "created_at")
    private long createdAt;
    
    @ColumnInfo(name = "claimed_at")
    private Long claimedAt;
    
    @ColumnInfo(name = "started_at")
    private Long startedAt;
    
    @ColumnInfo(name = "completed_at")
    private Long completedAt;
    
    // Session info
    @ColumnInfo(name = "session_id")
    private String sessionId;
    
    @ColumnInfo(name = "driver_id")
    private String driverId;
    
    @ColumnInfo(name = "vehicle_info")
    private String vehicleInfo;
    
    // Delivery count for progress tracking
    @ColumnInfo(name = "total_deliveries")
    private int totalDeliveries;
    
    @ColumnInfo(name = "completed_deliveries")
    private int completedDeliveries;
    
    // Sync metadata
    @ColumnInfo(name = "synced_at")
    private long syncedAt;
    
    @ColumnInfo(name = "sync_status")
    private String syncStatus;
    
    @ColumnInfo(name = "server_version")
    private String serverVersion;

    @ColumnInfo(name = "updated_at")
    private Long updatedAt;

    public TripEntity() {}
    
    @Ignore
    public TripEntity(String tripId, String clientId, String status, long createdAt) {
        this.tripId = tripId;
        this.clientId = clientId;
        this.status = status;
        this.createdAt = createdAt;
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
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public Long getClaimedAt() {
        return claimedAt;
    }
    
    public void setClaimedAt(Long claimedAt) {
        this.claimedAt = claimedAt;
    }
    
    public Long getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(Long startedAt) {
        this.startedAt = startedAt;
    }
    
    public Long getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(Long completedAt) {
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
    
    public String getVehicleInfo() {
        return vehicleInfo;
    }
    
    public void setVehicleInfo(String vehicleInfo) {
        this.vehicleInfo = vehicleInfo;
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
    
    public long getSyncedAt() {
        return syncedAt;
    }
    
    public void setSyncedAt(long syncedAt) {
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

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods
    public void claimTrip(String sessionId, String driverId) {
        this.status = "claimed";
        this.sessionId = sessionId;
        this.driverId = driverId;
        this.claimedAt = System.currentTimeMillis();
        this.syncStatus = "pending";
    }
    
    public void startTrip() {
        this.status = "in_progress";
        this.startedAt = System.currentTimeMillis();
        this.syncStatus = "pending";
    }
    
    public void completeTrip() {
        this.status = "completed";
        this.completedAt = System.currentTimeMillis();
        this.syncStatus = "pending";
    }
    
    public boolean needsSync() {
        return !"synced".equals(syncStatus);
    }
    
    public void markAsSynced() {
        this.syncStatus = "synced";
        this.syncedAt = System.currentTimeMillis();
    }
    
    public void incrementCompletedDeliveries() {
        this.completedDeliveries++;
        this.syncStatus = "pending";
    }
    
    public float getCompletionPercentage() {
        if (totalDeliveries == 0) return 0f;
        return (float) completedDeliveries / totalDeliveries * 100f;
    }
    
    public boolean isCompleted() {
        return completedDeliveries >= totalDeliveries && totalDeliveries > 0;
    }

    public Double getEstimatedDistance() {
        // TODO: Add estimatedDistance field to entity if needed
        return null; // Placeholder to prevent compilation errors
    }

    public void setEstimatedDistance(Double estimatedDistance) {
        // TODO: Add estimatedDistance field to entity if needed
        // Placeholder to prevent compilation errors
    }

    public Long getEndedAt() {
        return completedAt; // Use completedAt as endedAt
    }

    public void setEndedAt(Long endedAt) {
        this.completedAt = endedAt; // Map endedAt to completedAt
    }

    // Additional methods for ConflictResolver compatibility
    public String getId() {
        return tripId; // Use tripId as id
    }

    public void setId(String id) {
        this.tripId = id; // Map id to tripId
    }

    public String getStartLocation() {
        // TODO: Add startLocation field if needed for full functionality
        return null; // Placeholder to prevent compilation errors
    }

    public void setStartLocation(String startLocation) {
        // TODO: Add startLocation field if needed for full functionality
        // Placeholder to prevent compilation errors
    }

    public String getEndLocation() {
        // TODO: Add endLocation field if needed for full functionality
        return null; // Placeholder to prevent compilation errors
    }

    public void setEndLocation(String endLocation) {
        // TODO: Add endLocation field if needed for full functionality
        // Placeholder to prevent compilation errors
    }
}