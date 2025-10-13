package com.clone.EasyDelivery;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.clone.EasyDelivery.Utility.UnifiedTripManager;
import com.clone.EasyDelivery.Security.AuditLogger;
import com.clone.EasyDelivery.Utility.SecurityManager;
import com.clone.EasyDelivery.Firebase.FirestoreSyncManager;
import com.clone.EasyDelivery.Utility.AppConstant;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;

/**
 * EasyDeliveryApplication - Main Application class
 * 
 * Handles proper initialization and shutdown of the sync system components.
 * This ensures clean startup/shutdown sequences and proper resource management.
 */
public class EasyDeliveryApplication extends Application {
    
    private static final String TAG = "EasyDeliveryApp";
    private static final String PREFS_NAME = "EasyDeliveryApp";
    private static final String PREF_APP_VERSION = "app_version";
    
    private UnifiedTripManager tripManager;
    private SecurityManager securityManager;
    private FirestoreSyncManager firestoreSyncManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.i(TAG, "=== EasyDelivery Application Starting ===");
        
        try {
            // Initialize Firebase first
            initializeFirebase();
            
            // Initialize core security first
            initializeSecurity();
            
            // Initialize Firebase-based trip management
            initializeTripManagement();
            
            // Restore state from previous session
            restoreApplicationState();
            
            Log.i(TAG, "✅ EasyDelivery Application initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize EasyDelivery Application", e);
        }
    }
    
    @Override
    public void onTerminate() {
        Log.i(TAG, "=== EasyDelivery Application Terminating ===");
        
        try {
            // Save application state
            saveApplicationState();
            
            // Cleanup trip management
            cleanupTripManagement();
            
            // Shutdown Firebase services
            shutdownFirebase();
            
            Log.i(TAG, "✅ EasyDelivery Application terminated cleanly");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during application termination", e);
        }
        
        super.onTerminate();
    }
    
    /**
     * Initialize Firebase services
     */
    private void initializeFirebase() {
        try {
            Log.d(TAG, "Initializing Firebase services...");
            
            // Initialize Firebase App
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
                Log.d(TAG, "Firebase App initialized");
            }
            
            // Initialize Firestore with offline persistence
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            firestore.setFirestoreSettings(settings);
            
            // Initialize FirestoreSyncManager (replaces DropboxHelper)
            firestoreSyncManager = FirestoreSyncManager.getInstance(this);
            
            // Set client ID from AppConstant if available
            if (AppConstant.COMPANY != null && !AppConstant.COMPANY.trim().isEmpty()) {
                firestoreSyncManager.setCurrentClientId(AppConstant.COMPANY.toLowerCase());
            }
            
            Log.d(TAG, "✅ Firebase services initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize Firebase services", e);
            throw new RuntimeException("Firebase initialization failed", e);
        }
    }
    
    /**
     * Initialize security components first
     */
    private void initializeSecurity() {
        try {
            Log.d(TAG, "Initializing security components...");
            
            // Initialize SecurityManager with full security suite
            securityManager = SecurityManager.getInstance(this);
            
            // Initialize key lifecycle management
            securityManager.initializeKeyTimestamp();
            
            // Perform key maintenance check
            securityManager.performKeyMaintenance();
            
            // Initialize email credentials if needed
            securityManager.initializeEmailCredentials();
            
            // Initialize audit logging
            AuditLogger.getInstance(this);
            
            Log.d(TAG, "✅ Security components initialized");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize security components", e);
            throw new RuntimeException("Security initialization failed", e);
        }
    }
    
    /**
     * Initialize Firebase-based trip management
     */
    private void initializeTripManagement() {
        try {
            Log.d(TAG, "Initializing Firebase-based trip management...");
            
            // Initialize UnifiedTripManager with Firebase backend
            tripManager = UnifiedTripManager.getInstance(this);
            
            Log.d(TAG, "✅ Firebase-based trip management initialized");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize trip management", e);
            throw new RuntimeException("Trip management initialization failed", e);
        }
    }
    
    /**
     * Restore application state from previous session
     */
    private void restoreApplicationState() {
        try {
            Log.d(TAG, "Restoring application state...");
            
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            
            // Check if this is a fresh install or app update
            int savedVersion = prefs.getInt(PREF_APP_VERSION, 0);
            int currentVersion = 1; // BuildConfig.VERSION_CODE would need import
            
            if (savedVersion != currentVersion) {
                Log.i(TAG, "App version changed from " + savedVersion + " to " + currentVersion);
                // Update saved version
                prefs.edit().putInt(PREF_APP_VERSION, currentVersion).apply();
            }
            
            // Restore completed trips list from database
            restoreCompletedTripsFromDatabase();
            
            // Check for orphaned trips using Firebase
            checkForOrphanedTripsOnStartup();
            
            Log.d(TAG, "✅ Application state restored");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to restore application state", e);
        }
    }
    
    /**
     * Save application state for next session
     */
    private void saveApplicationState() {
        try {
            Log.d(TAG, "Saving application state...");
            
            // Save current timestamp for state restoration
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit()
                .putLong("last_shutdown_time", System.currentTimeMillis())
                .putInt(PREF_APP_VERSION, 1)
                .apply();
            
            Log.d(TAG, "✅ Application state saved");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to save application state", e);
        }
    }
    
    /**
     * Clean up trip management resources
     */
    private void cleanupTripManagement() {
        try {
            Log.d(TAG, "Cleaning up trip management...");
            
            // Cleanup UnifiedTripManager resources
            if (tripManager != null) {
                tripManager.cleanup();
            }
            
            Log.d(TAG, "✅ Trip management cleanup complete");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during trip management cleanup", e);
        }
    }
    
    /**
     * Gracefully shutdown Firebase services
     */
    private void shutdownFirebase() {
        try {
            Log.d(TAG, "Shutting down Firebase services...");
            
            // Shutdown FirestoreSyncManager
            if (firestoreSyncManager != null) {
                firestoreSyncManager.shutdown();
            }
            
            Log.d(TAG, "✅ Firebase services shutdown complete");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during Firebase services shutdown", e);
        }
    }
    
    /**
     * Get global trip manager instance
     */
    public UnifiedTripManager getTripManager() {
        return tripManager;
    }
    
    /**
     * Get global Firestore sync manager instance
     */
    public FirestoreSyncManager getFirestoreSyncManager() {
        return firestoreSyncManager;
    }
    
    /**
     * Restore completed trips list from database
     * This fixes the issue where completed trips get re-downloaded after app restart
     * because the in-memory AppConstant.completedTrips list is lost
     */
    private void restoreCompletedTripsFromDatabase() {
        Log.i(TAG, "=== Restoring completed trips from database ===");
        
        try {
            com.clone.EasyDelivery.Database.DeliveryDb restoreDb = new com.clone.EasyDelivery.Database.DeliveryDb(this);
            restoreDb.open();
            
            // Get all fully completed trips from database
            java.util.List<String> fullyCompletedTrips = restoreDb.getAllFullyCompletedTrips();
            
            Log.i(TAG, "Current AppConstant.completedTrips size: " + com.clone.EasyDelivery.Utility.AppConstant.completedTrips.size() + " - " + com.clone.EasyDelivery.Utility.AppConstant.completedTrips);
            Log.i(TAG, "Fully completed trips from database: " + fullyCompletedTrips.size() + " - " + fullyCompletedTrips);
            
            // Add any missing completed trips to the in-memory list
            int addedCount = 0;
            for (String tripId : fullyCompletedTrips) {
                if (!com.clone.EasyDelivery.Utility.AppConstant.completedTrips.contains(tripId)) {
                    com.clone.EasyDelivery.Utility.AppConstant.completedTrips.add(tripId);
                    addedCount++;
                    Log.i(TAG, "Restored completed trip to memory: " + tripId);
                }
            }
            
            Log.i(TAG, "Restored " + addedCount + " completed trips from database");
            Log.i(TAG, "Final AppConstant.completedTrips size: " + com.clone.EasyDelivery.Utility.AppConstant.completedTrips.size() + " - " + com.clone.EasyDelivery.Utility.AppConstant.completedTrips);
            
            restoreDb.close();
            
        } catch (Exception e) {
            Log.e(TAG, "Error restoring completed trips from database", e);
            e.printStackTrace();
        }
        
        Log.i(TAG, "=== Finished restoring completed trips from database ===");
    }
    
    /**
     * 🚑 CRITICAL: Check for orphaned trips on startup
     * 
     * This handles the scenario where:
     * 1. App was viewing a trip (trip claimed/in_progress by this device)
     * 2. App was forcefully closed/updated during development
     * 3. Trip remains orphaned in claimed/in_progress status
     * 4. Device should either resume or release the trip
     */
    private void checkForOrphanedTripsOnStartup() {
        Log.i(TAG, "=== 🚑 ORPHANED TRIP RECOVERY: Checking for orphaned trips on startup ===");
        
        try {
            Thread orphanCheckThread = new Thread(() -> {
                try {
                    // Wait for FirestoreSyncManager to be initialized
                    if (firestoreSyncManager == null) {
                        Log.w(TAG, "🚑 Cannot check orphaned trips - FirestoreSyncManager not available");
                        return;
                    }
                    
                    String currentDeviceId = firestoreSyncManager.getCurrentDeviceId();
                    if (currentDeviceId == null || currentDeviceId.isEmpty()) {
                        Log.w(TAG, "🚑 Cannot check orphaned trips - device ID unavailable");
                        return;
                    }
                    
                    Log.i(TAG, "🚑 Checking for trips orphaned by device: " + currentDeviceId);
                    
                    // Use Firestore query to find orphaned trips
                    findOrphanedTripsWithFirestore(currentDeviceId);
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error during orphaned trip recovery", e);
                }
            });
            
            orphanCheckThread.start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting orphaned trip check thread", e);
        }
        
        Log.i(TAG, "=== 🚑 Orphaned trip recovery check initiated ===");
    }
    
    /**
     * Find trips claimed/in_progress by this device using Firestore query
     */
    private void findOrphanedTripsWithFirestore(String deviceId) {
        try {
            com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            
            // Query for trips claimed or in_progress by this device
            db.collection("trips")
                .whereEqualTo("clientId", firestoreSyncManager.getCurrentClientId())
                .whereIn("status", java.util.Arrays.asList("claimed", "in_progress"))
                .whereEqualTo("sessionInfo.deviceId", deviceId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    java.util.List<String> orphanedTripIds = new java.util.ArrayList<>();
                    
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String tripId = doc.getId();
                        String status = doc.getString("status");
                        orphanedTripIds.add(tripId);
                        Log.i(TAG, "🚑 Found orphaned trip: " + tripId + " (status: " + status + ") claimed by this device");
                    }
                    
                    if (orphanedTripIds.isEmpty()) {
                        Log.i(TAG, "✅ No orphaned trips found for device: " + deviceId);
                    } else {
                        Log.w(TAG, "🚑 FOUND " + orphanedTripIds.size() + " ORPHANED TRIP(S): " + orphanedTripIds);
                        
                        // Handle each orphaned trip
                        for (String tripId : orphanedTripIds) {
                            handleOrphanedTripWithFirestore(tripId, deviceId);
                        }
                    }
                    
                    Log.i(TAG, "✅ Orphaned trip recovery completed");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error finding orphaned trips with Firestore", e);
                });
            
        } catch (Exception e) {
            Log.e(TAG, "Error in findOrphanedTripsWithFirestore", e);
        }
    }
    
    /**
     * Handle an orphaned trip by releasing it back to available using Firestore
     */
    private void handleOrphanedTripWithFirestore(String tripId, String deviceId) {
        Log.i(TAG, "🚑 HANDLING ORPHANED TRIP: " + tripId + " (device: " + deviceId + ")");
        
        try {
            // Clear any local state that might reference this trip
            if (com.clone.EasyDelivery.Utility.AppConstant.STARTED_TRIP.equals(tripId)) {
                com.clone.EasyDelivery.Utility.AppConstant.STARTED_TRIP = "";
                Log.i(TAG, "🧩 Cleared STARTED_TRIP reference: " + tripId);
            }
            
            if (com.clone.EasyDelivery.Utility.AppConstant.TRIPID != null && com.clone.EasyDelivery.Utility.AppConstant.TRIPID.equals(tripId)) {
                com.clone.EasyDelivery.Utility.AppConstant.TRIPID = "";
                Log.i(TAG, "🧩 Cleared TRIPID reference: " + tripId);
            }
            
            // Use FirestoreSyncManager to properly release the trip
            if (firestoreSyncManager != null) {
                firestoreSyncManager.releaseTrip(tripId, "Orphaned trip recovery on startup")
                    .addOnSuccessListener(success -> {
                        if (success) {
                            Log.i(TAG, "✅ ORPHANED TRIP RELEASED: " + tripId + " moved back to available");
                        } else {
                            Log.w(TAG, "⚠️ Failed to release orphaned trip: " + tripId);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error releasing orphaned trip: " + tripId, e);
                    });
            } else {
                Log.e(TAG, "❌ FirestoreSyncManager not available for orphaned trip cleanup: " + tripId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling orphaned trip: " + tripId, e);
        }
    }
    
    // All legacy connectivity and lifecycle management removed - 
    // Firebase handles these concerns automatically
}
