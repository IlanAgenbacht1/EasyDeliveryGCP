package com.clone.EasyDelivery.Api.models.request;

/**
 * Request model for registering a new device
 */
public class RegisterDeviceRequest {
    private String enrollmentKey;
    private String deviceId;
    private String deviceType;
    
    public RegisterDeviceRequest() {}
    
    public RegisterDeviceRequest(String enrollmentKey, String deviceId, String deviceType) {
        this.enrollmentKey = enrollmentKey;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
    }
    
    public String getEnrollmentKey() {
        return enrollmentKey;
    }
    
    public void setEnrollmentKey(String enrollmentKey) {
        this.enrollmentKey = enrollmentKey;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    
    public String getDeviceType() {
        return deviceType;
    }
    
    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }
}