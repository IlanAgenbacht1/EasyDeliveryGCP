package com.clone.EasyDelivery.Firebase.Models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Firestore Trip model matching Cloud API schema
 * Document path: /trips/{tripId}
 * Status values: available, claimed, in_progress, completed
 */
public class FirestoreTrip {
    
    @DocumentId
    private String id;
    
    @PropertyName("clientId")
    private String clientId;
    
    @PropertyName("status")
    private String status = "available";
    
    @PropertyName("claimedAt")
    private Timestamp claimedAt;
    
    @PropertyName("completedAt") 
    private Timestamp completedAt;
    
    @PropertyName("createdAt")
    @ServerTimestamp
    private Timestamp createdAt;
    
    @PropertyName("sessionInfo")
    private SessionInfo sessionInfo = new SessionInfo();
    
    // Default constructor required for Firestore
    public FirestoreTrip() {}
    
    public FirestoreTrip(String clientId) {
        this.clientId = clientId;
        this.status = "available";
        this.sessionInfo = new SessionInfo();
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Timestamp getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Timestamp claimedAt) { this.claimedAt = claimedAt; }
    
    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public SessionInfo getSessionInfo() { return sessionInfo; }
    public void setSessionInfo(SessionInfo sessionInfo) { this.sessionInfo = sessionInfo; }
    
    /**
     * Check if this trip is available for claiming
     */
    public boolean isAvailable() {
        return "available".equals(status);
    }
    
    /**
     * Check if this trip is claimed by the current device
     */
    public boolean isClaimedBy(String deviceId) {
        return "claimed".equals(status) && 
               sessionInfo != null && 
               deviceId.equals(sessionInfo.getDeviceId());
    }
    
    /**
     * Check if this trip is in progress by the current device
     */
    public boolean isInProgressBy(String deviceId) {
        return "in_progress".equals(status) && 
               sessionInfo != null && 
               deviceId.equals(sessionInfo.getDeviceId());
    }
    
    /**
     * Convert to Map for Firestore batch operations
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("clientId", clientId);
        result.put("status", status);
        result.put("claimedAt", claimedAt);
        result.put("completedAt", completedAt);
        result.put("createdAt", createdAt);
        result.put("sessionInfo", sessionInfo != null ? sessionInfo.toMap() : new HashMap<>());
        return result;
    }
    
    @Override
    public String toString() {
        return "FirestoreTrip{" +
                "id='" + id + '\'' +
                ", clientId='" + clientId + '\'' +
                ", status='" + status + '\'' +
                ", sessionInfo=" + sessionInfo +
                '}';
    }
}