package com.clone.EasyDelivery.viewmodel.models;

/**
 * Enumeration representing the overall application state
 * Used by MainViewModel for global app state management
 */
public enum AppState {
    
    /**
     * App is starting up or initializing
     */
    INITIALIZING,
    
    /**
     * App is running normally
     */
    RUNNING,
    
    /**
     * App is in background or paused
     */
    BACKGROUND,
    
    /**
     * App is synchronizing data
     */
    SYNCING,
    
    /**
     * App has encountered an error
     */
    ERROR,
    
    /**
     * App is shutting down
     */
    SHUTTING_DOWN
}