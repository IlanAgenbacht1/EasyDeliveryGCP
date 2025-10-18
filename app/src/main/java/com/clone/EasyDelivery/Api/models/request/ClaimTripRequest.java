package com.clone.EasyDelivery.Api.models.request;

/**
 * Request model for claiming a trip
 */
public class ClaimTripRequest {
    private String sessionId;
    
    public ClaimTripRequest() {}
    
    public ClaimTripRequest(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}