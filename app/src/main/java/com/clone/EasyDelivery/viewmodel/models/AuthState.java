package com.clone.EasyDelivery.viewmodel.models;

/**
 * Enum representing different authentication states in the app
 */
public enum AuthState {
    /**
     * User is not authenticated, device not registered
     */
    NOT_AUTHENTICATED,
    
    /**
     * Device is registered but driver not authenticated
     */
    DEVICE_REGISTERED,
    
    /**
     * Driver is authenticated but no active session
     */
    AUTHENTICATED,
    
    /**
     * Driver is authenticated and has an active session
     */
    SESSION_ACTIVE,
    
    /**
     * Authentication is in progress
     */
    AUTHENTICATING,
    
    /**
     * Authentication failed
     */
    AUTHENTICATION_FAILED
}