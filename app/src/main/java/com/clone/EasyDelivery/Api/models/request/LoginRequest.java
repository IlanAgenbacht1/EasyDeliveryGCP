package com.clone.EasyDelivery.Api.models.request;

/**
 * Request model for driver login
 */
public class LoginRequest {
    private String driverId;
    private String pin;
    private String deviceId;
    
    public LoginRequest() {}

    public LoginRequest(String driverId, String pin, String deviceId) {
        this.driverId = driverId;
        this.pin = pin;
        this.deviceId = deviceId;
    }
    
    public String getDriverId() {
        return driverId;
    }
    
    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}