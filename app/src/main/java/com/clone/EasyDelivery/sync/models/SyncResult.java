package com.clone.EasyDelivery.sync.models;

import java.util.List;

/**
 * Result object for sync operations containing status, messages, and metrics
 */
public class SyncResult {
    
    private final boolean success;
    private final boolean partialSuccess;
    private final String message;
    private final String errorMessage;
    private final int syncedCount;
    private final List<String> errors;
    private final long timestamp;
    
    private SyncResult(boolean success, boolean partialSuccess, String message, 
                      String errorMessage, int syncedCount, List<String> errors) {
        this.success = success;
        this.partialSuccess = partialSuccess;
        this.message = message;
        this.errorMessage = errorMessage;
        this.syncedCount = syncedCount;
        this.errors = errors;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Create a successful sync result
     */
    public static SyncResult success(String message, int syncedCount) {
        return new SyncResult(true, false, message, null, syncedCount, null);
    }
    
    /**
     * Create a successful sync result with zero count
     */
    public static SyncResult success(String message) {
        return success(message, 0);
    }
    
    /**
     * Create an error sync result
     */
    public static SyncResult error(String errorMessage) {
        return new SyncResult(false, false, null, errorMessage, 0, null);
    }
    
    /**
     * Create a partial success sync result (some operations succeeded, some failed)
     */
    public static SyncResult partialSuccess(String message, int syncedCount, List<String> errors) {
        return new SyncResult(false, true, message, null, syncedCount, errors);
    }
    
    // Getters
    
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isPartialSuccess() {
        return partialSuccess;
    }
    
    public boolean hasErrors() {
        return !success && !partialSuccess;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public int getSyncedCount() {
        return syncedCount;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Get a summary of the sync result
     */
    public String getSummary() {
        if (success) {
            return String.format("Success: %s (%d items synced)", message, syncedCount);
        } else if (partialSuccess) {
            return String.format("Partial Success: %s (%d items synced, %d errors)", 
                message, syncedCount, errors != null ? errors.size() : 0);
        } else {
            return String.format("Error: %s", errorMessage);
        }
    }
    
    /**
     * Get detailed error information if available
     */
    public String getDetailedErrors() {
        if (errors == null || errors.isEmpty()) {
            return errorMessage;
        }
        
        StringBuilder sb = new StringBuilder();
        if (errorMessage != null) {
            sb.append(errorMessage).append("\n");
        }
        
        sb.append("Detailed errors:\n");
        for (int i = 0; i < errors.size(); i++) {
            sb.append(String.format("%d. %s\n", i + 1, errors.get(i)));
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "SyncResult{" +
                "success=" + success +
                ", partialSuccess=" + partialSuccess +
                ", message='" + message + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", syncedCount=" + syncedCount +
                ", errorsCount=" + (errors != null ? errors.size() : 0) +
                ", timestamp=" + timestamp +
                '}';
    }
}