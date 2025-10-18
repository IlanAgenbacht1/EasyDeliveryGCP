package com.clone.EasyDelivery.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker.Result;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.clone.EasyDelivery.sync.models.SyncResult;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Simplified background worker for processing failed request retry queue
 * Uses WorkManager for reliable background execution with proper constraints
 */
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    // Work tags and names
    public static final String WORK_TAG_SYNC = "sync_work";
    public static final String WORK_NAME_PERIODIC_SYNC = "periodic_sync";
    public static final String WORK_NAME_IMMEDIATE_SYNC = "immediate_sync";

    // Work parameters
    private static final String KEY_FORCE_SYNC = "force_sync";

    // Timing configuration
    private static final long PERIODIC_SYNC_INTERVAL_MINUTES = 15; // 15 minutesl
    private static final long FLEX_INTERVAL_MINUTES = 10; // Allow 10 minute flex
    private static final long IMMEDIATE_SYNC_DELAY_SECONDS = 5; // 5 second delay for immediate sync

    // Simple retry manager
    private final SimpleRetryManager retryManager;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        // Get SimpleRetryManager instance - create manually since WorkManager doesn't support Hilt injection
        this.retryManager = SimpleRetryManager.createForWorker(context);

        if (this.retryManager == null) {
            Log.w(TAG, "RetryManager is null - retry operations will be skipped");
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "SyncWorker started - processing retry queue");

        try {
            // Get work parameters
            boolean forceSync = getInputData().getBoolean(KEY_FORCE_SYNC, false);

            Log.d(TAG, "Processing retry queue - Force: " + forceSync);

            // Process the retry queue
            SyncResult result = processRetryQueue();

            // Process result
            if (result.isSuccess()) {
                Log.i(TAG, "Retry processing completed successfully: " + result.getSummary());
                return Result.success(createOutputData(result));
            } else if (result.isPartialSuccess()) {
                Log.w(TAG, "Retry processing completed with warnings: " + result.getSummary());
                return Result.success(createOutputData(result));
            } else {
                Log.e(TAG, "Retry processing failed: " + result.getErrorMessage());
                return Result.retry(); // Retry on failure
            }

        } catch (Exception e) {
            Log.e(TAG, "SyncWorker failed with exception", e);
            return Result.retry(); // Retry on exception
        }
    }

    /**
     * Process the retry queue using SimpleRetryManager
     */
    private SyncResult processRetryQueue() {
        if (retryManager == null) {
            Log.w(TAG, "RetryManager is null, skipping retry queue processing");
            return SyncResult.success("Retry processing skipped - RetryManager not available", 0);
        }

        try {
            Log.d(TAG, "Starting retry queue processing");

            // Use SimpleRetryManager for centralized retry operations
            SyncResult result = retryManager.processFailedRequests().get();

            Log.d(TAG, "Retry queue processing completed: " + result.getSummary());
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Retry queue processing failed", e);
            return SyncResult.error("Retry processing failed: " + e.getMessage());
        }
    }

    /**
     * Create output data with sync results
     */
    private Data createOutputData(SyncResult result) {
        return new Data.Builder()
                .putBoolean("success", result.isSuccess())
                .putBoolean("partial_success", result.isPartialSuccess())
                .putString("message", result.getMessage())
                .putString("error_message", result.getErrorMessage())
                .putInt("synced_count", result.getSyncedCount())
                .putLong("timestamp", result.getTimestamp())
                .build();
    }

    // =================== STATIC SCHEDULER METHODS ===================

    /**
     * Schedule periodic background sync
     */
    public static void schedulePeriodicSync(Context context) {
        Log.i(TAG, "Scheduling periodic sync every " + PERIODIC_SYNC_INTERVAL_MINUTES + " minutes");

        // Create constraints - only run with network and when not in doze mode
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        // Create input data
        Data inputData = new Data.Builder()
                .putBoolean(KEY_FORCE_SYNC, false)
                .build();

        // Create periodic work request
        PeriodicWorkRequest periodicWork = new PeriodicWorkRequest.Builder(
                SyncWorker.class,
                PERIODIC_SYNC_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
                FLEX_INTERVAL_MINUTES,
                TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(WORK_TAG_SYNC)
                .build();

        // Schedule the work
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
                periodicWork
        );

        Log.d(TAG, "Periodic sync scheduled with ID: " + periodicWork.getId());
    }

    /**
     * Schedule immediate one-time sync
     */
    public static void scheduleImmediateSync(Context context, boolean forceSync) {
        Log.i(TAG, "Scheduling immediate sync - Force: " + forceSync);

        // Create constraints - require network
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Create input data
        Data inputData = new Data.Builder()
                .putBoolean(KEY_FORCE_SYNC, forceSync)
                .build();

        // Create one-time work request with short delay
        OneTimeWorkRequest immediateWork = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .setInitialDelay(IMMEDIATE_SYNC_DELAY_SECONDS, TimeUnit.SECONDS)
                .addTag(WORK_TAG_SYNC)
                .build();

        // Schedule the work
        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE_SYNC,
                ExistingWorkPolicy.REPLACE, // Replace any existing immediate sync
                immediateWork
        );

        Log.d(TAG, "Immediate sync scheduled with ID: " + immediateWork.getId());
    }

    /**
     * Schedule sync when network becomes available
     */
    public static void scheduleNetworkSync(Context context) {
        Log.i(TAG, "Scheduling network-available sync");

        // Create constraints - only network requirement
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Create input data for retry processing (most urgent)
        Data inputData = new Data.Builder()
                .putBoolean(KEY_FORCE_SYNC, false)
                .build();

        // Create one-time work request
        OneTimeWorkRequest networkWork = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(WORK_TAG_SYNC)
                .build();

        // Schedule the work
        WorkManager.getInstance(context).enqueue(networkWork);

        Log.d(TAG, "Network sync scheduled with ID: " + networkWork.getId());
    }

    /**
     * Cancel all scheduled sync work
     */
    public static void cancelAllSyncWork(Context context) {
        Log.i(TAG, "Cancelling all sync work");

        WorkManager workManager = WorkManager.getInstance(context);

        // Cancel by tag
        workManager.cancelAllWorkByTag(WORK_TAG_SYNC);

        // Cancel unique work
        workManager.cancelUniqueWork(WORK_NAME_PERIODIC_SYNC);
        workManager.cancelUniqueWork(WORK_NAME_IMMEDIATE_SYNC);

        Log.d(TAG, "All sync work cancelled");
    }

    /**
     * Cancel only periodic sync (keep immediate syncs)
     */
    public static void cancelPeriodicSync(Context context) {
        Log.i(TAG, "Cancelling periodic sync");

        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC_SYNC);

        Log.d(TAG, "Periodic sync cancelled");
    }

    /**
     * Check if sync work is scheduled
     */
    public static boolean isSyncScheduled(Context context) {
        // This would require querying WorkManager for active work
        // For simplicity, we'll assume it's scheduled if periodic sync is active
        return true; // Placeholder - implement proper check if needed
    }

    /**
     * Get sync work status info
     */
    public static void logSyncWorkStatus(Context context) {
        Log.d(TAG, "Querying sync work status...");

        WorkManager workManager = WorkManager.getInstance(context);

        // Query work by tag (this is async but we'll just log for now)
        workManager.getWorkInfosByTag(WORK_TAG_SYNC).addListener(() -> {
            try {
                List<WorkInfo> workInfos = workManager.getWorkInfosByTag(WORK_TAG_SYNC).get();
                Log.d(TAG, "Found " + workInfos.size() + " sync work items");

                for (WorkInfo workInfo : workInfos) {
                    Log.d(TAG, "Work ID: " + workInfo.getId() +
                            ", State: " + workInfo.getState() +
                              ", Tags: " + workInfo.getTags());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying work status", e);
            }
        }, new android.os.Handler(android.os.Looper.getMainLooper())::post); // API 24 compatible
    }
}