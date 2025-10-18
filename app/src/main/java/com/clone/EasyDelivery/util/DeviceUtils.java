package com.clone.EasyDelivery.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.security.MessageDigest;
import java.util.UUID;

/**
 * Utility class for device identification and information
 */
public class DeviceUtils {
    
    private static final String TAG = "DeviceUtils";
    private static final String PREFS_NAME = "device_prefs";
    private static final String KEY_DEVICE_UUID = "device_uuid";
    
    /**
     * Get unique device ID
     * Uses Android ID as primary identifier with UUID fallback
     */
    @SuppressLint("HardwareIds")
    public static String getDeviceId(Context context) {
        try {
            // Try to get Android ID first
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            
            if (androidId != null && !androidId.equals("9774d56d682e549c") && !androidId.isEmpty()) {
                // Create a hash of the Android ID for privacy
                return "android_" + hashString(androidId);
            }
            
            // Fallback to stored UUID or create new one
            return getOrCreateUUID(context);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting device ID", e);
            return getOrCreateUUID(context);
        }
    }
    
    /**
     * Get or create a persistent UUID for this device
     */
    private static String getOrCreateUUID(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uuid = prefs.getString(KEY_DEVICE_UUID, null);
        
        if (uuid == null) {
            uuid = "uuid_" + UUID.randomUUID().toString().replace("-", "");
            prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply();
            Log.i(TAG, "Generated new device UUID: " + uuid);
        }
        
        return uuid;
    }
    
    /**
     * Get device type information
     */
    public static String getDeviceType() {
        try {
            boolean isTablet = isTablet();
            String deviceType = isTablet ? "tablet" : "phone";
            
            return String.format("%s_%s_%s", 
                deviceType,
                Build.MANUFACTURER.toLowerCase().replace(" ", "_"),
                Build.MODEL.toLowerCase().replace(" ", "_"));
                
        } catch (Exception e) {
            Log.e(TAG, "Error getting device type", e);
            return "android_device";
        }
    }
    
    /**
     * Check if device is a tablet
     */
    private static boolean isTablet() {
        return (android.content.res.Resources.getSystem().getConfiguration().screenLayout
                & android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK)
                >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE;
    }
    
    /**
     * Get device information summary
     */
    public static String getDeviceInfo() {
        try {
            return String.format("Device: %s %s, Android: %s (API %d), Type: %s",
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                getDeviceType());
        } catch (Exception e) {
            Log.e(TAG, "Error getting device info", e);
            return "Unknown device";
        }
    }
    
    /**
     * Get device manufacturer
     */
    public static String getManufacturer() {
        return Build.MANUFACTURER;
    }
    
    /**
     * Get device model
     */
    public static String getModel() {
        return Build.MODEL;
    }
    
    /**
     * Get Android version
     */
    public static String getAndroidVersion() {
        return Build.VERSION.RELEASE;
    }
    
    /**
     * Get SDK version
     */
    public static int getSdkVersion() {
        return Build.VERSION.SDK_INT;
    }
    
    /**
     * Check if device has telephony features
     */
    public static boolean hasTelephony(Context context) {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            return telephonyManager != null && telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        } catch (Exception e) {
            Log.e(TAG, "Error checking telephony", e);
            return false;
        }
    }
    
    /**
     * Hash a string using SHA-256
     */
    private static String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            // Return first 16 characters of hash
            return hexString.toString().substring(0, 16);
        } catch (Exception e) {
            Log.e(TAG, "Error hashing string", e);
            return input.hashCode() + "";
        }
    }
    
    /**
     * Check if device supports biometric authentication
     */
    public static boolean supportsBiometric(Context context) {
        try {
            androidx.biometric.BiometricManager biometricManager = androidx.biometric.BiometricManager.from(context);
            return biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) 
                   == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "Error checking biometric support", e);
            return false;
        }
    }
    
    /**
     * Get device screen density
     */
    public static String getScreenDensity(Context context) {
        try {
            float density = context.getResources().getDisplayMetrics().density;
            if (density >= 4.0) return "xxxhdpi";
            if (density >= 3.0) return "xxhdpi";
            if (density >= 2.0) return "xhdpi";
            if (density >= 1.5) return "hdpi";
            if (density >= 1.0) return "mdpi";
            return "ldpi";
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen density", e);
            return "unknown";
        }
    }
}