package com.clone.EasyDelivery.Firebase.Models;

import com.google.firebase.firestore.PropertyName;

import java.util.HashMap;
import java.util.Map;

/**
 * Session information for trip claiming and driver identification
 */
public class SessionInfo {
    
    @PropertyName("deviceId")
    private String deviceId = "";
    
    @PropertyName("driverId")
    private String driverId = "";
    
    @PropertyName("vehicleId")
    private String vehicleId = "";
    
    // Default constructor required for Firestore
    public SessionInfo() {}
    
    public SessionInfo(String deviceId, String driverId, String vehicleId) {
        this.deviceId = deviceId != null ? deviceId : "";
        this.driverId = driverId != null ? driverId : "";
        this.vehicleId = vehicleId != null ? vehicleId : "";
    }
    
    // Getters and setters
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId != null ? deviceId : ""; }
    
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId != null ? driverId : ""; }
    
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId != null ? vehicleId : ""; }
    
    /**
     * Check if session info is valid (has required fields)
     */
    public boolean isValid() {
        return deviceId != null && !deviceId.trim().isEmpty();
    }
    
    /**
     * Convert to Map for Firestore operations
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("driverId", driverId);
        result.put("vehicleId", vehicleId);
        return result;
    }
    
    @Override
    public String toString() {
        return "SessionInfo{" +
                "deviceId='" + deviceId + '\'' +
                ", driverId='" + driverId + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                '}';
    }
}