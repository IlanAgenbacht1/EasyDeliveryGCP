package com.clone.EasyDelivery.Api.models.request;

/**
 * Request model for validating enrollment key
 */
public class ValidateKeyRequest {
    private String enrollmentKey;
    private String deviceId;
    
    public ValidateKeyRequest() {}
    
    public ValidateKeyRequest(String enrollmentKey, String deviceId) {
        this.enrollmentKey = enrollmentKey;
        this.deviceId = deviceId;
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
}