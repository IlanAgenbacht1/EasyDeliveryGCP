package com.clone.EasyDelivery.Api.models.request;

/**
 * Request model for starting a session
 */
public class StartSessionRequest {
    private String driverId;
    private String deviceId;
    private String clientId;
    private String vehicleId;
    
    public StartSessionRequest() {}

    public StartSessionRequest(String driverId, String deviceId, String clientId, String vehicleId) {
        this.driverId = driverId;
        this.deviceId = deviceId;
        this.clientId = clientId;
        this.vehicleId = vehicleId;
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

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }
}