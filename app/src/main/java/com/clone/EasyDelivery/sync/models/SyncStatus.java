package com.clone.EasyDelivery.sync.models;

/**
 * Enum representing the current status of synchronization operations
 */
public enum SyncStatus {
    /**
     * No sync operation is currently running
     */
    IDLE,
    
    /**
     * Sync operation is in progress
     */
    SYNCING,
    
    /**
     * Last sync operation completed successfully
     */
    SUCCESS,
    
    /**
     * Sync completed successfully (alias for SUCCESS)
     */
    COMPLETED,

    /**
     * Last sync operation failed
     */
    ERROR,
    
    /**
     * Sync operation completed with some failures (partial success)
     */
    PARTIAL_SUCCESS,
    
    /**
     * Network is not available for sync
     */
    NETWORK_UNAVAILABLE,
    
    /**
     * Sync is paused (user disabled or battery optimization)
     */
    PAUSED
}