package com.clone.EasyDelivery.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * SharedPreferencesManager - Centralized preferences management
 * Manual DI approach until Hilt annotation processing works properly
 */
@Singleton
public class SharedPreferencesManager {
    
    private static final String PREFS_NAME = "EasyDeliveryPrefs";
    
    // Preference keys
    private static final String KEY_CURRENT_DOCUMENT = "current_document";
    private static final String KEY_CURRENT_PARCEL_NO = "current_parcel_no";
    private static final String KEY_CURRENT_COMPANY = "current_company";
    private static final String KEY_CURRENT_TRIP_ID = "current_trip_id";
    private static final String KEY_CURRENT_SESSION_ID = "current_session_id";
    private static final String KEY_SIGNATURE_PATH = "signature_path";
    private static final String KEY_PHOTO_PATH = "photo_path";
    private static final String KEY_IMAGE_PATH = "image_path";
    private static final String KEY_PIC_PATH = "pic_path";
    private static final String KEY_SIGN_PATH = "sign_path";
    private static final String KEY_COMMENT = "comment";
    private static final String KEY_GPS_LATITUDE = "gps_latitude";
    private static final String KEY_GPS_LONGITUDE = "gps_longitude";
    private static final String KEY_GPS_HAS_LOCATION = "gps_has_location";
    private static final String KEY_PARCEL_VALIDATION_ENABLED = "parcel_validation_enabled";
    private static final String KEY_PARCEL_INPUT = "parcel_input";
    private static final String KEY_PARCEL_POSITION = "parcel_position";
    private static final String KEY_ZOOM = "zoom";
    
    private final SharedPreferences preferences;

    @Inject
    public SharedPreferencesManager(Context context) {
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    // =================== CURRENT OPERATION CONTEXT ===================
    
    public String getCurrentDocument() {
        return preferences.getString(KEY_CURRENT_DOCUMENT, "");
    }
    
    public void setCurrentDocument(String document) {
        preferences.edit().putString(KEY_CURRENT_DOCUMENT, document).apply();
    }
    
    public String getCurrentParcelNo() {
        return preferences.getString(KEY_CURRENT_PARCEL_NO, "");
    }
    
    public void setCurrentParcelNo(String parcelNo) {
        preferences.edit().putString(KEY_CURRENT_PARCEL_NO, parcelNo).apply();
    }
    
    public String getCurrentCompany() {
        return preferences.getString(KEY_CURRENT_COMPANY, "");
    }
    
    public void setCurrentCompany(String company) {
        preferences.edit().putString(KEY_CURRENT_COMPANY, company).apply();
    }
    
    public String getCurrentTripId() {
        return preferences.getString(KEY_CURRENT_TRIP_ID, "");
    }
    
    public void setCurrentTripId(String tripId) {
        preferences.edit().putString(KEY_CURRENT_TRIP_ID, tripId).apply();
    }
    
    public String getCurrentSessionId() {
        return preferences.getString(KEY_CURRENT_SESSION_ID, "");
    }
    
    public void setCurrentSessionId(String sessionId) {
        preferences.edit().putString(KEY_CURRENT_SESSION_ID, sessionId).apply();
    }
    
    // =================== FILE PATHS ===================
    
    public String getSignaturePath() {
        return preferences.getString(KEY_SIGNATURE_PATH, "");
    }
    
    public void setSignaturePath(String path) {
        preferences.edit().putString(KEY_SIGNATURE_PATH, path).apply();
    }
    
    public String getPhotoPath() {
        return preferences.getString(KEY_PHOTO_PATH, "");
    }
    
    public void setPhotoPath(String path) {
        preferences.edit().putString(KEY_PHOTO_PATH, path).apply();
    }
    
    public String getImagePath() {
        return preferences.getString(KEY_IMAGE_PATH, "");
    }
    
    public void setImagePath(String path) {
        preferences.edit().putString(KEY_IMAGE_PATH, path).apply();
    }
    
    public String getPicPath() {
        return preferences.getString(KEY_PIC_PATH, "");
    }
    
    public void setPicPath(String path) {
        preferences.edit().putString(KEY_PIC_PATH, path).apply();
    }
    
    public String getSignPath() {
        return preferences.getString(KEY_SIGN_PATH, "");
    }
    
    public void setSignPath(String path) {
        preferences.edit().putString(KEY_SIGN_PATH, path).apply();
    }
    
    public String getComment() {
        return preferences.getString(KEY_COMMENT, "");
    }
    
    public void setComment(String comment) {
        preferences.edit().putString(KEY_COMMENT, comment).apply();
    }
    
    // =================== GPS LOCATION ===================
    
    public Location getGpsLocation() {
        if (!preferences.getBoolean(KEY_GPS_HAS_LOCATION, false)) {
            return null;
        }
        
        Location location = new Location("saved");
        location.setLatitude(preferences.getFloat(KEY_GPS_LATITUDE, 0f));
        location.setLongitude(preferences.getFloat(KEY_GPS_LONGITUDE, 0f));
        return location;
    }
    
    public void setGpsLocation(Location location) {
        SharedPreferences.Editor editor = preferences.edit();
        if (location != null) {
            editor.putFloat(KEY_GPS_LATITUDE, (float) location.getLatitude());
            editor.putFloat(KEY_GPS_LONGITUDE, (float) location.getLongitude());
            editor.putBoolean(KEY_GPS_HAS_LOCATION, true);
        } else {
            editor.putBoolean(KEY_GPS_HAS_LOCATION, false);
        }
        editor.apply();
    }
    
    public String getFormattedGpsLocation() {
        Location location = getGpsLocation();
        if (location == null) {
            return "[0° N, 0° E]";
        }
        
        return "[" + location.getLatitude() + "° N, " + 
               location.getLongitude() + "° E]";
    }
    
    // =================== VALIDATION STATE ===================
    
    public boolean isParcelValidationEnabled() {
        return preferences.getBoolean(KEY_PARCEL_VALIDATION_ENABLED, false);
    }
    
    public void setParcelValidationEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_PARCEL_VALIDATION_ENABLED, enabled).apply();
    }
    
    public String getParcelInput() {
        return preferences.getString(KEY_PARCEL_INPUT, "");
    }
    
    public void setParcelInput(String input) {
        preferences.edit().putString(KEY_PARCEL_INPUT, input).apply();
    }
    
    public int getParcelPosition() {
        return preferences.getInt(KEY_PARCEL_POSITION, 0);
    }
    
    public void setParcelPosition(int position) {
        preferences.edit().putInt(KEY_PARCEL_POSITION, position).apply();
    }
    
    public String getZoom() {
        return preferences.getString(KEY_ZOOM, "");
    }
    
    public void setZoom(String zoom) {
        preferences.edit().putString(KEY_ZOOM, zoom).apply();
    }
    
    // =================== HELPER METHODS ===================
    
    /**
     * Clear current operation context
     */
    public void clearCurrentContext() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_CURRENT_DOCUMENT, "");
        editor.putString(KEY_CURRENT_PARCEL_NO, "");
        editor.putString(KEY_CURRENT_COMPANY, "");
        editor.putString(KEY_SIGNATURE_PATH, "");
        editor.putString(KEY_PHOTO_PATH, "");
        editor.putString(KEY_COMMENT, "");
        editor.putBoolean(KEY_GPS_HAS_LOCATION, false);
        editor.putString(KEY_PARCEL_INPUT, "");
        editor.putInt(KEY_PARCEL_POSITION, 0);
        editor.apply();
    }
    
    /**
     * Set current delivery context
     */
    public void setCurrentDelivery(String documentId, String tripId, String sessionId) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_CURRENT_DOCUMENT, documentId != null ? documentId : "");
        editor.putString(KEY_CURRENT_TRIP_ID, tripId != null ? tripId : "");
        editor.putString(KEY_CURRENT_SESSION_ID, sessionId != null ? sessionId : "");
        editor.apply();
    }
    
    /**
     * Check if we have a valid current context
     */
    public boolean hasValidCurrentContext() {
        return !getCurrentDocument().isEmpty() && 
               !getCurrentTripId().isEmpty() && 
               !getCurrentSessionId().isEmpty();
    }
    
    /**
     * Get signature directory path
     */
    public String getSignatureDirectory() {
        return "/Signatures"; // Default signature directory path
    }

    /**
     * Clear all preferences (for logout)
     */
    public void clearAll() {
        preferences.edit().clear().apply();
    }
}