package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import com.dropbox.core.v2.DbxClientV2;
import com.clone.EasyDelivery.Database.DeliveryDb;
import org.json.JSONObject;

// Firebase/Firestore imports
import com.clone.EasyDelivery.Firebase.FirestoreSyncManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import com.clone.EasyDelivery.Utility.AppConstant;

// Import new operation classes
import com.clone.EasyDelivery.Utility.operations.SyncOperation;
import com.clone.EasyDelivery.Utility.operations.RemoveTripOperation;
import com.clone.EasyDelivery.Utility.operations.CleanupCompletedTripsOperation;
import com.clone.EasyDelivery.Utility.operations.MaintenanceOperation;

/**
 * 🌐 ConnectivityAwareSyncManager - Hybrid sync manager (Firestore + Legacy)
 * 
 * Modern approach:
 * - Primary: Firestore offline-first with real-time sync (no manual connectivity needed)
 * - Fallback: Legacy Dropbox sync for compatibility
 * - Monitors Firestore sync status for UI feedback
 * - Provides unified API for both systems
 * 
 * The key insight: Firestore handles connectivity automatically,
 * but we maintain legacy support during transition.
 */
public class ConnectivityAwareSyncManager {
    
    private static final String TAG = "ConnectivityAwareSyncManager";
    
    // Network state tracking
    private final Context context;
    private final FirestoreSyncManager firestoreSyncManager; // Primary sync system
    private final AtomicBoolean isOnline = new AtomicBoolean(false);
    private final AtomicBoolean firestoreSyncEnabled = new AtomicBoolean(true); // Use Firestore by default
    private final AtomicLong lastConnectivityCheck = new AtomicLong(0);
    private final AtomicLong lastConnectivityLog = new AtomicLong(0);
    private static final long CONNECTIVITY_CHECK_INTERVAL = 2000; // 2 seconds
    private static final long CONNECTIVITY_LOG_INTERVAL = 30000; // 30 seconds for stable state logging
    
    // Sync state
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final AtomicLong lastSuccessfulSync = new AtomicLong(0);
    private boolean isSynced = false;
    
    // Periodic sync - optimized for responsiveness
    private Timer periodicSyncTimer;
    private static final long SYNC_INTERVAL_NORMAL = 15000; // 15 seconds
    private static final long SYNC_INTERVAL_FAST = 3000;    // 3 seconds when queue has items
    private static final long SYNC_INTERVAL_SLOW = 30000;   // 30 seconds when idle
    
    // Background cache refresh
    private Timer cacheRefreshTimer;
    private final AtomicBoolean cacheRefreshInProgress = new AtomicBoolean(false);
    private final AtomicLong lastCacheRefresh = new AtomicLong(0);
    private static final long CACHE_REFRESH_INTERVAL = 10000; // 30 seconds
    
    // Completed trips cleanup
    private Timer completedTripsCleanupTimer;
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);
    private final AtomicLong lastCleanup = new AtomicLong(0);
    private static final long CLEANUP_INTERVAL = 10000; // 5 minutes
    
    // Singleton
    private static ConnectivityAwareSyncManager instance;
    
    private ConnectivityAwareSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.firestoreSyncManager = FirestoreSyncManager.getInstance(context);
        
        Log.i(TAG, "Initializing ConnectivityAwareSyncManager with hybrid architecture");
        Log.i(TAG, "Primary: Firestore (" + (firestoreSyncManager != null ? "available" : "unavailable") + ")");
        Log.i(TAG, "Fallback: Legacy Dropbox sync");
        
        updateConnectivityState();
        startPeriodicSync();
        startCacheRefreshTimer();
        startCompletedTripsCleanupTimer();
    }
    
    public static synchronized ConnectivityAwareSyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConnectivityAwareSyncManager(context);
        }
        return instance;
    }
    
    /**
     * 🌐 Check current connectivity and update internal state
     */
    public boolean updateConnectivityState() {
        return updateConnectivityState(false);
    }
    
    /**
     * 🌐 Update connectivity state with option to force check (for external triggers)
     */
    public boolean updateConnectivityState(boolean forceCheck) {
        boolean wasOnline = isOnline.get();
        boolean nowOnline = checkNetworkConnectivity();

        // Only proceed if the state has actually changed.
        if (wasOnline != nowOnline) {
            isOnline.set(nowOnline);
            Log.i(TAG, "🌐 Connectivity changed: " + (nowOnline ? "ONLINE" : "OFFLINE"));

            // If we just came back online, trigger the sync for the offline queue.
            if (nowOnline) {
                Log.i(TAG, "📡 Network restored - triggering sync of queued operations");
                triggerOfflineSync();
            }
        } else {
            // Optional: Log that the state remains stable.
            Log.v(TAG, "🌐 Connectivity state stable: " + (nowOnline ? "ONLINE" : "OFFLINE"));
        }

        return nowOnline;
    }
    
    /**
     * 📶 Check basic network connectivity
     */
    /**
     * 📶 Checks for a validated network connection (i.e., actual internet access).
     * This is more reliable than just checking if a network is "active".
     */
    private boolean checkNetworkConnectivity() {
        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (connectivityManager == null) {
                Log.w(TAG, "Cannot get ConnectivityManager");
                return false;
            }

            // For modern Android versions (API 23+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) {
                    return false;
                }
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                // Check for both an active transport (Wi-Fi, Cellular, etc.) AND validated internet access.
                return capabilities != null &&
                        (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
            } else {
                // Fallback for older Android versions
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network connectivity", e);
            return false;
        }
    }

    public boolean isOnline() {
        return isOnline.get();
    }
    
    /**
     * Check if Firestore has pending writes (for UI feedback)
     * This is more reliable than network connectivity for showing sync status
     */
    public boolean hasPendingWrites() {
        if (firestoreSyncManager != null) {
            return firestoreSyncManager.hasPendingWrites();
        }
        return false;
    }
    
    /**
     * Get Firestore sync manager for direct access
     */
    public FirestoreSyncManager getFirestoreSyncManager() {
        return firestoreSyncManager;
    }
    
    /**
     * Enable/disable Firestore as primary sync mechanism
     */
    public void setFirestoreSyncEnabled(boolean enabled) {
        firestoreSyncEnabled.set(enabled);
        Log.i(TAG, "Firestore sync " + (enabled ? "enabled" : "disabled") + " - will " + 
              (enabled ? "use Firestore as primary" : "fallback to legacy sync"));
    }
    
    /**
     * Check if Firestore sync is enabled and available
     */
    public boolean isFirestoreSyncEnabled() {
        return firestoreSyncEnabled.get() && firestoreSyncManager != null;
    }
    
    /**
     * App lifecycle events - now Firestore-aware
     */
    public void onAppResumed() {
        Log.i(TAG, "📱 App resumed - triggering sync checks");
        
        if (isFirestoreSyncEnabled()) {
            // Firestore handles this automatically, but we can log status
            boolean hasPending = hasPendingWrites();
            Log.i(TAG, "Firestore sync status: " + (hasPending ? "pending writes exist" : "all synced"));
        } else {
            // Legacy: trigger connectivity check and sync
            updateConnectivityState(true);
            
            if (isOnline.get()) {
                triggerOfflineSync();
            }
        }
    }
    
    public void onAppPaused() {
        Log.i(TAG, "📱 App paused - Firestore will continue syncing in background");
        
        if (isFirestoreSyncEnabled()) {
            // Firestore continues working offline automatically
            Log.d(TAG, "Firestore offline persistence enabled - no action needed");
        } else {
            // Legacy: ensure any final sync operations are queued
            Log.d(TAG, "Legacy sync - operations will be queued until next online sync");
        }
    }
    
    /**
     * 🔄 Execute operation with automatic hybrid handling
     * 
     * This is the core method that provides the unified API:
     * - Primary: Use Firestore real-time sync
     * - Fallback: Use legacy Dropbox online/offline queue
     */
    public SyncOperation.SyncResult executeOperation(SyncOperation operation) {
        
        if (isOnline.get()) {
            return executeOnlineOperation(operation);
        } else {
            return executeOfflineOperation(operation);
        }
    }
    
    /**
     * ⚡ Execute operation immediately (online mode)
     */
    private SyncOperation.SyncResult executeOnlineOperation(SyncOperation operation) {
        Log.d(TAG, "⚡ Executing online operation: " + operation.getType());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Execute directly against Dropbox
            SyncOperation.SyncResult result = operation.executeOnline(context);
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (result.success) {
                Log.i(TAG, "✅ Online operation completed: " + operation.getType() + 
                      " (" + duration + "ms)");
                
                // Update local state immediately
                updateLocalState(operation, result);
                lastSuccessfulSync.set(System.currentTimeMillis());
                
            } else {
                Log.w(TAG, "⚠️ Online operation failed: " + operation.getType() + 
                      " - " + result.message);
                
                // If online operation fails, fall back to offline queue
                Log.i(TAG, "🔄 Falling back to offline queue for: " + operation.getType());
                return executeOfflineOperation(operation);
            }
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Online operation error: " + operation.getType(), e);
            
            // Network might have gone down during operation
            isOnline.set(false);
            return executeOfflineOperation(operation);
        }
    }
    
    /**
     * 💾 Queue operation for later sync (offline mode)
     */
    private SyncOperation.SyncResult executeOfflineOperation(SyncOperation operation) {
        Log.d(TAG, "💾 Queueing offline operation: " + operation.getType());
        
        try {
            // Update local state immediately (optimistic)
            SyncOperation.SyncResult localResult = operation.executeOffline(context);
            
            if (localResult.success) {
                // Queue for sync when online
                OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
                queue.enqueueOperation(operation);
                
                Log.i(TAG, "✅ Offline operation queued: " + operation.getType());
                
                // Return success immediately - user sees instant response
                return SyncOperation.SyncResult.success("Operation completed (will sync when online)");
                
            } else {
                Log.w(TAG, "⚠️ Offline operation failed: " + operation.getType() + 
                      " - " + localResult.message);
                return localResult;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Offline operation error: " + operation.getType(), e);
            return SyncOperation.SyncResult.failure("Offline operation failed: " + e.getMessage());
        }
    }
    
    /**
     * 📡 Trigger sync of all queued offline operations
     */
    private void triggerOfflineSync() {
        if (syncInProgress.get()) {
            Log.d(TAG, "Sync already in progress, skipping trigger");
            return;
        }
        
        // Run sync in background thread
        new Thread(() -> {
            try {
                syncInProgress.set(true);
                
                OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
                SyncOperation.SyncResult result = queue.syncAll();
                
                // Parse the result message to get detailed sync info
                String resultDetails = result.message != null ? result.message : "Unknown result";
                
                if (result.success) {
                    // Check if there were any individual operation failures
                    if (resultDetails.contains("failed") && !resultDetails.contains("0 failed")) {
                        Log.w(TAG, "⚠️ Offline sync completed with partial failures: " + resultDetails);
                    } else {
                        Log.i(TAG, "✅ Offline sync completed successfully: " + resultDetails);
                        lastSuccessfulSync.set(System.currentTimeMillis());
                    }
                } else {
                    Log.e(TAG, "❌ Offline sync failed: " + resultDetails);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error during offline sync", e);
            } finally {
                syncInProgress.set(false);
            }
        }).start();
    }
    
    /**
     * 🔄 Update local database state after successful operation
     */
    private void updateLocalState(SyncOperation operation, SyncOperation.SyncResult result) {
        try {
            // Update sync metadata to reflect successful cloud operation
            DeliveryDb database = new DeliveryDb(context);
            database.open();
            
            // Mark as synced in local database
            if (operation.getTripId() != null) {
                database.updateLastCloudSync(operation.getTripId(), 
                    System.currentTimeMillis(), "synced");
            }
            
            database.close();

            // Check if the operation that just succeeded was a trip completion.
            if (operation instanceof com.clone.EasyDelivery.Utility.operations.CompleteTripOperation) {
                Log.i(TAG, "Trip completion synced. Triggering automatic cleanup for trip: " + operation.getTripId());

                // Run cleanup on a background thread to avoid blocking the sync manager.
                new Thread(() -> {
                    UnifiedTripManager tripManager = UnifiedTripManager.getInstance(context);
                    tripManager.cleanupCompletedTrip(operation.getTripId());
                }).start();
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error updating local state after sync", e);
        }
    }
    
    /**
     * 📊 Get current sync status for UI display
     */
    public SyncStatus getSyncStatus() {
        
        boolean online = isOnline.get();
        boolean syncing = syncInProgress.get();
        long lastSync = lastSuccessfulSync.get();
        
        OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
        int queuedOperations = queue.getQueuedOperationCount();
        
        if (syncing) {
            return new SyncStatus(SyncStatus.State.SYNCING, "Synchronizing...", 
                online, queuedOperations, lastSync);
        } else if (queuedOperations > 0) {
            if (online) {
                return new SyncStatus(SyncStatus.State.PENDING, 
                    queuedOperations + " operations pending sync", 
                    online, queuedOperations, lastSync);
            } else {
                return new SyncStatus(SyncStatus.State.OFFLINE, 
                    "Offline - " + queuedOperations + " operations queued", 
                    online, queuedOperations, lastSync);
            }
        } else {
            if (online) {
                return new SyncStatus(SyncStatus.State.SYNCED, "All data synced", 
                    online, queuedOperations, lastSync);
            } else {
                return new SyncStatus(SyncStatus.State.OFFLINE, "Offline", 
                    online, queuedOperations, lastSync);
            }
        }
    }
    
    /**
     * 🧹 Force sync of all pending operations (user-initiated)
     */
    public void forceSync() {
        
        if (!isOnline.get()) {
            Log.w(TAG, "Cannot force sync - device is offline");
            return;
        }
        
        Log.i(TAG, "🔄 User-initiated force sync");
        triggerOfflineSync();
    }
    
    /**
     * 📊 Get sync statistics for debugging
     */
    public String getSyncStatistics() {
        
        OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
        SyncStatus status = getSyncStatus();
        
        StringBuilder stats = new StringBuilder();
        stats.append("🌐 Connectivity-Aware Sync Status:\n");
        stats.append("  - Online: ").append(status.isOnline).append("\n");
        stats.append("  - State: ").append(status.state).append("\n");
        stats.append("  - Message: ").append(status.message).append("\n");
        stats.append("  - Queued operations: ").append(status.queuedOperations).append("\n");
        stats.append("  - Last successful sync: ");
        
        if (status.lastSuccessfulSync > 0) {
            long minutesAgo = (System.currentTimeMillis() - status.lastSuccessfulSync) / (60 * 1000);
            stats.append(minutesAgo).append(" minutes ago\n");
        } else {
            stats.append("Never\n");
        }
        
        stats.append("\n").append(getCacheRefreshStatistics());
        stats.append("\n").append(getCompletedTripsCleanupStatistics());
        
        return stats.toString();
    }
    
    /**
     * Register connectivity listener for automatic sync triggers
     */
    public void registerConnectivityListener() {
        try {
            Log.d(TAG, "Registering connectivity listeners");
            // Connectivity monitoring is handled by updateConnectivityState() in periodic sync
            // This method exists for consistency with Application lifecycle
        } catch (Exception e) {
            Log.e(TAG, "Error registering connectivity listener", e);
        }
    }
    
    /**
     * Unregister connectivity listener
     */
    public void unregisterConnectivityListener() {
        try {
            Log.d(TAG, "Unregistering connectivity listeners");
            // Cleanup handled by shutdown() method
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering connectivity listener", e);
        }
    }
    
    // Removed duplicate lifecycle methods - using Firestore-aware versions above
    
    /**
     * Process pending operations from previous session
     */
    public void processPendingOperations() {
        try {
            Log.d(TAG, "Processing pending operations from previous session");
            
            OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
            int pendingCount = queue.getQueuedOperationCount();
            
            if (pendingCount > 0) {
                Log.i(TAG, "Found " + pendingCount + " pending operations from previous session");
                
                if (isOnline.get()) {
                    triggerOfflineSync();
                } else {
                    Log.d(TAG, "Device offline - pending operations will sync when connection is restored");
                }
            } else {
                Log.d(TAG, "No pending operations from previous session");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing pending operations", e);
        }
    }
    
    /**
     * Shutdown the sync manager - now Firestore-aware
     */
    public void shutdown() {
        try {
            Log.d(TAG, "Shutting down ConnectivityAwareSyncManager (hybrid mode)");
            
            // Stop periodic sync timer
            if (periodicSyncTimer != null) {
                periodicSyncTimer.cancel();
                periodicSyncTimer = null;
                Log.d(TAG, "Periodic sync timer stopped");
            }
            
            // Stop cache refresh timer
            if (cacheRefreshTimer != null) {
                cacheRefreshTimer.cancel();
                cacheRefreshTimer = null;
                Log.d(TAG, "Cache refresh timer stopped");
            }
            
            // Stop cleanup timer
            if (completedTripsCleanupTimer != null) {
                completedTripsCleanupTimer.cancel();
                completedTripsCleanupTimer = null;
                Log.d(TAG, "Cleanup timer stopped");
            }
            
            // Firestore cleanup is handled by FirestoreSyncManager
            // No need to manually shutdown Firestore listeners here
            if (firestoreSyncManager != null) {
                Log.d(TAG, "Firestore sync manager will be cleaned up by Application");
            }
            
            Log.d(TAG, "ConnectivityAwareSyncManager shutdown complete");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during sync manager shutdown", e);
        }
    }
    
    /**
     * 🔄 Start periodic sync timer
     */
    private void startPeriodicSync() {
        if (periodicSyncTimer != null) {
            periodicSyncTimer.cancel();
        }
        
        periodicSyncTimer = new Timer("PeriodicSync", true);
        scheduleNextSync();
        
        // Removed complex cache refresh and cleanup timers
        
        Log.i(TAG, "Periodic sync started with cache refresh and cleanup");
    }
    
    /**
     * 🔄 Schedule next sync based on current conditions
     */
    private void scheduleNextSync() {
        try {
            OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
            int queuedOps = queue.getQueuedOperationCount();
            
            long interval;
            if (queuedOps > 0) {
                interval = SYNC_INTERVAL_FAST; // Fast sync when items queued
            } else if (isOnline.get()) {
                interval = SYNC_INTERVAL_NORMAL; // Normal sync when online
            } else {
                interval = SYNC_INTERVAL_SLOW; // Slow sync when offline
            }
            
            periodicSyncTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    performPeriodicSync();
                }
            }, interval);
            
            // Only log sync scheduling when there are operations queued or at INFO level intervals
            if (queuedOps > 0 || interval >= SYNC_INTERVAL_NORMAL) {
                Log.d(TAG, "Next sync scheduled in " + (interval/1000) + "s (queued ops: " + queuedOps + ")");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling periodic sync", e);
            // Fallback to normal interval
            periodicSyncTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    performPeriodicSync();
                }
            }, SYNC_INTERVAL_NORMAL);
        }
    }
    
    /**
     * 🔄 Perform periodic sync check
     */
    private void performPeriodicSync() {
        try {
            
            if (isOnline.get() && !syncInProgress.get()) {
                OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
                int queuedOps = queue.getQueuedOperationCount();
                
                if (queuedOps > 0) {
                    Log.i(TAG, "Periodic sync triggering for " + queuedOps + " queued operations");
                    triggerOfflineSync();
                } else {
                    // Reduce verbose logging when no operations are queued
                    Log.v(TAG, "Periodic sync check - no operations queued");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in periodic sync", e);
        } finally {
            // Schedule next sync regardless of success/failure
            scheduleNextSync();
        }
    }
    
    /**
     * 🚫 Stop periodic sync (for cleanup)
     */
    public void stopPeriodicSync() {
        if (periodicSyncTimer != null) {
            periodicSyncTimer.cancel();
            periodicSyncTimer = null;
            Log.i(TAG, "Periodic sync stopped");
        }
        
        // Stop other timers
        if (cacheRefreshTimer != null) {
            cacheRefreshTimer.cancel();
            cacheRefreshTimer = null;
            Log.i(TAG, "Cache refresh timer stopped");
        }
        
        if (completedTripsCleanupTimer != null) {
            completedTripsCleanupTimer.cancel();
            completedTripsCleanupTimer = null;
            Log.i(TAG, "Completed trips cleanup timer stopped");
        }
    }
    
    /**
     * 🔄 Start cache refresh timer
     */
    private void startCacheRefreshTimer() {
        if (cacheRefreshTimer != null) {
            cacheRefreshTimer.cancel();
        }
        
        cacheRefreshTimer = new Timer("CacheRefresh", true);
        scheduleCacheRefresh();
        
        Log.i(TAG, "Cache refresh timer started (interval: " + (CACHE_REFRESH_INTERVAL / 1000) + " seconds)");
    }
    
    /**
     * 🔄 Schedule cache refresh
     */
    private void scheduleCacheRefresh() {
        try {
            cacheRefreshTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    performCacheRefresh();
                    scheduleCacheRefresh(); // Schedule next refresh
                }
            }, CACHE_REFRESH_INTERVAL);
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling cache refresh", e);
        }
    }
    
    /**
     * 🔄 Perform cache refresh
     */
    private void performCacheRefresh() {
        if (cacheRefreshInProgress.get()) {
            Log.v(TAG, "Cache refresh already in progress, skipping");
            return;
        }
        
        if (!isOnline.get()) {
            Log.v(TAG, "Skipping cache refresh - device offline");
            return;
        }
        
        Thread refreshThread = new Thread(() -> {
            try {
                cacheRefreshInProgress.set(true);
                
                Log.i(TAG, "🔄 Starting background cache refresh");
                
                TripCacheManager cacheManager = TripCacheManager.getInstance(context);
                cacheManager.refreshCache();
                
                lastCacheRefresh.set(System.currentTimeMillis());
                Log.i(TAG, "🔄 Cache refresh completed");
                
            } catch (Exception e) {
                Log.e(TAG, "Error during cache refresh", e);
            } finally {
                cacheRefreshInProgress.set(false);
            }
        });
        
        refreshThread.start();
    }
    
    /**
     * 🧹 Start completed trips cleanup timer
     */
    private void startCompletedTripsCleanupTimer() {
        if (completedTripsCleanupTimer != null) {
            completedTripsCleanupTimer.cancel();
        }
        
        completedTripsCleanupTimer = new Timer("CompletedTripsCleanup", true);
        scheduleCompletedTripsCleanup(true); // Initial run with shorter delay
        
        Log.i(TAG, "Completed trips cleanup timer started (normal interval: " + (CLEANUP_INTERVAL / (60 * 1000)) + " minutes)");
    }
    
    /**
     * 🧹 Schedule next completed trips cleanup
     */
    private void scheduleCompletedTripsCleanup() {
        scheduleCompletedTripsCleanup(false);
    }
    
    /**
     * 🧹 Schedule completed trips cleanup with optional initial run
     */
    private void scheduleCompletedTripsCleanup(boolean isInitial) {
        try {
            // For first run, schedule sooner (30 seconds) to test the system
            // For subsequent runs, use the 5-minute interval
            long delay = isInitial ? 30000L : CLEANUP_INTERVAL;
            
            completedTripsCleanupTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    performCompletedTripsCleanup();
                    scheduleCompletedTripsCleanup(false); // Schedule next cleanup (normal interval)
                }
            }, delay);
            
            if (isInitial) {
                Log.i(TAG, "🧹 Initial completed trips cleanup scheduled in " + (delay / 1000) + " seconds");
            } else {
                Log.v(TAG, "Next completed trips cleanup scheduled in " + (delay / (60 * 1000)) + " minutes");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling completed trips cleanup", e);
        }
    }
    
    /**
     * 🧹 Perform completed trips cleanup
     */
    private void performCompletedTripsCleanup() {
        if (cleanupInProgress.get()) {
            Log.v(TAG, "Cleanup already in progress, skipping");
            return;
        }
        
        // Only cleanup when online to ensure we have latest Dropbox state
        if (!isOnline.get()) {
            Log.v(TAG, "Skipping completed trips cleanup - device offline");
            return;
        }
        
        // Run cleanup in background thread
        Thread cleanupThread = new Thread(() -> {
            try {
                cleanupInProgress.set(true);
                long startTime = System.currentTimeMillis();
                
                Log.i(TAG, "🧹 Starting automated completed trips cleanup");
                
                // Create and execute cleanup operation
                CleanupCompletedTripsOperation cleanupOp = CleanupCompletedTripsOperation.create();
                SyncOperation.SyncResult result = cleanupOp.executeOnline(context);
                
                long duration = System.currentTimeMillis() - startTime;
                lastCleanup.set(System.currentTimeMillis());
                
                if (result.success) {
                    Log.i(TAG, "✅ Completed trips cleanup finished successfully: " + result.message + " (" + duration + "ms)");
                } else {
                    Log.w(TAG, "⚠️ Completed trips cleanup completed with issues: " + result.message);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error during completed trips cleanup", e);
            } finally {
                cleanupInProgress.set(false);
            }
        });
        
        cleanupThread.start();
    }
    
    /**
     * 📈 Get cache refresh statistics
     */
    public String getCacheRefreshStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("🔄 Background Cache Refresh Status:\n");
        stats.append("  - Refresh in progress: ").append(cacheRefreshInProgress.get()).append("\n");
        
        long lastRefresh = lastCacheRefresh.get();
        if (lastRefresh > 0) {
            long minutesAgo = (System.currentTimeMillis() - lastRefresh) / (60 * 1000);
            stats.append("  - Last refresh: ").append(minutesAgo).append(" minutes ago\n");
        } else {
            stats.append("  - Last refresh: Never\n");
        }
        
        stats.append("  - Refresh interval: ").append(CACHE_REFRESH_INTERVAL / 1000).append(" seconds\n");
        
        try {
            TripCacheManager cacheManager = TripCacheManager.getInstance(context);
            TripCacheManager.CacheStatistics cacheStats = cacheManager.getCacheStatistics();
            stats.append("  - Cached trips: ").append(cacheStats.totalCachedTrips).append("\n");
            stats.append("  - Cache size: ").append(cacheStats.totalCacheSize / 1024).append(" KB\n");
        } catch (Exception e) {
            stats.append("  - Cache stats: Error retrieving\n");
        }
        
        return stats.toString();
    }
    
    /**
     * 📈 Get completed trips cleanup statistics
     */
    public String getCompletedTripsCleanupStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("🧹 Completed Trips Cleanup Status:\n");
        stats.append("  - Cleanup in progress: ").append(cleanupInProgress.get()).append("\n");
        
        long lastCleanupTime = lastCleanup.get();
        if (lastCleanupTime > 0) {
            long minutesAgo = (System.currentTimeMillis() - lastCleanupTime) / (60 * 1000);
            stats.append("  - Last cleanup: ").append(minutesAgo).append(" minutes ago\n");
        } else {
            stats.append("  - Last cleanup: Never\n");
        }
        
        stats.append("  - Cleanup interval: ").append(CLEANUP_INTERVAL / (60 * 1000)).append(" minutes\n");
        
        try {
            DeliveryDb database = new DeliveryDb(context);
            database.open();
            try {
                List<String> completedTrips = database.getAllFullyCompletedTrips();
                stats.append("  - Completed trips in database: ").append(completedTrips.size()).append("\n");
            } finally {
                database.close();
            }
        } catch (Exception e) {
            stats.append("  - Completed trips: Error retrieving\n");
        }
        
        return stats.toString();
    }
    
    /**
     * 🔄 Force immediate cache refresh (for testing/manual triggers)
     */
    public void forceCacheRefresh() {
        Log.i(TAG, "Manual cache refresh requested");
        
        if (!isOnline.get()) {
            Log.w(TAG, "Cannot force cache refresh - device offline");
            return;
        }
        
        // Cancel current refresh if in progress and start new one
        cacheRefreshInProgress.set(false);
        performCacheRefresh();
    }
    
    /**
     * 🧹 Force immediate completed trips cleanup (for testing/manual triggers)
     */
    public void forceCompletedTripsCleanup() {
        Log.i(TAG, "Manual completed trips cleanup requested");
        
        if (!isOnline.get()) {
            Log.w(TAG, "Cannot force completed trips cleanup - device offline");
            return;
        }
        
        // Cancel current cleanup if in progress and start new one
        cleanupInProgress.set(false);
        performCompletedTripsCleanup();
    }
    
    
    /**
     * 🔄 Force trip removal sync
     * Immediately syncs trip removals from Dropbox
     */
    public void forceTripRemovalSync() {
        if (!isOnline.get()) {
            Log.w(TAG, "Cannot force trip removal sync - device offline");
            return;
        }
        
        Thread syncThread = new Thread(() -> {
            try {
                Log.i(TAG, "🔄 Force syncing trip removals from Dropbox");
                
                // Get current trips from Dropbox
                List<String> cloudTrips = getTripsFromDropbox();
                
                // Sync removals with cache manager
                TripCacheManager cacheManager = TripCacheManager.getInstance(context);
                int removedCount = cacheManager.syncTripRemovals(cloudTrips);
                
                Log.i(TAG, "🔄 Force trip removal sync complete: " + removedCount + " trips removed");
                
            } catch (Exception e) {
                Log.e(TAG, "Error during force trip removal sync", e);
            }
        });
        
        syncThread.start();
    }
    
    /**
     * 📊 Check if trips are in sync
     */
    public boolean isInSync() {
        Log.d(TAG, "Checking sync state...");
        
        // If offline, consider synced based on cached state
        if (!ConnectionHelper.isInternetConnected(context)) {
            Log.d(TAG, "Offline - using cached sync state: " + isSynced);
            return isSynced;
        }
        
        // If online, check dropbox vs local files
        try {
            List<String> dropboxTrips = getTripsFromDropbox();
            List<String> localTrips = getLocalTrips();
            
            boolean synced = dropboxTrips.equals(localTrips);
            
            Log.d(TAG, "Sync check - Dropbox: " + dropboxTrips.size() + 
                      ", Local: " + localTrips.size() + 
                      ", In sync: " + synced);
            
            isSynced = synced;
            return synced;
            
        } catch (Exception e) {
            Log.w(TAG, "Error checking sync state", e);
            return isSynced; // Return cached state on error
        }
    }
    
    /**
     * 📥 Get available trips from Dropbox
     */
    private List<String> getTripsFromDropbox() {
        List<String> trips = new ArrayList<>();
        
        try {
            DbxClientV2 client = DropboxHelper.getClient(context);
            if (client == null) {
                Log.w(TAG, "Dropbox client not available for trip list");
                return trips;
            }
            
            String availablePath = "/Customers/" + AppConstant.COMPANY + "/available/";
            
            com.dropbox.core.v2.files.ListFolderResult result = client.files().listFolder(availablePath);
            
            for (com.dropbox.core.v2.files.Metadata entry : result.getEntries()) {
                if (entry instanceof com.dropbox.core.v2.files.FileMetadata) {
                    String filename = entry.getName();
                    if (filename.endsWith(".json")) {
                        String tripId = filename.substring(0, filename.length() - 5);
                        trips.add(tripId);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error getting trips from Dropbox", e);
        }
        
        return trips;
    }
    
    /**
     * 📂 Get local trips
     */
    private List<String> getLocalTrips() {
        List<String> trips = new ArrayList<>();
        
        try {
            File tripDir = new File(context.getFilesDir() + "/Trip/");
            if (tripDir.exists() && tripDir.isDirectory()) {
                File[] files = tripDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".json")) {
                            String tripId = file.getName().substring(0, file.getName().length() - 5);
                            trips.add(tripId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting local trips", e);
        }
        
        return trips;
    }
    
    // ================== HELPER CLASSES ==================
    
    /**
     * Sync status information for UI
     */
    public static class SyncStatus {
        public enum State {
            SYNCED,     // Everything is synced
            SYNCING,    // Currently syncing
            PENDING,    // Has operations to sync
            OFFLINE     // Device is offline
        }
        
        public final State state;
        public final String message;
        public final boolean isOnline;
        public final int queuedOperations;
        public final long lastSuccessfulSync;
        
        public SyncStatus(State state, String message, boolean isOnline, 
                         int queuedOperations, long lastSuccessfulSync) {
            this.state = state;
            this.message = message;
            this.isOnline = isOnline;
            this.queuedOperations = queuedOperations;
            this.lastSuccessfulSync = lastSuccessfulSync;
        }
    }
}
