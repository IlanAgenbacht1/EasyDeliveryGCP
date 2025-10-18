package com.clone.EasyDelivery.viewmodel;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.clone.EasyDelivery.sync.models.SyncStatus;
import com.clone.EasyDelivery.viewmodel.models.AppState;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * MainViewModel manages global app-wide state with Hilt dependency injection
 * Coordinates network connectivity, sync status, settings, and cross-screen data
 */
@HiltViewModel
public class MainViewModel extends ViewModel {
    
    private static final String TAG = "MainViewModel";
    
    // Dependencies injected by Hilt
    private final SharedPreferences sharedPreferences;
    
    // App-wide state LiveData
    private final MutableLiveData<Boolean> networkConnectivityLiveData = new MutableLiveData<>();
    private final MutableLiveData<SyncStatus> syncStatusLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> syncProgressLiveData = new MutableLiveData<>();
    private final MutableLiveData<AppState> appStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isOfflineMode = new MutableLiveData<>();
    private final MutableLiveData<String> currentDriverId = new MutableLiveData<>();
    private final MutableLiveData<String> currentSessionId = new MutableLiveData<>();
    
    // Derived state
    private final MediatorLiveData<Boolean> shouldShowOfflineIndicator = new MediatorLiveData<>();
    private final MediatorLiveData<String> connectionStatusMessage = new MediatorLiveData<>();
    
    @Inject
    public MainViewModel(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
        
        // Initialize app state
        initializeAppState();
        
        // Setup derived state
        setupDerivedState();
        
        Log.i(TAG, "MainViewModel initialized with Hilt DI");
    }
    
    // =================== INITIALIZATION ===================
    
    private void initializeAppState() {
        // Initialize with default values
        networkConnectivityLiveData.setValue(true); // Assume connected initially
        syncStatusLiveData.setValue(SyncStatus.IDLE);
        syncProgressLiveData.setValue("Ready");
        appStateLiveData.setValue(AppState.RUNNING);
        isOfflineMode.setValue(false);
        
        // Restore saved state
        restoreAppState();
    }
    
    private void setupDerivedState() {
        // Show offline indicator when not connected or in offline mode
        shouldShowOfflineIndicator.addSource(networkConnectivityLiveData, connected -> 
            shouldShowOfflineIndicator.setValue(!connected || Boolean.TRUE.equals(isOfflineMode.getValue())));
        shouldShowOfflineIndicator.addSource(isOfflineMode, offline -> 
            shouldShowOfflineIndicator.setValue(offline || !Boolean.TRUE.equals(networkConnectivityLiveData.getValue())));
        
        // Connection status message
        connectionStatusMessage.addSource(networkConnectivityLiveData, this::updateConnectionMessage);
        connectionStatusMessage.addSource(syncStatusLiveData, syncStatus -> updateConnectionMessage(null));
        connectionStatusMessage.addSource(isOfflineMode, offline -> updateConnectionMessage(null));
    }
    
    private void updateConnectionMessage(Boolean connected) {
        Boolean isConnected = networkConnectivityLiveData.getValue();
        Boolean isOffline = isOfflineMode.getValue();
        SyncStatus syncStatus = syncStatusLiveData.getValue();
        
        if (Boolean.TRUE.equals(isOffline)) {
            connectionStatusMessage.setValue("Offline Mode");
        } else if (!Boolean.TRUE.equals(isConnected)) {
            connectionStatusMessage.setValue("No Connection");
        } else if (syncStatus == SyncStatus.SYNCING) {
            connectionStatusMessage.setValue("Syncing...");
        } else if (syncStatus == SyncStatus.ERROR) {
            connectionStatusMessage.setValue("Sync Error");
        } else {
            connectionStatusMessage.setValue("Online");
        }
    }
    
    // =================== NETWORK & CONNECTIVITY ===================
    
    /**
     * Update network connectivity status
     */
    public void updateNetworkConnectivity(boolean isConnected) {
        Log.d(TAG, "Network connectivity updated: " + isConnected);
        networkConnectivityLiveData.setValue(isConnected);
        
        // Save to preferences for persistence
        sharedPreferences.edit()
            .putBoolean("last_network_state", isConnected)
            .putLong("last_connectivity_check", System.currentTimeMillis())
            .apply();
    }
    
    /**
     * Check current network connectivity
     * Note: This should be called with actual connectivity status from ConnectionManager
     */
    public void checkNetworkConnectivity() {
        // This method should be called with actual connectivity status from ConnectionManager
        // The actual connectivity check is now handled by the ConnectionManager utility
        Log.d(TAG, "Network connectivity check requested - should be called with actual status");
    }
    
    /**
     * Toggle offline mode
     */
    public void toggleOfflineMode() {
        boolean currentOfflineMode = Boolean.TRUE.equals(isOfflineMode.getValue());
        boolean newOfflineMode = !currentOfflineMode;
        
        Log.i(TAG, "Toggling offline mode: " + currentOfflineMode + " -> " + newOfflineMode);
        isOfflineMode.setValue(newOfflineMode);
        
        // Save to preferences
        sharedPreferences.edit()
            .putBoolean("offline_mode", newOfflineMode)
            .apply();
    }
    
    /**
     * Set offline mode explicitly
     */
    public void setOfflineMode(boolean offline) {
        Log.i(TAG, "Setting offline mode: " + offline);
        isOfflineMode.setValue(offline);
        
        sharedPreferences.edit()
            .putBoolean("offline_mode", offline)
            .apply();
    }
    
    // =================== SYNC STATUS MANAGEMENT ===================
    
    /**
     * Update sync status
     */
    public void updateSyncStatus(SyncStatus status) {
        Log.d(TAG, "Sync status updated: " + status);
        syncStatusLiveData.setValue(status);
    }
    
    /**
     * Update sync progress message
     */
    public void updateSyncProgress(String progress) {
        Log.d(TAG, "Sync progress: " + progress);
        syncProgressLiveData.setValue(progress);
    }
    
    /**
     * Start sync operation
     */
    public void startSync() {
        Log.i(TAG, "Starting sync operation");
        updateSyncStatus(SyncStatus.SYNCING);
        updateSyncProgress("Preparing sync...");
    }
    
    /**
     * Complete sync operation
     */
    public void completeSyncWithSuccess() {
        Log.i(TAG, "Sync completed successfully");
        updateSyncStatus(SyncStatus.COMPLETED);
        updateSyncProgress("Sync completed");
        
        // Save last sync time
        sharedPreferences.edit()
            .putLong("last_sync_time", System.currentTimeMillis())
            .apply();
        
        // Reset to idle after a short delay
        // In a real implementation, this would be handled by the sync manager
    }
    
    /**
     * Handle sync error
     */
    public void handleSyncError(String errorMessage) {
        Log.w(TAG, "Sync error: " + errorMessage);
        updateSyncStatus(SyncStatus.ERROR);
        updateSyncProgress("Sync failed: " + errorMessage);
    }
    
    // =================== SESSION & DRIVER MANAGEMENT ===================
    
    /**
     * Update current driver ID
     */
    public void updateCurrentDriver(String driverId) {
        Log.d(TAG, "Current driver updated: " + driverId);
        currentDriverId.setValue(driverId);
        
        sharedPreferences.edit()
            .putString("current_driver_id", driverId)
            .apply();
    }
    
    /**
     * Update current session ID
     */
    public void updateCurrentSession(String sessionId) {
        Log.d(TAG, "Current session updated: " + sessionId);
        currentSessionId.setValue(sessionId);
        
        sharedPreferences.edit()
            .putString("current_session_id", sessionId)
            .putLong("session_start_time", System.currentTimeMillis())
            .apply();
    }
    
    /**
     * Clear session data
     */
    public void clearSessionData() {
        Log.i(TAG, "Clearing session data");
        currentDriverId.setValue(null);
        currentSessionId.setValue(null);
        
        sharedPreferences.edit()
            .remove("current_driver_id")
            .remove("current_session_id")
            .remove("session_start_time")
            .apply();
    }
    
    // =================== APP STATE MANAGEMENT ===================
    
    /**
     * Update app state
     */
    public void updateAppState(AppState state) {
        Log.d(TAG, "App state updated: " + state);
        appStateLiveData.setValue(state);
    }
    
    /**
     * Restore app state from preferences
     */
    private void restoreAppState() {
        try {
            // Restore network state
            boolean lastNetworkState = sharedPreferences.getBoolean("last_network_state", true);
            networkConnectivityLiveData.setValue(lastNetworkState);
            
            // Restore offline mode
            boolean offlineMode = sharedPreferences.getBoolean("offline_mode", false);
            isOfflineMode.setValue(offlineMode);
            
            // Restore driver and session
            String driverId = sharedPreferences.getString("current_driver_id", null);
            if (driverId != null) {
                currentDriverId.setValue(driverId);
            }
            
            String sessionId = sharedPreferences.getString("current_session_id", null);
            if (sessionId != null) {
                currentSessionId.setValue(sessionId);
            }
            
            Log.d(TAG, "App state restored from preferences");
            
        } catch (Exception e) {
            Log.e(TAG, "Error restoring app state", e);
        }
    }
    
    /**
     * Save current app state to preferences
     */
    public void saveAppState() {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            // Save current connectivity
            if (networkConnectivityLiveData.getValue() != null) {
                editor.putBoolean("last_network_state", networkConnectivityLiveData.getValue());
            }
            
            // Save offline mode
            if (isOfflineMode.getValue() != null) {
                editor.putBoolean("offline_mode", isOfflineMode.getValue());
            }
            
            // Save current driver and session
            if (currentDriverId.getValue() != null) {
                editor.putString("current_driver_id", currentDriverId.getValue());
            }
            
            if (currentSessionId.getValue() != null) {
                editor.putString("current_session_id", currentSessionId.getValue());
            }
            
            editor.putLong("last_state_save", System.currentTimeMillis());
            editor.apply();
            
            Log.d(TAG, "App state saved to preferences");
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving app state", e);
        }
    }
    
    // =================== GETTERS FOR UI STATE ===================
    
    public LiveData<Boolean> getNetworkConnectivity() {
        return networkConnectivityLiveData;
    }
    
    public LiveData<SyncStatus> getSyncStatus() {
        return syncStatusLiveData;
    }
    
    public LiveData<String> getSyncProgress() {
        return syncProgressLiveData;
    }
    
    public LiveData<AppState> getAppState() {
        return appStateLiveData;
    }
    
    public LiveData<Boolean> getIsOfflineMode() {
        return isOfflineMode;
    }
    
    public LiveData<String> getCurrentDriverId() {
        return currentDriverId;
    }
    
    public LiveData<String> getCurrentSessionId() {
        return currentSessionId;
    }
    
    public LiveData<Boolean> getShouldShowOfflineIndicator() {
        return shouldShowOfflineIndicator;
    }
    
    public LiveData<String> getConnectionStatusMessage() {
        return connectionStatusMessage;
    }
    
    // =================== UTILITY METHODS ===================
    
    /**
     * Get last sync time as formatted string
     */
    public String getLastSyncTimeFormatted() {
        long lastSyncTime = sharedPreferences.getLong("last_sync_time", 0);
        if (lastSyncTime == 0) {
            return "Never";
        }
        
        long timeDiff = System.currentTimeMillis() - lastSyncTime;
        long minutes = timeDiff / (1000 * 60);
        
        if (minutes < 1) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " min ago";
        } else {
            long hours = minutes / 60;
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        }
    }
    
    /**
     * Check if app needs sync based on time
     */
    public boolean needsSync() {
        long lastSyncTime = sharedPreferences.getLong("last_sync_time", 0);
        long currentTime = System.currentTimeMillis();
        long syncInterval = 15 * 60 * 1000; // 15 minutes
        
        return (currentTime - lastSyncTime) > syncInterval;
    }
    
    /**
     * Get session duration in minutes
     */
    public long getSessionDurationMinutes() {
        long sessionStartTime = sharedPreferences.getLong("session_start_time", 0);
        if (sessionStartTime == 0) {
            return 0;
        }
        
        return (System.currentTimeMillis() - sessionStartTime) / (1000 * 60);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        
        // Save state when ViewModel is cleared
        saveAppState();
        
        Log.i(TAG, "MainViewModel cleared");
    }
}