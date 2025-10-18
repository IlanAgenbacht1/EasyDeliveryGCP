package com.clone.EasyDelivery.Api.models.response;

/**
 * Response model for enrollment key validation
 */
public class ValidateKeyResponse extends BaseApiResponse {
    private boolean valid;
    private String clientId;
    private String clientName;
    private int devicesRegistered;
    private int deviceLimit;
    
    public ValidateKeyResponse() {}
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getClientName() {
        return clientName;
    }
    
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
    
    public int getDevicesRegistered() {
        return devicesRegistered;
    }
    
    public void setDevicesRegistered(int devicesRegistered) {
        this.devicesRegistered = devicesRegistered;
    }
    
    public int getDeviceLimit() {
        return deviceLimit;
    }
    
    public void setDeviceLimit(int deviceLimit) {
        this.deviceLimit = deviceLimit;
    }
}