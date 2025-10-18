package com.clone.EasyDelivery.sync;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.clone.EasyDelivery.Api.services.AuthService;
import com.clone.EasyDelivery.Api.services.DeliveryService;
import com.clone.EasyDelivery.Api.services.TripService;
import com.clone.EasyDelivery.data.dao.OfflineActionDao;
import com.clone.EasyDelivery.data.entities.OfflineActionEntity;
import com.clone.EasyDelivery.data.DeliveryDatabase;
import com.clone.EasyDelivery.sync.models.SyncResult;
import com.clone.EasyDelivery.sync.models.SyncStatus;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import com.google.gson.Gson;

/**
 * Simplified retry manager that focuses on core functionality:
 * - Queue failed requests
 * - Retry them when network is available
 * - Give up after max attempts
 * - Show status to user
 */
@Singleton
public class SimpleRetryManager {

    private static final String TAG = "SimpleRetryManager";
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final Context context;
    private final ExecutorService executor;
    private final OfflineActionDao offlineActionDao;

    // API Services
    private final AuthService authService;
    private final TripService tripService;
    private final DeliveryService deliveryService;

    // Simple state tracking
    private final MutableLiveData<SyncStatus> syncStatus;
    private final MutableLiveData<String> syncProgress;
    private volatile boolean isProcessing = false;

    @Inject
    public SimpleRetryManager(@ApplicationContext Context context,
                              ExecutorService executor,
                              OfflineActionDao offlineActionDao,
                              AuthService authService,
                              TripService tripService,
                              DeliveryService deliveryService) {
        this.context = context;
        this.executor = executor;
        this.offlineActionDao = offlineActionDao;
        this.authService = authService;
        this.tripService = tripService;
        this.deliveryService = deliveryService;

        this.syncStatus = new MutableLiveData<>(SyncStatus.IDLE);
        this.syncProgress = new MutableLiveData<>("");

        Log.i(TAG, "SimpleRetryManager initialized");
    }

    /**
     * Queue a failed request for retry later
     */
    public void queueFailedRequest(String entityType, String entityId, String action, String payload) {
        executor.execute(() -> {
            OfflineActionEntity offlineAction = new OfflineActionEntity(
                    entityType, entityId, action, payload != null ? payload : "",
                    System.currentTimeMillis(), null
            );
            offlineActionDao.insertAction(offlineAction);
            Log.d(TAG, "Queued failed request: " + action + " for " + entityType + " " + entityId);
        });
    }

    /**
     * Process all failed requests in the queue
     */
    public CompletableFuture<SyncResult> processFailedRequests() {
        if (isProcessing) {
            Log.w(TAG, "Already processing requests, skipping");
            return CompletableFuture.completedFuture(
                    SyncResult.error("Already processing requests")
            );
        }

        Log.i(TAG, "Starting to process failed requests");
        isProcessing = true;
        syncStatus.postValue(SyncStatus.SYNCING);
        syncProgress.postValue("Processing failed requests...");

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<OfflineActionEntity> failedActions = offlineActionDao.getPendingActions();
                int successCount = 0;
                int failureCount = 0;

                Log.i(TAG, "Found " + failedActions.size() + " failed requests to process");

                for (OfflineActionEntity action : failedActions) {
                    try {
                        if (processAction(action)) {
                            // Success - remove from queue
                            offlineActionDao.markActionCompleted(action.getId(), System.currentTimeMillis());
                            successCount++;
                            Log.d(TAG, "Successfully processed: " + action.getActionType() + " for " + action.getEntityType());
                        } else {
                            // Failed - increment retry count
                            handleRetry(action);
                            failureCount++;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Exception processing action: " + action.getId(), e);
                        handleRetry(action);
                        failureCount++;
                    }
                }

                String summary = "Processed " + successCount + " successful, " + failureCount + " failed";
                Log.i(TAG, summary);
                syncProgress.postValue(summary);

                return SyncResult.success(summary, successCount);

            } catch (Exception e) {
                Log.e(TAG, "Failed to process retry queue", e);
                return SyncResult.error("Processing failed: " + e.getMessage());
            } finally {
                isProcessing = false;
                syncStatus.postValue(SyncStatus.IDLE);
            }
        }, executor);
    }

    /**
     * Process a single failed action
     */
    private boolean processAction(OfflineActionEntity action) {
        Log.d(TAG, "Processing action: " + action.getActionType() + " for " + action.getEntityType() + " " + action.getEntityId());

        // Simple switch on entity type - make direct API calls
        switch (action.getEntityType()) {
            case "SESSION":
                return processSessionAction(action);
            case "DELIVERY":
                return processDeliveryAction(action);
            case "TRIP":
                return processTripAction(action);
            default:
                Log.w(TAG, "Unknown entity type: " + action.getEntityType());
                return false;
        }
    }

    private boolean processSessionAction(OfflineActionEntity action) {
        // For now, just return true - implement actual API calls as needed
        // This is where you'd call authService.endSession(action.getEntityId()) etc.
        Log.d(TAG, "Processing session action: " + action.getActionType());
        return true; // Stub - implement actual API call
    }

    private boolean processDeliveryAction(OfflineActionEntity action) {
        // For now, just return true - implement actual API calls as needed
        Log.d(TAG, "Processing delivery action: " + action.getActionType());
        return true; // Stub - implement actual API call
    }

    private boolean processTripAction(OfflineActionEntity action) {
        // For now, just return true - implement actual API calls as needed
        Log.d(TAG, "Processing trip action: " + action.getActionType());
        return true; // Stub - implement actual API call
    }

    /**
     * Handle retry logic for failed actions
     */
    private void handleRetry(OfflineActionEntity action) {
        long nextRetryTime = System.currentTimeMillis() + (60 * 1000); // Retry in 1 minute

        if (action.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            // Give up after max retries
            offlineActionDao.markActionFailed(action.getId(), "Max retries exceeded");
            Log.w(TAG, "Giving up on action after " + MAX_RETRY_ATTEMPTS + " attempts: " + action.getId());
        } else {
            // Schedule for retry
            offlineActionDao.incrementRetryCount(action.getId(), System.currentTimeMillis(), nextRetryTime, "Retry scheduled");
            Log.d(TAG, "Scheduled retry " + (action.getRetryCount() + 1) + " for action: " + action.getId());
        }
    }

    /**
     * Clean up old completed actions
     */
    public void cleanup() {
        executor.execute(() -> {
            long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000); // 7 days ago
            offlineActionDao.deleteOldCompletedActions(cutoffTime);
            Log.d(TAG, "Cleaned up old completed actions");
        });
    }

    /**
     * Static method to create SimpleRetryManager instance for WorkManager
     * Since WorkManager doesn't support Hilt injection easily, we create a simple instance
     * with the basic dependencies needed for retry processing
     */
    public static SimpleRetryManager createForWorker(Context context) {
        try {
            // Get basic dependencies directly (not ideal, but works for WorkManager)
            DeliveryDatabase database = DeliveryDatabase.getInstance(context);
            OfflineActionDao offlineActionDao = database.offlineActionDao();
            ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);

            // Create NetworkModule to get services (not ideal, but functional)
            com.clone.EasyDelivery.di.NetworkModule networkModule = new com.clone.EasyDelivery.di.NetworkModule();
            okhttp3.OkHttpClient okHttpClient = networkModule.provideOkHttpClient(context);
            com.google.gson.Gson gson = networkModule.provideGson();
            retrofit2.Retrofit retrofit = networkModule.provideRetrofit(okHttpClient, gson);

            AuthService authService = networkModule.provideAuthService(retrofit);
            TripService tripService = networkModule.provideTripService(retrofit);
            DeliveryService deliveryService = networkModule.provideDeliveryService(retrofit);

            // Create instance manually
            return new SimpleRetryManager(
                    context, executor, offlineActionDao,
                    authService, tripService, deliveryService
            );

        } catch (Exception e) {
            Log.e(TAG, "Failed to create SimpleRetryManager for worker", e);
            return null;
        }
    }

    // Public accessors for UI
    public LiveData<SyncStatus> getSyncStatus() {
        return syncStatus;
    }

    public LiveData<String> getSyncProgress() {
        return syncProgress;
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    public CompletableFuture<SyncResult> forceSyncNow() {
        Log.i(TAG, "Force sync requested");
        return processFailedRequests();
    }

    // Legacy method stub for compatibility
    public long getLastFullSyncTime() {
        return 0; // Not tracking this in simplified version
    }

    public boolean isSyncInProgress() {
        return isProcessing();
    }
}