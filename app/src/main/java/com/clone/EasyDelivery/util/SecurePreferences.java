package com.clone.EasyDelivery.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Secure preferences using Android's EncryptedSharedPreferences
 * for storing sensitive data like session tokens and driver credentials
 */
public class SecurePreferences {
    
    private static final String TAG = "SecurePreferences";
    private static final String PREFS_NAME = "secure_auth_prefs";
    
    private static volatile SecurePreferences INSTANCE;
    private final SharedPreferences encryptedPrefs;
    
    private SecurePreferences(Context context) throws GeneralSecurityException, IOException {
        // Create master key for encryption
        MasterKey masterKey = new MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build();
        
        // Create encrypted shared preferences
        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
        
        Log.i(TAG, "SecurePreferences initialized successfully");
    }
    
    public static SecurePreferences getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SecurePreferences.class) {
                if (INSTANCE == null) {
                    try {
                        INSTANCE = new SecurePreferences(context.getApplicationContext());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to initialize SecurePreferences, falling back to regular SharedPreferences", e);
                        
                        // Fallback to regular SharedPreferences if encryption fails
                        INSTANCE = new SecurePreferences();
                        INSTANCE.initFallback(context.getApplicationContext());
                    }
                }
            }
        }
        return INSTANCE;
    }
    
    // Fallback constructor for regular SharedPreferences
    private SecurePreferences() {
        this.encryptedPrefs = null;
    }
    
    private void initFallback(Context context) {
        try {
            // Use reflection to set the fallback preferences
            java.lang.reflect.Field field = SecurePreferences.class.getDeclaredField("encryptedPrefs");
            field.setAccessible(true);
            field.set(this, context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE));
            Log.w(TAG, "Using fallback SharedPreferences (not encrypted)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize fallback preferences", e);
            throw new RuntimeException("Cannot initialize preferences", e);
        }
    }
    
    // =================== STRING OPERATIONS ===================
    
    /**
     * Store a string value securely
     */
    public void storeString(String key, String value) {
        try {
            encryptedPrefs.edit()
                .putString(key, value)
                .apply();
            
            Log.d(TAG, "Stored string value for key: " + key);
        } catch (Exception e) {
            Log.e(TAG, "Error storing string value for key: " + key, e);
        }
    }
    
    /**
     * Retrieve a string value
     */
    public String getString(String key, String defaultValue) {
        try {
            String value = encryptedPrefs.getString(key, defaultValue);
            Log.d(TAG, "Retrieved string value for key: " + key + " (has value: " + (value != null) + ")");
            return value;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving string value for key: " + key, e);
            return defaultValue;
        }
    }
    
    // =================== BOOLEAN OPERATIONS ===================
    
    /**
     * Store a boolean value securely
     */
    public void storeBoolean(String key, boolean value) {
        try {
            encryptedPrefs.edit()
                .putBoolean(key, value)
                .apply();
            
            Log.d(TAG, "Stored boolean value for key: " + key + " = " + value);
        } catch (Exception e) {
            Log.e(TAG, "Error storing boolean value for key: " + key, e);
        }
    }
    
    /**
     * Retrieve a boolean value
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        try {
            boolean value = encryptedPrefs.getBoolean(key, defaultValue);
            Log.d(TAG, "Retrieved boolean value for key: " + key + " = " + value);
            return value;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving boolean value for key: " + key, e);
            return defaultValue;
        }
    }
    
    // =================== LONG OPERATIONS ===================
    
    /**
     * Store a long value securely
     */
    public void storeLong(String key, long value) {
        try {
            encryptedPrefs.edit()
                .putLong(key, value)
                .apply();
            
            Log.d(TAG, "Stored long value for key: " + key);
        } catch (Exception e) {
            Log.e(TAG, "Error storing long value for key: " + key, e);
        }
    }
    
    /**
     * Retrieve a long value
     */
    public long getLong(String key, long defaultValue) {
        try {
            long value = encryptedPrefs.getLong(key, defaultValue);
            Log.d(TAG, "Retrieved long value for key: " + key);
            return value;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving long value for key: " + key, e);
            return defaultValue;
        }
    }
    
    // =================== INT OPERATIONS ===================
    
    /**
     * Store an int value securely
     */
    public void storeInt(String key, int value) {
        try {
            encryptedPrefs.edit()
                .putInt(key, value)
                .apply();
            
            Log.d(TAG, "Stored int value for key: " + key);
        } catch (Exception e) {
            Log.e(TAG, "Error storing int value for key: " + key, e);
        }
    }
    
    /**
     * Retrieve an int value
     */
    public int getInt(String key, int defaultValue) {
        try {
            int value = encryptedPrefs.getInt(key, defaultValue);
            Log.d(TAG, "Retrieved int value for key: " + key);
            return value;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving int value for key: " + key, e);
            return defaultValue;
        }
    }
    
    // =================== UTILITY OPERATIONS ===================
    
    /**
     * Check if a key exists
     */
    public boolean contains(String key) {
        try {
            boolean exists = encryptedPrefs.contains(key);
            Log.d(TAG, "Key exists check for: " + key + " = " + exists);
            return exists;
        } catch (Exception e) {
            Log.e(TAG, "Error checking if key exists: " + key, e);
            return false;
        }
    }
    
    /**
     * Remove a key and its value
     */
    public void removeKey(String key) {
        try {
            encryptedPrefs.edit()
                .remove(key)
                .apply();
            
            Log.d(TAG, "Removed key: " + key);
        } catch (Exception e) {
            Log.e(TAG, "Error removing key: " + key, e);
        }
    }
    
    /**
     * Clear all stored preferences
     */
    public void clear() {
        try {
            encryptedPrefs.edit()
                .clear()
                .apply();
            
            Log.i(TAG, "Cleared all secure preferences");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing secure preferences", e);
        }
    }
    
    /**
     * Get all keys (for debugging - be careful with sensitive data)
     */
    public java.util.Set<String> getAllKeys() {
        try {
            return encryptedPrefs.getAll().keySet();
        } catch (Exception e) {
            Log.e(TAG, "Error getting all keys", e);
            return new java.util.HashSet<>();
        }
    }
    
    /**
     * Get count of stored items
     */
    public int getStoredItemCount() {
        try {
            return encryptedPrefs.getAll().size();
        } catch (Exception e) {
            Log.e(TAG, "Error getting stored item count", e);
            return 0;
        }
    }
    
    // =================== SESSION-SPECIFIC HELPERS ===================
    
    /**
     * Store session token securely
     */
    public void storeSessionToken(String token) {
        storeString("session_token", token);
    }
    
    /**
     * Get session token
     */
    public String getSessionToken() {
        return getString("session_token", null);
    }
    
    /**
     * Clear session token
     */
    public void clearSessionToken() {
        removeKey("session_token");
    }
    
    /**
     * Store PIN hash securely (for offline authentication)
     */
    public void storePinHash(String driverId, String pinHash) {
        storeString("pin_hash_" + driverId, pinHash);
    }
    
    /**
     * Get PIN hash for driver
     */
    public String getPinHash(String driverId) {
        return getString("pin_hash_" + driverId, null);
    }
    
    /**
     * Clear PIN hash for driver
     */
    public void clearPinHash(String driverId) {
        removeKey("pin_hash_" + driverId);
    }
    
    /**
     * Store last login timestamp
     */
    public void storeLastLoginTime() {
        storeLong("last_login_time", System.currentTimeMillis());
    }
    
    /**
     * Get last login timestamp
     */
    public long getLastLoginTime() {
        return getLong("last_login_time", 0);
    }
    
    /**
     * Check if this is the first launch
     */
    public boolean isFirstLaunch() {
        boolean isFirst = !contains("app_launched_before");
        if (isFirst) {
            storeBoolean("app_launched_before", true);
        }
        return isFirst;
    }
}
