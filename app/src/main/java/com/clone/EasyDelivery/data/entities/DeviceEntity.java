package com.clone.EasyDelivery.data.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Room entity for device data with sync metadata
 */
@Entity(tableName = "devices")
public class DeviceEntity {
    
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "device_id")
    private String deviceId;
    
    @ColumnInfo(name = "client_id")
    private String clientId;
    
    @ColumnInfo(name = "device_type")
    private String deviceType;
    
    @ColumnInfo(name = "is_approved")
    private boolean isApproved;
    
    @ColumnInfo(name = "registered_at")
    private long registeredAt;
    
    @ColumnInfo(name = "last_seen_at")
    private long lastSeenAt;
    
    // Sync metadata
    @ColumnInfo(name = "synced_at")
    private long syncedAt;
    
    @ColumnInfo(name = "sync_status")
    private String syncStatus; // "synced", "pending", "error"
    
    @ColumnInfo(name = "server_version")
    private String serverVersion;
    
    public DeviceEntity() {}
    
    @Ignore
    public DeviceEntity(String deviceId, String clientId, String deviceType, boolean isApproved) {
        this.deviceId = deviceId;
        this.clientId = clientId;
        this.deviceType = deviceType;
        this.isApproved = isApproved;
        this.registeredAt = System.currentTimeMillis();
        this.lastSeenAt = System.currentTimeMillis();
        this.syncStatus = "synced";
        this.syncedAt = System.currentTimeMillis();
    }
    
    // Getters and setters
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
    
    public String getDeviceType() {
        return deviceType;
    }
    
    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }
    
    public boolean isApproved() {
        return isApproved;
    }
    
    public void setApproved(boolean approved) {
        isApproved = approved;
    }
    
    public long getRegisteredAt() {
        return registeredAt;
    }
    
    public void setRegisteredAt(long registeredAt) {
        this.registeredAt = registeredAt;
    }
    
    public long getLastSeenAt() {
        return lastSeenAt;
    }
    
    public void setLastSeenAt(long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
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
    
    // Helper methods
    public boolean needsSync() {
        return !"synced".equals(syncStatus);
    }
    
    public void markAsSynced() {
        this.syncStatus = "synced";
        this.syncedAt = System.currentTimeMillis();
    }
    
    public void markAsPending() {
        this.syncStatus = "pending";
    }
    
    public void markAsError() {
        this.syncStatus = "error";
    }
}