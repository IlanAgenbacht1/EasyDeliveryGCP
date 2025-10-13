package com.clone.EasyDelivery;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.clone.EasyDelivery.Utility.ConnectivityAwareSyncManager;
import com.clone.EasyDelivery.Utility.UnifiedTripManager;
import com.clone.EasyDelivery.Utility.OfflineSyncQueue;
import com.clone.EasyDelivery.Security.AuditLogger;
import com.clone.EasyDelivery.Utility.SecurityManager;
import com.clone.EasyDelivery.Utility.LocationHelper;
import com.clone.EasyDelivery.Firebase.FirestoreSyncManager;

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
    
    private ConnectivityAwareSyncManager syncManager;
    private UnifiedTripManager tripManager;
    private OfflineSyncQueue syncQueue;
    private SecurityManager securityManager;
    private FirestoreSyncManager firestoreSyncManager;
    private BroadcastReceiver connectivityReceiver;
    private volatile long lastConnectivityChangeTime = 0;
    private static final long CONNECTIVITY_DEBOUNCE_MS = 3000; // 3 seconds - increased for better debouncing
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.i(TAG, "=== EasyDelivery Application Starting ===");
        
        try {
            // Initialize Firebase first
            initializeFirebase();
            
            // Initialize core security first
            initializeSecurity();
            
            // Initialize sync system components
            initializeSyncSystem();
            
            // Restore state from previous session
            restoreApplicationState();
            
            // Register application lifecycle callbacks
            registerActivityLifecycleCallbacks(new SyncSystemLifecycleHandler());
            
            // Register system broadcast receivers
            registerConnectivityReceiver();
            
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
            
            // Unregister broadcast receivers
            unregisterConnectivityReceiver();
            
            // Gracefully shutdown sync system
            shutdownSyncSystem();
            
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
            
            // Initialize email password if missing
            if (securityManager.getEmailPassword() == null || securityManager.getEmailPassword().trim().isEmpty()) {
                securityManager.configureEmailPassword("jvvu juda uudo gbcj");
            }
            
            // Initialize audit logging
            AuditLogger.getInstance(this);
            
            Log.d(TAG, "✅ Security components initialized");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize security components", e);
            throw new RuntimeException("Security initialization failed", e);
        }
    }
    
    /**
     * Initialize sync system components in proper order
     */
    private void initializeSyncSystem() {
        try {
            Log.d(TAG, "Initializing sync system components...");
            
            // Initialize OfflineSyncQueue first (lowest level)
            syncQueue = OfflineSyncQueue.getInstance(this);
            
            // Initialize ConnectivityAwareSyncManager (mid level)
            syncManager = ConnectivityAwareSyncManager.getInstance(this);
            
            // Initialize UnifiedTripManager (high level)
            tripManager = UnifiedTripManager.getInstance(this);
            
            // Register connectivity listeners
            syncManager.registerConnectivityListener();
            
            Log.d(TAG, "✅ Sync system components initialized");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize sync system", e);
            throw new RuntimeException("Sync system initialization failed", e);
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
            int currentVersion = BuildConfig.VERSION_CODE;
            
            if (savedVersion != currentVersion) {
                Log.i(TAG, "App version changed from " + savedVersion + " to " + currentVersion + " - performing migration");
                performVersionMigration(savedVersion, currentVersion);
                
                // Update saved version
                prefs.edit().putInt(PREF_APP_VERSION, currentVersion).apply();
            }
            
            // Restore sync queue state
            syncQueue.restoreQueueState();
            
            // Restore completed trips list from database to fix trip lifecycle issues
            restoreCompletedTripsFromDatabase();
            
            // Check for orphaned trips on startup
            checkForOrphanedTripsOnStartup();
            
            // Initialize location services
            initializeLocationServices();
            
            // Process any pending operations from previous session
            syncManager.processPendingOperations();
            
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
            
            // Save sync queue state
            if (syncQueue != null) {
                syncQueue.saveQueueState();
            }
            
            // Save current timestamp for state restoration
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit()
                .putLong("last_shutdown_time", System.currentTimeMillis())
                .putInt(PREF_APP_VERSION, BuildConfig.VERSION_CODE)
                .apply();
            
            Log.d(TAG, "✅ Application state saved");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to save application state", e);
        }
    }
    
    /**
     * Gracefully shutdown sync system
     */
    private void shutdownSyncSystem() {
        try {
            Log.d(TAG, "Shutting down sync system...");
            
            // Unregister connectivity listeners
            if (syncManager != null) {
                syncManager.unregisterConnectivityListener();
            }
            
            // Shutdown components in reverse order
            if (tripManager != null) {
                // tripManager doesn't need explicit shutdown currently
            }
            
            if (syncManager != null) {
                syncManager.shutdown();
            }
            
            if (syncQueue != null) {
                syncQueue.close();
            }
            
            Log.d(TAG, "✅ Sync system shutdown complete");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during sync system shutdown", e);
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
     * Handle app version migrations
     */
    private void performVersionMigration(int fromVersion, int toVersion) {
        Log.i(TAG, "Performing migration from version " + fromVersion + " to " + toVersion);
        
        try {
            // Perform version-specific migrations here
            
            // Clean up old queue entries if needed
            if (fromVersion < toVersion && syncQueue != null) {
                syncQueue.cleanupOldOperations();
            }
            
            Log.i(TAG, "✅ Version migration completed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Version migration failed", e);
        }
    }
    
    /**
     * Get global sync manager instance
     */
    public ConnectivityAwareSyncManager getSyncManager() {
        return syncManager;
    }
    
    /**
     * Get global trip manager instance
     */
    public UnifiedTripManager getTripManager() {
        return tripManager;
    }
    
    /**
     * Get global sync queue instance
     */
    public OfflineSyncQueue getSyncQueue() {
        return syncQueue;
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
    
    /**
     * 📍 Initialize location services for the application
     */
    private void initializeLocationServices() {
        Log.i(TAG, "=== Initializing location services ===");
        
        try {
            // Start location fetching in background thread
            Thread locationInitThread = new Thread(() -> {
                try {
                    boolean isConnected = syncManager.updateConnectivityState();
                    Log.d(TAG, "📍 Application location init - Network connected: " + isConnected);
                    
                    // Initialize location helper and start location updates
                    android.os.Handler mainHandler = new android.os.Handler(getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            com.clone.EasyDelivery.Utility.LocationHelper.getLocation(isConnected, EasyDeliveryApplication.this);
                            Log.d(TAG, "✅ Application location services initialized successfully");
                        } catch (Exception locationEx) {
                            Log.e(TAG, "❌ LocationHelper.getLocation failed during app init", locationEx);
                            Log.e(TAG, "🔍 Location init failure details - Connected: " + isConnected);
                        }
                    });
                } catch (Exception connectivityEx) {
                    Log.e(TAG, "❌ Connectivity check failed during location init", connectivityEx);
                    Log.e(TAG, "🔍 Attempting location init without connectivity check as fallback");
                    // Fallback: try location init without connectivity check
                    android.os.Handler mainHandler = new android.os.Handler(getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            com.clone.EasyDelivery.Utility.LocationHelper.getLocation(false, EasyDeliveryApplication.this); // Assume offline as fallback
                            Log.d(TAG, "✅ Fallback location services initialized successfully");
                        } catch (Exception fallbackEx) {
                            Log.e(TAG, "❌ Even fallback location init failed", fallbackEx);
                        }
                    });
                }
            });
            
            locationInitThread.start();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Critical error: Failed to start location initialization thread", e);
            Log.e(TAG, "🔍 Thread creation failure details - MainLooper: " + (getMainLooper() != null));
        }
        
        Log.i(TAG, "=== Location services initialization initiated ===");
    }
    
    /**
     * 📶 Register connectivity change broadcast receiver
     * This handles system connectivity changes and triggers appropriate sync actions
     */
    private void registerConnectivityReceiver() {
        try {
            Log.d(TAG, "Registering connectivity change receiver...");
            
            connectivityReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    
                    if ("android.net.conn.CONNECTIVITY_CHANGE".equals(action)) {
                        handleConnectivityChange();
                    }
                }
            };
            
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(connectivityReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(connectivityReceiver, filter);
            }
            
            Log.d(TAG, "✅ Connectivity receiver registered successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error registering connectivity receiver", e);
        }
    }
    
    /**
     * 📶 Unregister connectivity change broadcast receiver
     */
    private void unregisterConnectivityReceiver() {
        try {
            if (connectivityReceiver != null) {
                unregisterReceiver(connectivityReceiver);
                connectivityReceiver = null;
                Log.d(TAG, "Connectivity receiver unregistered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering connectivity receiver", e);
        }
    }
    
    /**
     * 📶 Handle connectivity changes with debouncing to prevent spam
     */
    private void handleConnectivityChange() {
        long currentTime = System.currentTimeMillis();
        
        // Debounce rapid connectivity changes
        //if (currentTime - lastConnectivityChangeTime < CONNECTIVITY_DEBOUNCE_MS) {
        //    Log.d(TAG, "Connectivity change ignored (debounced - " + (currentTime - lastConnectivityChangeTime) + "ms ago)");
        //    return;
        //}
        
        lastConnectivityChangeTime = currentTime;
        Log.i(TAG, "🔄 Processing connectivity change (debounce window expired)");
        
        Thread connectivityThread = new Thread(() -> {
            try {
                boolean connected = syncManager.updateConnectivityState(true);
                Log.i(TAG, "Network state: " + (connected ? "ONLINE" : "OFFLINE"));
                
                // Update location based on new connectivity state (background)
                Handler mainHandler = new Handler(getMainLooper());
                mainHandler.post(() -> {
                    try {
                        LocationHelper.getLocation(connected, EasyDeliveryApplication.this);
                    } catch (Exception locationEx) {
                        Log.w(TAG, "Location update failed: " + locationEx.getMessage());
                    }
                });
                
                // Notify sync manager and trigger sync if online
                if (syncManager != null) {
                    // Force connectivity check since this was triggered by system broadcast
                    //boolean wasOnline = syncManager.updateConnectivityState(true);
                    
                    if (connected) {
                        Log.i(TAG, "Connection restored - triggering sync");
                        syncManager.onAppResumed();
                    }
                }
                
            } catch (Exception e) {
                Log.w(TAG, "Connectivity change handling failed: " + e.getMessage());
                
                // Fallback location update
                Handler mainHandler = new Handler(getMainLooper());
                mainHandler.post(() -> {
                    try {
                        LocationHelper.getLocation(false, EasyDeliveryApplication.this);
                    } catch (Exception fallbackEx) {
                        Log.w(TAG, "Fallback location update also failed");
                    }
                });
            }
        });
        
        connectivityThread.start();
    }
    
    /**
     * Activity lifecycle handler for sync system state management
     */
    private class SyncSystemLifecycleHandler implements ActivityLifecycleCallbacks {
        
        @Override
        public void onActivityCreated(android.app.Activity activity, android.os.Bundle savedInstanceState) {
            // No action needed
        }
        
        @Override
        public void onActivityStarted(android.app.Activity activity) {
            // No action needed
        }
        
        @Override
        public void onActivityResumed(android.app.Activity activity) {
            // Trigger sync when app becomes active
            if (syncManager != null) {
                syncManager.onAppResumed();
            }
        }
        
        @Override
        public void onActivityPaused(android.app.Activity activity) {
            // Save state when app goes to background
            if (syncManager != null) {
                syncManager.onAppPaused();
            }
        }
        
        @Override
        public void onActivityStopped(android.app.Activity activity) {
            // No action needed
        }
        
        @Override
        public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle outState) {
            // No action needed
        }
        
        @Override
        public void onActivityDestroyed(android.app.Activity activity) {
            // No action needed
        }
    }
}
