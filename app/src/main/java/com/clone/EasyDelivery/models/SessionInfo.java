package com.clone.EasyDelivery.models;

import com.google.gson.annotations.SerializedName;

/**
 * Unified SessionInfo model for API responses
 * Replaces SessionInfoDto to reduce model duplication
 */
public class SessionInfo {

    @SerializedName("sessionId")
    private String sessionId;

    @SerializedName("driverId")
    private String driverId;

    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("clientId")
    private String clientId;

    @SerializedName("vehicleInfo")
    private String vehicleInfo;

    @SerializedName("isActive")
    private boolean isActive;

    @SerializedName("startedAt")
    private Long startedAt; // Use Long for consistency

    @SerializedName("lastActiveAt")
    private Long lastActiveAt;

    // Constructors
    public SessionInfo() {
    }

    public SessionInfo(String sessionId, String driverId, String deviceId, String clientId,
                       String vehicleInfo, boolean isActive, Long startedAt, Long lastActiveAt) {
        this.sessionId = sessionId;
        this.driverId = driverId;
        this.deviceId = deviceId;
        this.clientId = clientId;
        this.vehicleInfo = vehicleInfo;
        this.isActive = isActive;
        this.startedAt = startedAt;
        this.lastActiveAt = lastActiveAt;
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
        this.isActive = active;
    }

    public Long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Long startedAt) {
        this.startedAt = startedAt;
    }

    public Long getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    // Compatibility methods for Date conversion (if needed)
    public java.util.Date getStartedAtAsDate() {
        return startedAt != null ? new java.util.Date(startedAt) : null;
    }

    public java.util.Date getLastActiveAtAsDate() {
        return lastActiveAt != null ? new java.util.Date(lastActiveAt) : null;
    }
}