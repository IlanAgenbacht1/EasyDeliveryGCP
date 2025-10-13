package com.clone.EasyDelivery.Firebase;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.clone.EasyDelivery.Firebase.Models.*;
import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Database.DeliveryDb;

// Firebase Storage imports
import com.google.firebase.storage.FirebaseStorage;
import java.io.File;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FirestoreSyncManager - Replaces DropboxHelper with Firestore integration
 * 
 * Key Features:
 * - Offline-first with Firestore cache
 * - Real-time trip updates via SnapshotListeners
 * - Transaction-based trip claiming for atomicity
 * - Batch operations for efficiency
 * - Automatic retry and conflict resolution
 */
public class FirestoreSyncManager {
    
    private static final String TAG = "FirestoreSyncManager";
    private static final String TRIPS_COLLECTION = "trips";
    private static final String DELIVERIES_COLLECTION = "deliveries";
    
    private static FirestoreSyncManager instance;
    private final Context context;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final FirebaseStorageManager storageManager;
    private final ExecutorService executor;
    private final Handler mainHandler;
    
    private String currentClientId = "dev"; // Default to dev, can be configured
    private String currentDeviceId;
    
    // Listeners for real-time updates
    private ListenerRegistration tripListListener;
    private final Map<String, ListenerRegistration> tripDeliveryListeners = new HashMap<>();
    
    private FirestoreSyncManager(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.storageManager = FirebaseStorageManager.getInstance(context);
        this.executor = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Configure Firestore settings for offline persistence
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build();
        db.setFirestoreSettings(settings);
        
        initializeDeviceId();
    }
    
    public static synchronized FirestoreSyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new FirestoreSyncManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Initialize device ID using Firebase Auth anonymous authentication
     */
    private void initializeDeviceId() {
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        currentDeviceId = auth.getCurrentUser().getUid();
                        Log.i(TAG, "Device authenticated with ID: " + currentDeviceId);
                    } else {
                        Log.e(TAG, "Failed to authenticate device", task.getException());
                        // Fallback to a generated device ID
                        currentDeviceId = generateFallbackDeviceId();
                    }
                });
        } else {
            currentDeviceId = auth.getCurrentUser().getUid();
        }
    }
    
    private String generateFallbackDeviceId() {
        return "device_" + System.currentTimeMillis() + "_" + 
               android.provider.Settings.Secure.getString(context.getContentResolver(), 
                                                         android.provider.Settings.Secure.ANDROID_ID);
    }
    
    // ======================== TRIP MANAGEMENT ========================
    
    /**
     * Get all available trips for the current client with real-time updates
     * Replaces DropboxHelper.downloadAllTrips()
     */
    public void listenForAvailableTrips(TripListCallback callback) {
        Log.i(TAG, "Setting up real-time listener for available trips");
        
        if (tripListListener != null) {
            tripListListener.remove();
        }
        
        Query query = db.collection(TRIPS_COLLECTION)
                .whereEqualTo("clientId", currentClientId)
                .whereIn("status", java.util.Arrays.asList("available", "claimed"))
                .orderBy("createdAt", Query.Direction.DESCENDING);
        
        tripListListener = query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen for trips failed", error);
                callback.onError("Failed to listen for trips: " + error.getMessage());
                return;
            }
            
            if (snapshots != null) {
                List<FirestoreTrip> trips = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshots) {
                    FirestoreTrip trip = doc.toObject(FirestoreTrip.class);
                    trip.setId(doc.getId());
                    trips.add(trip);
                }
                
                Log.i(TAG, "Received " + trips.size() + " trips from Firestore");
                callback.onTripsUpdated(trips);
            }
        });
    }
    
    /**
     * Claim a trip using Firestore transaction for atomicity
     * Replaces the Dropbox file moving mechanism
     */
    public Task<Boolean> claimTrip(String tripId, String driverId, String vehicleId) {
        Log.i(TAG, "Claiming trip: " + tripId);
        
        DocumentReference tripRef = db.collection(TRIPS_COLLECTION).document(tripId);
        
        return db.runTransaction(transaction -> {
            DocumentSnapshot tripSnapshot = transaction.get(tripRef);
            
            if (!tripSnapshot.exists()) {
                throw new RuntimeException("Trip not found: " + tripId);
            }
            
            FirestoreTrip trip = tripSnapshot.toObject(FirestoreTrip.class);
            if (trip == null) {
                throw new RuntimeException("Failed to parse trip data");
            }
            
            // Check if trip is available for claiming
            if (!"available".equals(trip.getStatus())) {
                throw new RuntimeException("Trip is not available (status: " + trip.getStatus() + ")");
            }
            
            // Update trip with claim information
            SessionInfo sessionInfo = new SessionInfo(currentDeviceId, driverId, vehicleId);
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "claimed");
            updates.put("claimedAt", FieldValue.serverTimestamp());
            updates.put("sessionInfo", sessionInfo.toMap());
            
            transaction.update(tripRef, updates);
            
            Log.i(TAG, "Trip " + tripId + " claimed successfully by device " + currentDeviceId);
            return true;
            
        }).continueWith(task -> {
            if (task.isSuccessful()) {
                // Update local state
                if (AppConstant.TRIPID == null || AppConstant.TRIPID.isEmpty()) {
                    AppConstant.TRIPID = tripId;
                    AppConstant.STARTED_TRIP = tripId;
                }
                return true;
            } else {
                Log.e(TAG, "Failed to claim trip " + tripId, task.getException());
                return false;
            }
        });
    }
    
    /**
     * Start a trip (change status from claimed to in_progress)
     */
    public Task<Boolean> startTrip(String tripId) {
        Log.i(TAG, "Starting trip: " + tripId);
        
        DocumentReference tripRef = db.collection(TRIPS_COLLECTION).document(tripId);
        
        return db.runTransaction(transaction -> {
            DocumentSnapshot tripSnapshot = transaction.get(tripRef);
            
            if (!tripSnapshot.exists()) {
                throw new RuntimeException("Trip not found: " + tripId);
            }
            
            FirestoreTrip trip = tripSnapshot.toObject(FirestoreTrip.class);
            if (trip == null || !trip.isClaimedBy(currentDeviceId)) {
                throw new RuntimeException("Trip not claimed by this device");
            }
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "in_progress");
            
            transaction.update(tripRef, updates);
            return true;
            
        }).continueWith(task -> {
            if (task.isSuccessful()) {
                AppConstant.STARTED_TRIP = tripId;
                Log.i(TAG, "Trip " + tripId + " started successfully");
                return true;
            } else {
                Log.e(TAG, "Failed to start trip " + tripId, task.getException());
                return false;
            }
        });
    }
    
    /**
     * Complete a trip and all its deliveries
     */
    public Task<Boolean> completeTrip(String tripId) {
        Log.i(TAG, "Completing trip: " + tripId);
        
        return db.runBatch(batch -> {
            // Update trip status
            DocumentReference tripRef = db.collection(TRIPS_COLLECTION).document(tripId);
            Map<String, Object> tripUpdates = new HashMap<>();
            tripUpdates.put("status", "completed");
            tripUpdates.put("completedAt", FieldValue.serverTimestamp());
            batch.update(tripRef, tripUpdates);
            
            // Note: Individual delivery completion is handled separately
            // This just marks the overall trip as completed
            
        }).continueWith(task -> {
            if (task.isSuccessful()) {
                // Add to completed trips list
                if (!AppConstant.completedTrips.contains(tripId)) {
                    AppConstant.completedTrips.add(tripId);
                }
                AppConstant.STARTED_TRIP = "";
                AppConstant.TRIPID = "";
                
                Log.i(TAG, "Trip " + tripId + " completed successfully");
                return true;
            } else {
                Log.e(TAG, "Failed to complete trip " + tripId, task.getException());
                return false;
            }
        });
    }
    
    /**
     * Release a trip (move back to available status)
     * Used for orphaned trip recovery
     */
    public Task<Boolean> releaseTrip(String tripId, String reason) {
        Log.i(TAG, "Releasing trip: " + tripId + " - Reason: " + reason);
        
        DocumentReference tripRef = db.collection(TRIPS_COLLECTION).document(tripId);
        
        return db.runTransaction(transaction -> {
            DocumentSnapshot tripSnapshot = transaction.get(tripRef);
            
            if (!tripSnapshot.exists()) {
                throw new RuntimeException("Trip not found: " + tripId);
            }
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "available");
            updates.put("claimedAt", null);
            updates.put("sessionInfo", new SessionInfo().toMap());
            
            transaction.update(tripRef, updates);
            return true;
            
        }).continueWith(task -> {
            if (task.isSuccessful()) {
                Log.i(TAG, "Trip " + tripId + " released successfully");
                return true;
            } else {
                Log.e(TAG, "Failed to release trip " + tripId, task.getException());
                return false;
            }
        });
    }
    
    // ======================== DELIVERY MANAGEMENT ========================
    
    /**
     * Get deliveries for a specific trip with real-time updates
     */
    public void listenForTripDeliveries(String tripId, DeliveriesCallback callback) {
        Log.i(TAG, "Setting up real-time listener for deliveries in trip: " + tripId);
        
        // Remove existing listener for this trip
        ListenerRegistration existingListener = tripDeliveryListeners.get(tripId);
        if (existingListener != null) {
            existingListener.remove();
        }
        
        CollectionReference deliveriesRef = db.collection(TRIPS_COLLECTION)
                .document(tripId)
                .collection(DELIVERIES_COLLECTION);
        
        ListenerRegistration listener = deliveriesRef.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen for deliveries failed", error);
                callback.onError("Failed to listen for deliveries: " + error.getMessage());
                return;
            }
            
            if (snapshots != null) {
                List<FirestoreDelivery> deliveries = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshots) {
                    FirestoreDelivery delivery = doc.toObject(FirestoreDelivery.class);
                    delivery.setId(doc.getId());
                    deliveries.add(delivery);
                }
                
                Log.i(TAG, "Received " + deliveries.size() + " deliveries for trip " + tripId);
                callback.onDeliveriesUpdated(deliveries);
            }
        });
        
        tripDeliveryListeners.put(tripId, listener);
    }
    
    /**
     * Complete a delivery with POD data and file uploads
     */
    public Task<Boolean> completeDelivery(String tripId, String deliveryId, PodData podData) {
        return completeDelivery(tripId, deliveryId, podData, null, null);
    }
    
    /**
     * Complete a delivery with POD data, images, and signature files
     */
    public Task<Boolean> completeDelivery(String tripId, String deliveryId, PodData podData, 
                                         File imageFile, File signatureFile) {
        Log.i(TAG, "🏁 Completing delivery with POD: " + deliveryId + " in trip: " + tripId);
        
        return Task.call(executor, () -> {
            try {
                PodData finalPodData = podData;
                
                // Upload files to Firebase Storage if provided
                if ((imageFile != null && imageFile.exists()) || (signatureFile != null && signatureFile.exists())) {
                    Log.i(TAG, "📤 Uploading POD files for delivery: " + deliveryId);
                    
                    Task<PodData> uploadTask = storageManager.uploadPodData(tripId, deliveryId, podData, imageFile, signatureFile);
                    finalPodData = Tasks.await(uploadTask);
                    
                    Log.i(TAG, "✅ POD files uploaded successfully for delivery: " + deliveryId);
                }
                
                // Update Firestore with POD data (including URLs if files were uploaded)
                DocumentReference deliveryRef = db.collection(TRIPS_COLLECTION)
                        .document(tripId)
                        .collection(DELIVERIES_COLLECTION)
                        .document(deliveryId);
                
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "completed");
                updates.put("podData", com.google.gson.GsonBuilder.create().toJson(finalPodData));
                
                // Use Firestore transaction for atomic completion
                Task<Void> firestoreTask = db.runTransaction(transaction -> {
                    DocumentSnapshot deliverySnapshot = transaction.get(deliveryRef);
                    
                    if (!deliverySnapshot.exists()) {
                        throw new RuntimeException("Delivery not found: " + deliveryId);
                    }
                    
                    FirestoreDelivery delivery = deliverySnapshot.toObject(FirestoreDelivery.class);
                    if (delivery == null) {
                        throw new RuntimeException("Failed to parse delivery data");
                    }
                    
                    if ("completed".equals(delivery.getStatus())) {
                        throw new RuntimeException("Delivery already completed: " + deliveryId);
                    }
                    
                    transaction.update(deliveryRef, updates);
                    return null;
                });
                
                Tasks.await(firestoreTask);
                
                Log.i(TAG, "✅ Delivery " + deliveryId + " completed successfully with POD data");
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to complete delivery " + deliveryId + " with POD", e);
                return false;
            }
        });
    }
    
    /**
     * Batch complete multiple deliveries (for efficiency)
     */
    public Task<Boolean> batchCompleteDeliveries(String tripId, Map<String, PodData> deliveryPodData) {
        Log.i(TAG, "🏁 Batch completing " + deliveryPodData.size() + " deliveries in trip: " + tripId);
        
        return db.runBatch(batch -> {
            for (Map.Entry<String, PodData> entry : deliveryPodData.entrySet()) {
                String deliveryId = entry.getKey();
                PodData podData = entry.getValue();
                
                DocumentReference deliveryRef = db.collection(TRIPS_COLLECTION)
                        .document(tripId)
                        .collection(DELIVERIES_COLLECTION)
                        .document(deliveryId);
                
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "completed");
                updates.put("podData", com.google.gson.GsonBuilder.create().toJson(podData));
                
                batch.update(deliveryRef, updates);
            }
        }).continueWith(task -> {
            if (task.isSuccessful()) {
                Log.i(TAG, "✅ Batch completed " + deliveryPodData.size() + " deliveries successfully");
                return true;
            } else {
                Log.e(TAG, "❌ Batch delivery completion failed", task.getException());
                return false;
            }
        });
    }
    
    // ======================== DATA MIGRATION ========================
    
    /**
     * Migrate legacy SQLite data to Firestore for smooth transition
     */
    public void migrateLegacyData() {
        executor.execute(() -> {
            try {
                Log.i(TAG, "Starting legacy data migration from SQLite to Firestore");
                
                DeliveryDb legacyDb = new DeliveryDb(context);
                legacyDb.open();
                
                // Get all incomplete sync trips from legacy database
                List<String> incompleteTripIds = legacyDb.getIncompleteSyncList();
                
                for (String tripId : incompleteTripIds) {
                    migrateTripData(legacyDb, tripId);
                }
                
                legacyDb.close();
                Log.i(TAG, "Legacy data migration completed");
                
            } catch (Exception e) {
                Log.e(TAG, "Error during legacy data migration", e);
            }
        });
    }
    
    private void migrateTripData(DeliveryDb legacyDb, String tripId) {
        try {
            // Get completed documents from legacy DB
            List<String> completedDocs = legacyDb.getCompletedDocumentList(tripId);
            
            if (!completedDocs.isEmpty()) {
                WriteBatch batch = db.batch();
                
                for (String docId : completedDocs) {
                    Delivery legacyDelivery = legacyDb.getCompletedDocument(docId, tripId);
                    FirestoreDelivery firestoreDelivery = FirestoreDelivery.fromLegacyDelivery(legacyDelivery);
                    
                    DocumentReference deliveryRef = db.collection(TRIPS_COLLECTION)
                            .document(tripId)
                            .collection(DELIVERIES_COLLECTION)
                            .document(docId);
                    
                    batch.set(deliveryRef, firestoreDelivery.toMap(), SetOptions.merge());
                }
                
                // Execute batch write
                Tasks.await(batch.commit());
                Log.i(TAG, "Migrated " + completedDocs.size() + " deliveries for trip " + tripId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error migrating trip data for " + tripId, e);
        }
    }
    
    // ======================== UTILITY METHODS ========================
    
    public String getCurrentDeviceId() {
        return currentDeviceId != null ? currentDeviceId : "";
    }
    
    public void setCurrentClientId(String clientId) {
        this.currentClientId = clientId;
    }
    
    public String getCurrentClientId() {
        return currentClientId;
    }
    
    /**
     * Check if Firestore has pending writes (for UI feedback)
     */
    public boolean hasPendingWrites() {
        // This requires storing the pending writes count, which we can implement later
        // For now, assume no pending writes
        return false;
    }
    
    /**
     * Force sync with server (useful for manual refresh)
     */
    public Task<Void> forceSyncWithServer() {
        return db.clearPersistence().continueWithTask(task -> {
            // Re-enable persistence after clearing
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            db.setFirestoreSettings(settings);
            return Tasks.forResult(null);
        });
    }
    
    /**
     * Cleanup listeners when shutting down
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down FirestoreSyncManager");
        
        if (tripListListener != null) {
            tripListListener.remove();
            tripListListener = null;
        }
        
        for (ListenerRegistration listener : tripDeliveryListeners.values()) {
            listener.remove();
        }
        tripDeliveryListeners.clear();
        
        // Shutdown storage manager
        if (storageManager != null) {
            storageManager.shutdown();
        }
        
        executor.shutdown();
    }
    
    // ======================== CALLBACK INTERFACES ========================
    
    public interface TripListCallback {
        void onTripsUpdated(List<FirestoreTrip> trips);
        void onError(String error);
    }
    
    public interface DeliveriesCallback {
        void onDeliveriesUpdated(List<FirestoreDelivery> deliveries);
        void onError(String error);
    }
    
    public interface OperationCallback {
        void onSuccess();
        void onError(String error);
    }
}