package com.clone.EasyDelivery;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.work.Configuration;
import androidx.work.WorkerFactory;
import androidx.work.WorkManager;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.ExistingPeriodicWorkPolicy;

import com.clone.EasyDelivery.sync.SyncWorker;
import com.google.firebase.FirebaseApp;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

/**
 * EasyDeliveryApplication - Main Application class with Hilt DI
 * 
 * Handles proper initialization of Firebase, WorkManager for background sync,
 * and Hilt dependency injection. Replaces manual service instantiation with DI.
 */
@HiltAndroidApp
public class EasyDeliveryApplication extends Application implements Configuration.Provider {
    
    private static final String TAG = "EasyDeliveryApp";
    private static final String PREFS_NAME = "EasyDeliveryApp";
    private static final String PREF_APP_VERSION = "app_version";
    private static final String SYNC_WORK_NAME = "background_sync";
    
    @Inject
    WorkerFactory workerFactory;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.i(TAG, "=== EasyDelivery Application Starting with Hilt DI ===");
        
        try {
            // Initialize Firebase for Firebase Storage (keeping minimal Firebase setup)
            initializeFirebase();
            
            // Initialize WorkManager for background sync
            initializeBackgroundSync();
            
            // Restore state from previous session
            restoreApplicationState();
            
            Log.i(TAG, "✅ EasyDelivery Application initialized successfully with Hilt");
            
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
            
            Log.i(TAG, "✅ EasyDelivery Application terminated cleanly");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during application termination", e);
        }
        
        super.onTerminate();
    }
    
    /**
     * Initialize Firebase services (minimal setup for Firebase Storage only)
     */
    private void initializeFirebase() {
        try {
            Log.d(TAG, "Initializing Firebase services...");

            // Check if we have a valid Firebase configuration
            if (!isValidFirebaseConfig()) {
                Log.w(TAG, "⚠️ Invalid Firebase configuration detected. Skipping Firebase initialization.");
                Log.w(TAG, "Please replace google-services.json with a valid Firebase configuration file.");
                return;
            }

            // Initialize Firebase App
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
                Log.d(TAG, "Firebase App initialized");
            }
            
            Log.d(TAG, "✅ Firebase services initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize Firebase services", e);
            Log.w(TAG, "App will continue without Firebase functionality. Please check your google-services.json file.");
            // Don't throw exception to prevent app crash - just log and continue
        }
    }
    
    /**
     * Check if Firebase configuration is valid (not placeholder values)
     */
    private boolean isValidFirebaseConfig() {
        try {
            // Try to get Firebase app default instance to check if config is valid
            // This is a simple check - if the config has placeholder values, it will fail
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            android.os.Bundle bundle = ai.metaData;

            if (bundle != null) {
                String apiKey = bundle.getString("com.google.firebase.analytics.AnalyticsConnector");
                // Check for common placeholder patterns
                if (apiKey != null && (apiKey.contains("1234567") || apiKey.contains("abcdef"))) {
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            Log.w(TAG, "Could not validate Firebase config: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initialize WorkManager for background synchronization
     */
    private void initializeBackgroundSync() {
        try {
            Log.d(TAG, "Initializing WorkManager background sync...");
            
            // Create constraints for sync work (require network)
            Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
            
            // Create periodic work request for background sync
            PeriodicWorkRequest syncWorkRequest = new PeriodicWorkRequest.Builder(
                    SyncWorker.class,
                    15, TimeUnit.MINUTES // Sync every 15 minutes
                )
                .setConstraints(constraints)
                .build();
            
            // Schedule the work
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncWorkRequest
            );
            
            Log.d(TAG, "✅ WorkManager background sync initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize background sync", e);
            throw new RuntimeException("Background sync initialization failed", e);
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
            
            // Note: State restoration now handled by API client and offline manager
            
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
     * Provide WorkManager Configuration for Hilt WorkerFactory
     */
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build();
    }
}
