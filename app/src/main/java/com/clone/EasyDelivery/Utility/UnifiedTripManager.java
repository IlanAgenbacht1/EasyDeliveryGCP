package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.util.Log;
import com.clone.EasyDelivery.Application.EasyDeliveryApplication;
import com.clone.EasyDelivery.Model.*;
import com.clone.EasyDelivery.Model.Enum.TripStatus;
import com.clone.EasyDelivery.Firebase.FirestoreSyncManager;
import com.clone.EasyDelivery.Firebase.Models.FirestoreTrip;
import com.clone.EasyDelivery.Firebase.Models.FirestoreDelivery;
import com.clone.EasyDelivery.Firebase.Models.SessionInfo;
import com.clone.EasyDelivery.Utility.RealmClient;

import io.realm.Realm;
import io.realm.RealmResults;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.auth.FirebaseAuth;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * UnifiedTripManager - Modern Firebase/Firestore trip management
 * 
 * Features:
 * - Firebase/Firestore real-time operations
 * - Transaction-based atomic operations
 * - Device-aware operations with Firebase Auth
 * - Offline-first with automatic sync
 * - Clean, modern architecture
 */
public class UnifiedTripManager {
    
    private static final String TAG = "UnifiedTripManager";
    
    private final Context context;
    private final FirestoreSyncManager firestoreSyncManager;
    private final String deviceId;
    private static UnifiedTripManager instance;
    
    // Real-time trip listeners
    private final Map<String, ListenerRegistration> tripListeners = new HashMap<>();
    
    // Local tracking for UI optimization
    private final Set<String> locallyClaimedTrips = new HashSet<>();
    
    private UnifiedTripManager(Context context) {
        this.context = context.getApplicationContext();
        this.firestoreSyncManager = FirestoreSyncManager.getInstance(context);
        this.deviceId = firestoreSyncManager != null ? firestoreSyncManager.getCurrentDeviceId() : generateDeviceId();
        
        Log.i(TAG, "UnifiedTripManager initialized with Firestore - Device ID: " + deviceId);
    }
    
    public static synchronized UnifiedTripManager getInstance(Context context) {
        if (instance == null) {
            instance = new UnifiedTripManager(context);
        }
        return instance;
    }
    
    /**
     * Claim a trip using Firestore transaction
     */
    public boolean claimTrip(String tripId) {
        return claimTrip(tripId, AppConstant.DRIVER, AppConstant.VEHICLE);
    }
    
    /**
     * Claim a trip with driver and vehicle information
     */
    public boolean claimTrip(String tripId, String driverId, String vehicleId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot claim trip - invalid trip ID");
            return false;
        }
        
        if (firestoreSyncManager == null) {
            Log.e(TAG, "FirestoreSyncManager not available for trip claiming");
            return false;
        }
        
        Log.i(TAG, "🎯 Claiming trip: " + tripId + " (driver: " + driverId + ", vehicle: " + vehicleId + ")");
        
        try {
            Task<Boolean> claimTask = firestoreSyncManager.claimTrip(tripId, driverId, vehicleId);
            Boolean success = Tasks.await(claimTask);
            
            if (success != null && success) {
                locallyClaimedTrips.add(tripId);
                // Start listening to real-time updates for this trip
                startTripListener(tripId);
                Log.i(TAG, "✅ Trip claimed successfully: " + tripId);
                return true;
            } else {
                Log.w(TAG, "⚠️ Trip claim failed: " + tripId);
                return false;
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException && cause.getMessage() != null) {
                Log.w(TAG, "⚠️ Trip claim failed: " + tripId + " - " + cause.getMessage());
            } else {
                Log.e(TAG, "❌ Trip claim error: " + tripId, e);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "❌ Trip claim interrupted: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Start a trip using Firestore transaction
     */
    public boolean startTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot start trip - invalid trip ID");
            return false;
        }
        
        if (firestoreSyncManager == null) {
            Log.e(TAG, "FirestoreSyncManager not available for trip starting");
            return false;
        }
        
        Log.i(TAG, "🚀 Starting trip: " + tripId);
        
        try {
            Task<Boolean> startTask = firestoreSyncManager.startTrip(tripId);
            Boolean success = Tasks.await(startTask);
            
            if (success != null && success) {
                Log.i(TAG, "✅ Trip started successfully: " + tripId);
                // Update local app state
                AppConstant.TRIPID = tripId;
                return true;
            } else {
                Log.w(TAG, "⚠️ Trip start failed: " + tripId);
                return false;
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException && cause.getMessage() != null) {
                Log.w(TAG, "⚠️ Trip start failed: " + tripId + " - " + cause.getMessage());
            } else {
                Log.e(TAG, "❌ Trip start error: " + tripId, e);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "❌ Trip start interrupted: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Complete a trip using Firestore batch operation
     */
    public boolean completeTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot complete trip - invalid trip ID");
            return false;
        }
        
        if (firestoreSyncManager == null) {
            Log.e(TAG, "FirestoreSyncManager not available for trip completion");
            return false;
        }
        
        Log.i(TAG, "🏁 Completing trip: " + tripId);
        
        try {
            Task<Boolean> completeTask = firestoreSyncManager.completeTrip(tripId);
            Boolean success = Tasks.await(completeTask);
            
            if (success != null && success) {
                locallyClaimedTrips.remove(tripId);
                // Stop listening to this trip
                stopTripListener(tripId);
                // Update local app state
                if (tripId.equals(AppConstant.TRIPID)) {
                    AppConstant.TRIPID = "";
                }
                AppConstant.completedTrips.add(tripId);
                Log.i(TAG, "✅ Trip completed successfully: " + tripId);
                return true;
            } else {
                Log.w(TAG, "⚠️ Trip completion failed: " + tripId);
                return false;
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException && cause.getMessage() != null) {
                Log.w(TAG, "⚠️ Trip completion failed: " + tripId + " - " + cause.getMessage());
            } else {
                Log.e(TAG, "❌ Trip completion error: " + tripId, e);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "❌ Trip completion interrupted: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Release a trip using Firestore transaction
     */
    public boolean releaseTrip(String tripId, String reason) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot release trip - invalid trip ID");
            return false;
        }
        
        if (reason == null) {
            reason = "Manual release";
        }
        
        if (firestoreSyncManager == null) {
            Log.e(TAG, "FirestoreSyncManager not available for trip release");
            return false;
        }
        
        Log.i(TAG, "↩️ Releasing trip: " + tripId + " (reason: " + reason + ")");
        
        try {
            Task<Boolean> releaseTask = firestoreSyncManager.releaseTrip(tripId, reason);
            Boolean success = Tasks.await(releaseTask);
            
            if (success != null && success) {
                locallyClaimedTrips.remove(tripId);
                // Stop listening to this trip
                stopTripListener(tripId);
                // Update local app state
                if (tripId.equals(AppConstant.TRIPID)) {
                    AppConstant.TRIPID = "";
                }
                Log.i(TAG, "✅ Trip released successfully: " + tripId);
                return true;
            } else {
                Log.w(TAG, "⚠️ Trip release failed: " + tripId);
                return false;
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException && cause.getMessage() != null) {
                Log.w(TAG, "⚠️ Trip release failed: " + tripId + " - " + cause.getMessage());
            } else {
                Log.e(TAG, "❌ Trip release error: " + tripId, e);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "❌ Trip release interrupted: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Release a trip without reason
     */
    public boolean releaseTrip(String tripId) {
        return releaseTrip(tripId, "Manual release");
    }
    
    /**
     * Claim and start a trip in one operation
     */
    public boolean claimAndStartTrip(String tripId) {
        Log.i(TAG, "Claim and start trip: " + tripId);
        
        boolean claimed = claimTrip(tripId);
        if (claimed) {
            // Small delay to let Firestore update propagate
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            boolean started = startTrip(tripId);
            if (started) {
                Log.i(TAG, "Successfully claimed and started trip: " + tripId);
                return true;
            } else {
                // If start failed, release the claim
                Log.w(TAG, "Start failed, releasing claim for trip: " + tripId);
                releaseTrip(tripId, "Start failed after claim");
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Update delivery status within a trip
     */
    public boolean updateDeliveryStatus(String tripId, String deliveryId, String status, Map<String, Object> metadata) {
        if (firestoreSyncManager == null) {
            Log.e(TAG, "FirestoreSyncManager not available for delivery update");
            return false;
        }
        
        Log.i(TAG, "Updating delivery status: " + deliveryId + " in trip: " + tripId + " -> " + status);
        
        try {
            Task<Boolean> updateTask = firestoreSyncManager.updateDeliveryStatus(tripId, deliveryId, status, metadata);
            Boolean success = Tasks.await(updateTask);
            
            if (success != null && success) {
                Log.i(TAG, "✅ Delivery status updated: " + deliveryId);
                return true;
            } else {
                Log.w(TAG, "⚠️ Delivery status update failed: " + deliveryId);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error updating delivery status: " + deliveryId, e);
            return false;
        }
    }
    
    /**
     * Get available trips from Firestore
     */
    public CompletableFuture<List<FirestoreTrip>> getAvailableTrips() {
        if (firestoreSyncManager == null) {
            CompletableFuture<List<FirestoreTrip>> future = new CompletableFuture<>();
            future.complete(new ArrayList<>());
            return future;
        }
        
        return firestoreSyncManager.getAvailableTrips()
            .thenApply(trips -> {
                Log.i(TAG, "Retrieved " + trips.size() + " available trips from Firestore");
                return trips;
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "Error retrieving available trips", throwable);
                return new ArrayList<>();
            });
    }
    
    /**
     * Get a specific trip from Firestore
     */
    public CompletableFuture<FirestoreTrip> getTrip(String tripId) {
        if (firestoreSyncManager == null) {
            CompletableFuture<FirestoreTrip> future = new CompletableFuture<>();
            future.complete(null);
            return future;
        }
        
        return firestoreSyncManager.getTrip(tripId);
    }
    
    /**
     * Get trips assigned to current device
     */
    public CompletableFuture<List<FirestoreTrip>> getMyTrips() {
        if (firestoreSyncManager == null) {
            CompletableFuture<List<FirestoreTrip>> future = new CompletableFuture<>();
            future.complete(new ArrayList<>());
            return future;
        }
        
        return firestoreSyncManager.getMyTrips()
            .thenApply(trips -> {
                // Update local tracking
                locallyClaimedTrips.clear();
                for (FirestoreTrip trip : trips) {
                    locallyClaimedTrips.add(trip.getTripId());
                }
                
                Log.i(TAG, "Retrieved " + trips.size() + " assigned trips from Firestore");
                return trips;
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "Error retrieving assigned trips", throwable);
                return new ArrayList<>();
            });
    }
    
    /**
     * Start real-time listener for a specific trip
     */
    private void startTripListener(String tripId) {
        if (firestoreSyncManager == null || tripListeners.containsKey(tripId)) {
            return;
        }
        
        ListenerRegistration listener = firestoreSyncManager.listenToTrip(tripId, new FirestoreSyncManager.TripUpdateCallback() {
            @Override
            public void onTripUpdated(FirestoreTrip trip) {
                Log.d(TAG, "Real-time trip update: " + tripId + " -> " + trip.getStatus());
                // Update local Realm database
                updateLocalTrip(trip);
            }
            
            @Override
            public void onError(Exception error) {
                Log.e(TAG, "Trip listener error for " + tripId, error);
            }
        });
        
        tripListeners.put(tripId, listener);
        Log.d(TAG, "Started real-time listener for trip: " + tripId);
    }
    
    /**
     * Stop real-time listener for a specific trip
     */
    private void stopTripListener(String tripId) {
        ListenerRegistration listener = tripListeners.remove(tripId);
        if (listener != null) {
            listener.remove();
            Log.d(TAG, "Stopped real-time listener for trip: " + tripId);
        }
    }
    
    /**
     * Update local Realm database with Firestore trip data
     */
    private void updateLocalTrip(FirestoreTrip firestoreTrip) {
        try {
            Realm realm = RealmClient.getInstance(context);
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    // Convert Firestore trip to local Trip model
                    Trip localTrip = realm.createObject(Trip.class, firestoreTrip.getTripId());
                    localTrip.setStatus(firestoreTrip.getStatus());
                    localTrip.setDriverId(firestoreTrip.getDriverId());
                    localTrip.setVehicleId(firestoreTrip.getVehicleId());
                    localTrip.setStartTime(firestoreTrip.getStartTime());
                    localTrip.setEndTime(firestoreTrip.getEndTime());
                    localTrip.setLastModified(firestoreTrip.getLastModified());
                    
                    // Update deliveries
                    for (FirestoreDelivery firestoreDelivery : firestoreTrip.getDeliveries()) {
                        Delivery localDelivery = realm.createObject(Delivery.class, firestoreDelivery.getDeliveryId());
                        localDelivery.setTripId(firestoreTrip.getTripId());
                        localDelivery.setDocument(firestoreDelivery.getDocument());
                        localDelivery.setStatus(firestoreDelivery.getStatus());
                        localDelivery.setCustomerName(firestoreDelivery.getCustomerName());
                        localDelivery.setAddress(firestoreDelivery.getAddress());
                        // Set other delivery fields...
                        localTrip.getDeliveries().add(localDelivery);
                    }
                    
                    Log.d(TAG, "Updated local trip: " + firestoreTrip.getTripId());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating local trip: " + firestoreTrip.getTripId(), e);
        }
    }
    
    /**
     * Upload POD (Proof of Delivery) image to Firebase Storage
     */
    public CompletableFuture<String> uploadPODImage(String tripId, String deliveryId, byte[] imageData) {
        if (firestoreSyncManager == null) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("FirestoreSyncManager not available"));
            return future;
        }
        
        return firestoreSyncManager.uploadPODImage(tripId, deliveryId, imageData);
    }
    
    /**
     * Get current device ID
     */
    public String getDeviceId() {
        return deviceId;
    }
    
    /**
     * Check if we have locally claimed trips
     */
    public Set<String> getLocallyClaimedTrips() {
        return new HashSet<>(locallyClaimedTrips);
    }
    
    /**
     * Force sync with Firestore
     */
    public void forceSync() {
        if (firestoreSyncManager != null) {
            Log.i(TAG, "Force sync requested - enabling Firestore offline persistence");
            firestoreSyncManager.enableOfflinePersistence();
        }
    }
    
    /**
     * Get sync status for UI display
     */
    public String getSyncStatus() {
        if (firestoreSyncManager == null) {
            return "FirestoreSyncManager not available";
        }
        
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return "Connected to Firestore";
        } else {
            return "Not authenticated with Firebase";
        }
    }
    
    /**
     * Get system statistics for debugging
     */
    public String getSystemStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Unified Trip Manager Status:\n");
        stats.append("Device ID: ").append(deviceId).append("\n");
        stats.append("Locally Claimed Trips: ").append(locallyClaimedTrips.size()).append("\n");
        stats.append("Active Trip Listeners: ").append(tripListeners.size()).append("\n");
        stats.append("Firebase Auth: ").append(FirebaseAuth.getInstance().getCurrentUser() != null ? "Authenticated" : "Not authenticated").append("\n");
        stats.append("Sync Status: ").append(getSyncStatus()).append("\n");
        
        return stats.toString();
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        // Stop all trip listeners
        for (Map.Entry<String, ListenerRegistration> entry : tripListeners.entrySet()) {
            entry.getValue().remove();
            Log.d(TAG, "Stopped listener for trip: " + entry.getKey());
        }
        tripListeners.clear();
        
        // Clear local tracking
        locallyClaimedTrips.clear();
        
        Log.i(TAG, "UnifiedTripManager cleanup completed");
    }
    
    /**
     * Generate a unique device ID
     */
    private String generateDeviceId() {
        try {
            String androidId = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
            );
            
            String driverName = AppConstant.DRIVER != null ? 
                AppConstant.DRIVER.replaceAll("[^A-Za-z0-9]", "") : "Driver";
            
            StringBuilder composite = new StringBuilder();
            composite.append(driverName).append("_");
            
            if (androidId != null && androidId.length() > 4) {
                composite.append(androidId.substring(0, Math.min(8, androidId.length())));
            } else {
                composite.append("DEV");
            }
            
            if (android.os.Build.MODEL != null) {
                int modelHash = Math.abs(android.os.Build.MODEL.hashCode() % 10000);
                composite.append("_").append(modelHash);
            }
            
            return composite.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating device ID, using fallback", e);
            return "Device_FALLBACK_" + System.currentTimeMillis();
        }
    }
}
            
            SyncOperation operation = ReleaseTripOperation.create(tripId, operationData);
            
            SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                locallyClaimedTrips.remove(tripId);
                Log.i(TAG, "✅ Trip release initiated via legacy: " + tripId + " - " + result.message + " (removed from local tracking)");
                return true;
            } else {
                Log.w(TAG, "⚠️ Legacy trip release failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in legacy release operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Convenient method: Claim and start a trip in one call
     * 
     * This combines the most common workflow - claiming a trip and immediately
     * starting work on it.
     * 
     * @param tripId The trip to claim and start
     * @return true if both operations initiated successfully
     */
    public boolean claimAndStartTrip(String tripId) {
        Log.i(TAG, "Claim and start trip: " + tripId);
        
        boolean claimed = claimTrip(tripId);
        if (claimed) {
            // Small delay to let local state update
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            boolean started = startTrip(tripId);
            if (started) {
                Log.i(TAG, "Successfully claimed and started trip: " + tripId);
                return true;
            } else {
                // If start failed, try to release the claim
                Log.w(TAG, "Start failed, releasing claim for trip: " + tripId);
                releaseTrip(tripId, "Start failed after claim");
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Update trip status/metadata
     * 
     * Use this to update trip metadata without changing its core state.
     * Good for progress updates, notes, etc.
     * 
     * @param tripId The trip to update
     * @param status The new status
     * @param data Additional data to include
     * @return true if operation initiated successfully
     */
    public boolean updateTripStatus(String tripId, String status, JSONObject data) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot update trip status - invalid trip ID");
            return false;
        }
        
        Log.i(TAG, "Updating trip status: " + tripId + " -> " + status);
        
        try {
            JSONObject operationData = new JSONObject();
            operationData.put("tripId", tripId);
            operationData.put("deviceId", deviceId);
            operationData.put("action", "update_status");
            operationData.put("status", status);
            if (data != null) {
                operationData.put("data", data);
            }
            
            SyncOperation operation = UpdateTripStatusOperation.create(tripId, status, data);
            
            SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                Log.i(TAG, "Trip status update initiated: " + tripId);
                return true;
            } else {
                Log.w(TAG, "Trip status update failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating status update operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Handle delivery completion within a trip context
     * 
     * This method ensures that when a delivery is completed within a trip,
     * all necessary sync operations are triggered automatically.
     * 
     * @param tripId The trip containing the completed delivery
     * @param document The completed document/delivery ID
     * @return true if sync operations were queued successfully
     */
    public boolean handleDeliveryCompletion(String tripId, String document) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot handle delivery completion - invalid trip ID");
            return false;
        }
        
        if (document == null || document.trim().isEmpty()) {
            Log.w(TAG, "Cannot handle delivery completion - invalid document ID");
            return false;
        }
        
        Log.i(TAG, "Handling delivery completion: " + document + " (Trip: " + tripId + ")");
        
        try {
            // Queue delivery data sync operation
            com.clone.EasyDelivery.Utility.operations.SyncDeliveryDataOperation deliverySync = 
                com.clone.EasyDelivery.Utility.operations.SyncDeliveryDataOperation.create(tripId, document);
            
            if (deliverySync != null) {
                SyncResult deliveryResult = syncManager.executeOperation(deliverySync);
                if (!deliveryResult.success) {
                    Log.w(TAG, "Failed to queue delivery data sync: " + deliveryResult.message);
                    return false;
                }
                Log.d(TAG, "Queued delivery data sync for: " + document);
            } else {
                Log.e(TAG, "Failed to create delivery sync operation for: " + document);
                return false;
            }
            
            // Check if this delivery needs email sending
            if (shouldSendEmailForDelivery(tripId, document)) {
                com.clone.EasyDelivery.Utility.operations.SendEmailOperation emailOp = 
                    com.clone.EasyDelivery.Utility.operations.SendEmailOperation.create(tripId, document);
                
                if (emailOp != null) {
                    SyncResult emailResult = syncManager.executeOperation(emailOp);
                    if (!emailResult.success) {
                        Log.w(TAG, "Failed to queue email operation: " + emailResult.message);
                        // Don't return false - email is secondary to data sync
                    } else {
                        Log.d(TAG, "Queued email operation for: " + document);
                    }
                } else {
                    Log.w(TAG, "Failed to create email operation for: " + document);
                }
            }
            
            // Update trip metadata to reflect the delivery completion
            JSONObject deliveryData = new JSONObject();
            deliveryData.put("completed_delivery", document);
            deliveryData.put("completion_timestamp", System.currentTimeMillis());
            updateTripStatus(tripId, "delivery_completed", deliveryData);
            
            Log.i(TAG, "Successfully handled delivery completion: " + document + " (Trip: " + tripId + ")");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling delivery completion: " + document + " (Trip: " + tripId + ")", e);
            return false;
        }
    }
    
    /**
     * Check if email should be sent for a completed delivery
     */
    private boolean shouldSendEmailForDelivery(String tripId, String document) {
        try {
            com.clone.EasyDelivery.Database.DeliveryDb database = new com.clone.EasyDelivery.Database.DeliveryDb(context);
            database.open();
            
            try {
                // Check if this delivery has unsent email status
                java.util.List<com.clone.EasyDelivery.Model.Delivery> unsentEmails = database.getAllUnsentEmails();
                for (com.clone.EasyDelivery.Model.Delivery delivery : unsentEmails) {
                    if (document.equals(delivery.getDocument()) && tripId.equals(delivery.getTripId())) {
                        Log.d(TAG, "Delivery " + document + " requires email sending");
                        return true;
                    }
                }
                
                Log.d(TAG, "Delivery " + document + " does not require email sending");
                return false;
                
            } finally {
                database.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking email requirement for delivery: " + document, e);
            // Default to sending email if we can't determine status
            return true;
        }
    }
    
    /**
     * Get trip sync status for UI display
     * 
     * This provides detailed information about the sync state of a specific trip,
     * useful for showing progress indicators in the UI.
     * 
     * @param tripId The trip to check sync status for
     * @return TripSyncStatus object containing sync state information
     */
    public TripSyncStatus getTripSyncStatus(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return new TripSyncStatus(tripId, false, "Invalid trip ID", 0, 0);
        }
        
        try {
            // Check if trip has pending sync operations
            OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
            int pendingOperations = queue.getPendingOperationsCount(tripId);
            
            // Check database sync status
            com.clone.EasyDelivery.Database.DeliveryDb database = new com.clone.EasyDelivery.Database.DeliveryDb(context);
            database.open();
            
            try {
                boolean isDataSynced = database.isDataSynced(tripId);
                int totalDocuments = database.getCompletedDocumentList(tripId).size();
                int syncedDocuments = database.getSyncedDocumentCount(tripId);
                
                String statusMessage;
                if (pendingOperations > 0) {
                    statusMessage = pendingOperations + " operations pending";
                } else if (isDataSynced) {
                    statusMessage = "All data synced";
                } else {
                    statusMessage = "Sync incomplete";
                }
                
                return new TripSyncStatus(tripId, isDataSynced && pendingOperations == 0, 
                                        statusMessage, syncedDocuments, totalDocuments);
                
            } finally {
                database.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting trip sync status for: " + tripId, e);
            return new TripSyncStatus(tripId, false, "Error checking status: " + e.getMessage(), 0, 0);
        }
    }
    
    /**
     * Clean up completed trips from local storage
     * 
     * This method removes completed and fully synced trips from local storage
     * to prevent them from reappearing in the dashboard.
     * 
     * @param tripId The trip to clean up
     * @return true if cleanup was successful
     */
    public boolean cleanupCompletedTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot cleanup trip - invalid trip ID");
            return false;
        }
        
        try {
            // Check if trip is fully synced before cleanup
            TripSyncStatus status = getTripSyncStatus(tripId);
            if (!status.isFullySynced()) {
                Log.w(TAG, "Cannot cleanup trip " + tripId + " - not fully synced: " + status.getStatusMessage());
                return false;
            }
            
            Log.i(TAG, "Cleaning up completed trip: " + tripId);
            
            // Remove from completed trips list
            AppConstant.completedTrips.remove(tripId);
            
            // Remove from local tracking
            locallyClaimedTrips.remove(tripId);
            
            // Delete local trip file
            File tripFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json");
            if (tripFile.exists()) {
                boolean deleted = tripFile.delete();
                if (deleted) {
                    Log.i(TAG, "Deleted local trip file: " + tripFile.getAbsolutePath());
                } else {
                    Log.w(TAG, "Failed to delete local trip file: " + tripFile.getAbsolutePath());
                }
            }
            
            // Delete temporary files
            File tempFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json.tmp");
            if (tempFile.exists()) {
                tempFile.delete();
            }
            
            // Clean up database records
            com.clone.EasyDelivery.Database.DeliveryDb database = new com.clone.EasyDelivery.Database.DeliveryDb(context);
            database.open();
            try {
                database.deleteUploadedData(tripId);
                Log.i(TAG, "Deleted uploaded data records for trip: " + tripId);
            } finally {
                database.close();
            }
            
            Log.i(TAG, "Successfully cleaned up completed trip: " + tripId);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up completed trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Get current sync status - useful for UI indicators
     */
    public ConnectivityAwareSyncManager.SyncStatus getSyncStatus() {
        return syncManager.getSyncStatus();
    }
    
    /**
     * Force immediate sync of all pending operations
     */
    public void forceSync() {
        Log.i(TAG, "Force sync requested");
        syncManager.forceSync();
    }
    
    /**
     * Get system statistics for debugging
     */
    public String getSystemStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Unified Trip Manager Status:\n");
        stats.append("Device ID: ").append(deviceId).append("\n\n");
        stats.append(syncManager.getSyncStatistics()).append("\n");
        
        OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
        stats.append(queue.getQueueStatistics());
        
        return stats.toString();
    }
    
    /**
     * Get the device ID used for operations
     */
    public String getDeviceId() {
        return deviceId;
    }
    
    /**
     * Release a trip (overloaded method without reason)
     */
    public boolean releaseTrip(String tripId) {
        return releaseTrip(tripId, "Manual release");
    }
    
    
    /**
     * Manual cleanup - triggers immediate device-aware cleanup
     * Can be called by UI components if needed
     */
    public void forceCleanupStuckTrips() {
        Log.i(TAG, "Manual cleanup requested");
        performPeriodicCleanup();
    }
    
    
    /**
     * Get list of available trips for dashboard
     * 🗄️ OFFLINE-FIRST: Prioritizes cached trips, then adds cloud trips when online
     */
    public java.util.List<String> getAvailableTrips() {
        java.util.List<String> availableTrips = new java.util.ArrayList<>();
        
        try {
            // 🗄️ OFFLINE-FIRST: Start with cached trips from TripCacheManager
            TripCacheManager cacheManager = TripCacheManager.getInstance(context);
            java.util.List<TripCacheManager.TripCacheEntry> cachedTrips = cacheManager.getCachedTrips();
            
            // Add all cached trips immediately (works offline)
            for (TripCacheManager.TripCacheEntry entry : cachedTrips) {
                if (!AppConstant.completedTrips.contains(entry.tripId)) {
                    availableTrips.add(entry.tripId);
                    Log.v(TAG, "Cached trip: " + entry.tripId + " (" + entry.syncStatus + ")");
                }
            }
            
            Log.i(TAG, "Found " + availableTrips.size() + " cached trips");
            
            // 🌐 ONLINE: Add additional trips from Dropbox when connected
            ConnectivityAwareSyncManager syncManager = ConnectivityAwareSyncManager.getInstance(context);
            boolean isOnline = syncManager.isOnline();
            
            if (isOnline) {
                java.util.List<String> cloudTrips = getLocalTripsDirectly(); // This queries Dropbox
                
                // 🔧 STALE TRIP CLEANUP: Remove cached trips that no longer exist on Dropbox
                Log.i(TAG, "🔍 CLEANUP CHECK: Cached trips: " + availableTrips + ", Dropbox trips: " + cloudTrips);
                int removedCount = 0;
                java.util.Iterator<String> iterator = availableTrips.iterator();
                while (iterator.hasNext()) {
                    String cachedTrip = iterator.next();
                    if (!cloudTrips.contains(cachedTrip) && !locallyClaimedTrips.contains(cachedTrip)) {
                        Log.i(TAG, "🗑️ CLEANUP: Removing stale cached trip: " + cachedTrip + " (not in Dropbox)");
                        iterator.remove();
                        // Also remove from cache manager
                        cacheManager.removeTripFromCache(cachedTrip);
                        removedCount++;
                    }
                }
                Log.i(TAG, "🗑️ CLEANUP RESULT: Removed " + removedCount + " stale trips from cache");
                
                for (String tripId : cloudTrips) {
                    if (!availableTrips.contains(tripId) && !AppConstant.completedTrips.contains(tripId)) {
                        availableTrips.add(tripId);
                        Log.d(TAG, "Added cloud trip: " + tripId);
                        
                        // Store in cache for offline access
                        if (!cacheManager.isTripCached(tripId)) {
                            // Trigger background download and caching
                            downloadTripFromDropboxAsync(tripId);
                        }
                    }
                }
                
                Log.i(TAG, "Total " + availableTrips.size() + " trips (" + cloudTrips.size() + " from cloud)");
            } else {
                Log.i(TAG, "Using " + availableTrips.size() + " cached trips only");
            }
            
            // Add locally tracked claimed trips so they stay visible
            for (String claimedTrip : locallyClaimedTrips) {
                if (!availableTrips.contains(claimedTrip)) {
                    availableTrips.add(claimedTrip);
                    Log.d(TAG, "Added locally tracked trip to dashboard: " + claimedTrip);
                }
            }
            
            java.util.Collections.sort(availableTrips);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting available trips", e);
        }
        
        return availableTrips;
    }
    
    /**
     * 🗄️ Get cached trips only (pure offline access)
     * This method works entirely offline using locally cached trip data
     */
    public java.util.List<String> getCachedTrips() {
        java.util.List<String> cachedTrips = new java.util.ArrayList<>();
        
        try {
            TripCacheManager cacheManager = TripCacheManager.getInstance(context);
            java.util.List<TripCacheManager.TripCacheEntry> entries = cacheManager.getCachedTrips();
            
            for (TripCacheManager.TripCacheEntry entry : entries) {
                if (!AppConstant.completedTrips.contains(entry.tripId)) {
                    cachedTrips.add(entry.tripId);
                }
            }
            
            // Add locally tracked claimed trips
            for (String claimedTrip : locallyClaimedTrips) {
                if (!cachedTrips.contains(claimedTrip)) {
                    cachedTrips.add(claimedTrip);
                }
            }
            
            java.util.Collections.sort(cachedTrips);
            
            Log.i(TAG, "Retrieved " + cachedTrips.size() + " cached trips (offline-capable)");
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached trips", e);
        }
        
        return cachedTrips;
    }
    
    /**
     * Get trips from Dropbox available folder directly
     * This ensures real-time synchronization between devices
     * 🔧 FIXED: Prevents race conditions that cause trips to pop in/out
     */
    private java.util.List<String> getLocalTripsDirectly() {
        java.util.List<String> availableTrips = new java.util.ArrayList<>();
        
        try {
            // Check what's actually available on Dropbox, not just local files
            java.util.List<String> dropboxAvailableTrips = getTripsFromDropboxAvailableFolder();
            
            // 🔧 RACE CONDITION FIX: Always add trips that exist on Dropbox
            // Don't make trip visibility dependent on local file download completion
            for (String tripId : dropboxAvailableTrips) {
                if (!AppConstant.completedTrips.contains(tripId)) {
                    // ✅ ALWAYS ADD: If trip exists on Dropbox, show it in dashboard
                    availableTrips.add(tripId);
                    
                    // Check local file status for background optimization only
                    File tripFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json");
                    
                    if (!tripFile.exists() || tripFile.length() == 0) {
                        // Download in background - doesn't affect visibility
                        downloadTripFromDropboxAsync(tripId);
                    }
                }
            }
            
            java.util.Collections.sort(availableTrips);
            Log.i(TAG, "Found " + availableTrips.size() + " trips from Dropbox");
            
        } catch (Exception e) {
            Log.w(TAG, "Error getting trips from Dropbox, using local fallback: " + e.getMessage());
            // Fallback to local files if Dropbox is unavailable
            return getLocalTripsDirectlyFallback();
        }
        
        return availableTrips;
    }
    
    /**
     * Get list of trips currently in Dropbox available folder
     */
    private java.util.List<String> getTripsFromDropboxAvailableFolder() {
        java.util.List<String> dropboxTrips = new java.util.ArrayList<>();
        
        try {
            com.dropbox.core.v2.DbxClientV2 client = DropboxHelper.getClient(context);
            if (client == null) {
                Log.w(TAG, "Dropbox client not available for checking available trips");
                return dropboxTrips;
            }
            
            String availablePath = "/Customers/" + AppConstant.COMPANY + "/available";
            com.dropbox.core.v2.files.ListFolderResult availableFiles = client.files().listFolder(availablePath);
            
            if (availableFiles != null && !availableFiles.getEntries().isEmpty()) {
                for (int i = 0; i < availableFiles.getEntries().size(); i++) {
                    String fileName = availableFiles.getEntries().get(i).getName();
                    if (fileName.endsWith(".json")) {
                        String tripId = fileName.substring(0, fileName.length() - 5);
                        dropboxTrips.add(tripId);
                    }
                }
            }
            
            Log.d(TAG, "Found " + dropboxTrips.size() + " trips in Dropbox available folder");
            
        } catch (Exception e) {
            Log.e(TAG, "Error listing Dropbox available folder", e);
        }
        
        return dropboxTrips;
    }
    
    // 🔧 SYNCHRONIZED DOWNLOAD MANAGER: Prevents race conditions and duplicate downloads
    private static final java.util.Set<String> activeDownloads = new java.util.HashSet<>();
    private static final Object downloadLock = new Object();
    
    /**
     * Download trip file from Dropbox with race condition protection
     * 🔧 ENHANCED: Prevents duplicate downloads and stores in TripCacheManager
     */
    private void downloadTripFromDropboxAsync(final String tripId) {
        synchronized (downloadLock) {
            // Check if download is already in progress
            if (activeDownloads.contains(tripId)) {
                Log.i(TAG, "Download already in progress for: " + tripId);
                return;
            }
            
            // Check if already cached
            TripCacheManager cacheManager = TripCacheManager.getInstance(context);
            if (cacheManager.isTripCached(tripId)) {
                Log.i(TAG, "Trip already cached: " + tripId);
                return;
            }
            
            // Mark download as active
            activeDownloads.add(tripId);
            Log.i(TAG, "Starting protected download for: " + tripId);
        }
        
        Thread downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean downloadSucceeded = false;
                String downloadedContent = null;
                
                try {
                    // Update sync status to "syncing"
                    TripCacheManager cacheManager = TripCacheManager.getInstance(context);
                    cacheManager.updateTripSyncStatus(tripId, TripCacheManager.STATUS_SYNCING);
                    
                    // Download from Dropbox
                    DropboxHelper.downloadFileWithLocking(context, tripId + ".json");
                    
                    // 🔧 VERIFICATION: Check if file was actually created
                    File downloadedFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json");
                    if (downloadedFile.exists() && downloadedFile.length() > 0) {
                        // Read the downloaded content
                        downloadedContent = JsonHandler.readFileAsString(context, tripId);
                        
                        if (downloadedContent != null && !downloadedContent.trim().isEmpty()) {
                            // Store in cache manager for offline access
                            boolean cached = cacheManager.storeTripInCache(tripId, downloadedContent);
                            
                            if (cached) {
                                cacheManager.updateTripSyncStatus(tripId, TripCacheManager.STATUS_SYNCED);
                                downloadSucceeded = true;
                                Log.i(TAG, "🔄 SYNC_DOWNLOAD: Successfully downloaded and cached: " + tripId + " (" + downloadedFile.length() + " bytes)");
                            } else {
                                cacheManager.updateTripSyncStatus(tripId, TripCacheManager.STATUS_ERROR);
                                Log.e(TAG, "🔄 SYNC_DOWNLOAD: Failed to cache downloaded trip: " + tripId);
                            }
                        } else {
                            cacheManager.updateTripSyncStatus(tripId, TripCacheManager.STATUS_ERROR);
                            Log.e(TAG, "🔄 SYNC_DOWNLOAD: Downloaded file has no content: " + tripId);
                        }
                    } else {
                        Log.e(TAG, "🔄 SYNC_DOWNLOAD: Download claimed success but file missing: " + tripId);
                    }
                    
                } catch (Exception e) {
                    Log.w(TAG, "🔄 SYNC_DOWNLOAD: Download failed for " + tripId + ": " + e.getMessage());
                    
                    // Update sync status to error
                    try {
                        TripCacheManager cacheManager = TripCacheManager.getInstance(context);
                        cacheManager.updateTripSyncStatus(tripId, TripCacheManager.STATUS_ERROR);
                    } catch (Exception cacheError) {
                        Log.e(TAG, "Failed to update cache status after download error", cacheError);
                    }
                    
                    downloadSucceeded = false;
                } finally {
                    // Always remove from active downloads
                    synchronized (downloadLock) {
                        activeDownloads.remove(tripId);
                        Log.i(TAG, "🔄 SYNC_DOWNLOAD: Released download lock for: " + tripId);
                    }
                }
            }
        });
        downloadThread.start();
    }
    
    /**
     * Fallback method - get trips from local file system only
     * 🔧 FIXED: Improved stability for offline scenarios
     */
    private java.util.List<String> getLocalTripsDirectlyFallback() {
        java.util.List<String> localTrips = new java.util.ArrayList<>();
        
        try {
            File tripDir = new File(context.getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                tripDir.mkdirs();
                return localTrips;
            }
            
            String[] tripFiles = tripDir.list();
            if (tripFiles == null) {
                return localTrips;
            }
            
            // 🔧 STABILITY: More lenient file validation to prevent trips from disappearing
            for (String fileName : tripFiles) {
                if (fileName.endsWith(".json") && !fileName.endsWith(".tmp")) {
                    String tripName = fileName.substring(0, fileName.length() - 5);
                    File currentFile = new File(tripDir, fileName);
                    
                    // 🔧 IMPROVED: Only exclude if file is completely empty or trip is definitely completed
                    boolean isFileValid = currentFile.exists() && currentFile.length() >= 0; // Accept even small files
                    boolean isTripCompleted = AppConstant.completedTrips.contains(tripName);
                    
                    if (isFileValid && !isTripCompleted) {
                        localTrips.add(tripName);
                    }
                }
            }
            
            java.util.Collections.sort(localTrips);
            Log.i(TAG, "Found " + localTrips.size() + " local trips (fallback)");
            
        } catch (Exception e) {
            Log.e(TAG, "🔄 STABLE_FALLBACK: Error getting local trips, returning empty list", e);
        }
        
        return localTrips;
    }
    
    // ================== PERSISTENCE & CLEANUP HELPERS ==================
    
    /**
     * Get in-progress trips claimed by this device from Dropbox
     * Used by both persistence logic and cleanup logic
     */
    private java.util.List<String> getInProgressTripsByDevice() {
        java.util.List<String> inProgressTrips = new java.util.ArrayList<>();
        
        try {
            com.dropbox.core.v2.DbxClientV2 client = DropboxHelper.getClient(context);
            if (client == null) {
                return inProgressTrips;
            }
            
            String inProgressPath = "/Customers/" + AppConstant.COMPANY + "/in_progress";
            
            com.dropbox.core.v2.files.ListFolderResult inProgressFiles = client.files().listFolder(inProgressPath);
            
            if (inProgressFiles != null && !inProgressFiles.getEntries().isEmpty()) {
                for (int i = 0; i < inProgressFiles.getEntries().size(); i++) {
                    String fileName = inProgressFiles.getEntries().get(i).getName();
                    
                    DropboxHelper.ClaimInfo claimInfo = DropboxHelper.parseClaimInfo(fileName);
                    
                    if (claimInfo.isValidClaim && deviceId.equals(claimInfo.deviceId)) {
                        inProgressTrips.add(claimInfo.tripId);
                        Log.d(TAG, "Found in-progress trip by this device: " + claimInfo.tripId);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting in-progress trips by device", e);
        }
        
        return inProgressTrips;
    }
    
    /**
     * Device-aware periodic cleanup of stuck in-progress trips
     * Only releases trips claimed by this device when no active trip exists
     * Called by SyncService periodic operations
     */
    public void performPeriodicCleanup() {
        Thread cleanupThread = new Thread(() -> {
            try {
                Log.d(TAG, "Performing periodic cleanup check...");
                
                // Find trips stuck in Dropbox in_progress folder by this device
                java.util.List<String> dropboxInProgressTrips = getInProgressTripsByDevice();
                
                if (dropboxInProgressTrips.isEmpty()) {
                    Log.d(TAG, "No in_progress trips found by this device - cleanup complete");
                    return;
                }
                
                // Simple logic: If no active trip, release all in_progress trips by this device
                String currentTripId = AppConstant.TRIPID;
                boolean hasActiveTrip = (currentTripId != null && !currentTripId.isEmpty());
                
                if (!hasActiveTrip) {
                    Log.w(TAG, "CLEANUP: No active trip - releasing " + dropboxInProgressTrips.size() + " stuck trips");
                    
                    for (String stuckTrip : dropboxInProgressTrips) {
                        Log.i(TAG, "Releasing stuck trip: " + stuckTrip);
                        releaseTrip(stuckTrip, "Periodic cleanup: no active trip");
                    }
                } else {
                    Log.d(TAG, "Active trip detected: " + currentTripId + " - no cleanup needed");
                    
                    // Ensure the active trip is in local tracking
                    if (dropboxInProgressTrips.contains(currentTripId)) {
                        locallyClaimedTrips.add(currentTripId);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in periodic cleanup", e);
            }
        });
        cleanupThread.start();
    }
    
    /**
     * 🗑️ Force sync trip removals from Dropbox
     * Manually triggers trip removal sync to immediately remove trips
     * that are no longer available on Dropbox
     */
    public void forceSyncTripRemovals() {
        try {
            Log.i(TAG, "Forcing trip removal sync...");
            
            ConnectivityAwareSyncManager syncManager = ConnectivityAwareSyncManager.getInstance(context);
            syncManager.forceTripRemovalSync();
            
        } catch (Exception e) {
            Log.e(TAG, "Error during force trip removal sync", e);
        }
    }
    
    
    private String generateDeviceId() {
        try {
            String androidId = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
            );
            
            String driverName = AppConstant.DRIVER != null ? 
                AppConstant.DRIVER.replaceAll("[^A-Za-z0-9]", "") : "Driver";
            
            StringBuilder composite = new StringBuilder();
            composite.append(driverName).append("_");
            
            if (androidId != null && androidId.length() > 4) {
                composite.append(androidId.substring(0, Math.min(8, androidId.length())));
            } else {
                composite.append("DEV");
            }
            
            if (android.os.Build.MODEL != null) {
                int modelHash = Math.abs(android.os.Build.MODEL.hashCode() % 10000);
                composite.append("_").append(modelHash);
            }
            
            return composite.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating device ID, using fallback", e);
            return "Device_FALLBACK_" + System.currentTimeMillis();
        }
    }
}
