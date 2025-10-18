package com.clone.EasyDelivery.data.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity for session data
 */
@Entity(
    tableName = "sessions",
    foreignKeys = @ForeignKey(
        entity = DeviceEntity.class,
        parentColumns = "device_id",
        childColumns = "device_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index(value = "device_id"),
        @Index(value = "driver_id"),
        @Index(value = "is_active")
    }
)
public class SessionEntity {
    
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "session_id")
    private String sessionId;
    
    @ColumnInfo(name = "driver_id")
    private String driverId;
    
    @ColumnInfo(name = "device_id")
    private String deviceId;
    
    @ColumnInfo(name = "client_id")
    private String clientId;
    
    @ColumnInfo(name = "vehicle_info")
    private String vehicleInfo;
    
    @ColumnInfo(name = "is_active")
    private boolean isActive;
    
    @ColumnInfo(name = "started_at")
    private long startedAt;
    
    @ColumnInfo(name = "ended_at")
    private Long endedAt;
    
    @ColumnInfo(name = "last_active_at")
    private long lastActiveAt;
    
    // Sync metadata
    @ColumnInfo(name = "synced_at")
    private long syncedAt;
    
    @ColumnInfo(name = "sync_status")
    private String syncStatus;
    
    public SessionEntity() {}
    
    @Ignore
    public SessionEntity(String sessionId, String driverId, String deviceId, String clientId, String vehicleInfo) {
        this.sessionId = sessionId;
        this.driverId = driverId;
        this.deviceId = deviceId;
        this.clientId = clientId;
        this.vehicleInfo = vehicleInfo;
        this.isActive = true;
        this.startedAt = System.currentTimeMillis();
        this.lastActiveAt = System.currentTimeMillis();
        this.syncStatus = "synced";
        this.syncedAt = System.currentTimeMillis();
    }
    
    // Getters and setters
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
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getVehicleInfo() {
        return vehicleInfo;
    }
    
    public void setVehicleInfo(String vehicleInfo) {
        this.vehicleInfo = vehicleInfo;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public long getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }
    
    public Long getEndedAt() {
        return endedAt;
    }
    
    public void setEndedAt(Long endedAt) {
        this.endedAt = endedAt;
    }
    
    public long getLastActiveAt() {
        return lastActiveAt;
    }
    
    public void setLastActiveAt(long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
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
    
    // Helper methods
    public void endSession() {
        this.isActive = false;
        this.endedAt = System.currentTimeMillis();
        this.syncStatus = "pending"; // Mark for sync to server
    }
    
    public void updateLastActive() {
        this.lastActiveAt = System.currentTimeMillis();
        this.syncStatus = "pending"; // Mark for sync to server
    }
    
    public boolean needsSync() {
        return !"synced".equals(syncStatus);
    }
    
    public void markAsSynced() {
        this.syncStatus = "synced";
        this.syncedAt = System.currentTimeMillis();
    }
    
    public void markAsUnsynced() {
        this.syncStatus = "pending";
    }
}
