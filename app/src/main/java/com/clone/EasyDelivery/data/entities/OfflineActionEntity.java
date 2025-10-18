package com.clone.EasyDelivery.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Entity for tracking offline actions to be synchronized
 */
@Entity(tableName = "offline_actions")
public class OfflineActionEntity {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    @ColumnInfo(name = "action_type")
    private String actionType;
    
    @ColumnInfo(name = "resource_id")
    private String resourceId;
    
    @ColumnInfo(name = "data_json")
    private String dataJson;
    
    @ColumnInfo(name = "status")
    private String status;
    
    @ColumnInfo(name = "created_at")
    private long createdAt;
    
    @ColumnInfo(name = "updated_at")
    private long updatedAt;
    
    @ColumnInfo(name = "retry_count")
    private int retryCount;
    
    @ColumnInfo(name = "next_retry_time")
    private long nextRetryTime;
    
    @ColumnInfo(name = "priority")
    private int priority;
    
    @ColumnInfo(name = "session_id")
    private String sessionId;
    
    @ColumnInfo(name = "error_message")
    private String errorMessage;
    
    @ColumnInfo(name = "last_attempt_at")
    private Long lastAttemptAt;
    
    @ColumnInfo(name = "next_retry_at")
    private Long nextRetryAt;
    
    @ColumnInfo(name = "max_retries")
    private int maxRetries;
    
    @ColumnInfo(name = "entity_type")
    private String entityType;

    // Constructors
    public OfflineActionEntity() {}
    
    @Ignore
    public OfflineActionEntity(String entityType, String entityId, String actionType, 
                             String dataJson, long createdAt, String sessionId) {
        this.entityType = entityType;
        this.resourceId = entityId;  // resourceId stores the entity ID
        this.actionType = actionType;
        this.dataJson = dataJson;
        this.status = "PENDING";
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.sessionId = sessionId;
        this.retryCount = 0;
        this.priority = 0;
        this.maxRetries = 3; // Default max retries
    }

    // Additional constructor for legacy compatibility
    @Ignore
    public OfflineActionEntity(String actionType, String resourceId, String dataJson,
                               String status, long createdAt) {
        this.actionType = actionType;
        this.resourceId = resourceId;
        this.dataJson = dataJson;
        this.status = status != null ? status : "PENDING";
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.sessionId = ""; // Default session
        this.retryCount = 0;
        this.priority = 0;
        this.maxRetries = 3;
        this.entityType = "UNKNOWN"; // Default entity type
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    
    public long getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(long nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Long getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Long lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    
    public Long getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Long nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    
    // Convenience method to get entity ID (alias for resourceId)
    public String getEntityId() { return resourceId; }
    public void setEntityId(String entityId) { this.resourceId = entityId; }
}
