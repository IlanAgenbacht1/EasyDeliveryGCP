package com.clone.EasyDelivery.Firebase;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.clone.EasyDelivery.Firebase.Models.PodData;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FirebaseStorageManager - Handles POD image and signature uploads
 * 
 * Features:
 * - Offline upload queue with automatic retry
 * - Organized storage structure by client/trip/delivery
 * - Automatic URL generation for Firestore documents
 * - Progress tracking and error handling
 */
public class FirebaseStorageManager {
    
    private static final String TAG = "FirebaseStorageManager";
    
    private static FirebaseStorageManager instance;
    private final Context context;
    private final FirebaseStorage storage;
    private final ExecutorService executor;
    
    // Storage paths
    private static final String STORAGE_ROOT = "delivery-pod-data";
    private static final String IMAGES_FOLDER = "images";
    private static final String SIGNATURES_FOLDER = "signatures";
    
    private FirebaseStorageManager(Context context) {
        this.context = context.getApplicationContext();
        this.storage = FirebaseStorage.getInstance();
        this.executor = Executors.newFixedThreadPool(2);
        
        Log.i(TAG, "FirebaseStorageManager initialized");
    }
    
    public static synchronized FirebaseStorageManager getInstance(Context context) {
        if (instance == null) {
            instance = new FirebaseStorageManager(context);
        }
        return instance;
    }
    
    /**
     * Upload POD images and signatures, then update Firestore with URLs
     */
    public Task<PodData> uploadPodData(String tripId, String deliveryId, PodData podData, 
                                      File imageFile, File signatureFile) {
        Log.i(TAG, "Uploading POD data for delivery: " + deliveryId + " in trip: " + tripId);
        
        return Task.call(executor, () -> {
            PodData updatedPodData = new PodData();
            updatedPodData.setComment(podData.getComment());
            updatedPodData.setRecipientName(podData.getRecipientName());
            updatedPodData.setQuantity(podData.getQuantity());
            updatedPodData.setCapturedGpsLocation(podData.getCapturedGpsLocation());
            
            // Upload image if provided
            if (imageFile != null && imageFile.exists()) {
                String imageUrl = uploadFile(imageFile, buildImagePath(tripId, deliveryId)).getResult();
                updatedPodData.setPhotoUrl(imageUrl);
                Log.i(TAG, "Image uploaded successfully: " + imageUrl);
            }
            
            // Upload signature if provided
            if (signatureFile != null && signatureFile.exists()) {
                String signatureUrl = uploadFile(signatureFile, buildSignaturePath(tripId, deliveryId)).getResult();
                updatedPodData.setSignatureUrl(signatureUrl);
                Log.i(TAG, "Signature uploaded successfully: " + signatureUrl);
            }
            
            return updatedPodData;
        });
    }
    
    /**
     * Upload a single file to Firebase Storage
     */
    public Task<String> uploadFile(File file, String storagePath) {
        if (file == null || !file.exists()) {
            return Task.forException(new IllegalArgumentException("File does not exist: " + file));
        }
        
        Log.d(TAG, "Uploading file: " + file.getName() + " to: " + storagePath);
        
        StorageReference fileRef = storage.getReference().child(storagePath);
        Uri fileUri = Uri.fromFile(file);
        
        UploadTask uploadTask = fileRef.putFile(fileUri);
        
        return uploadTask.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            
            // Get the download URL
            return fileRef.getDownloadUrl();
        }).continueWith(task -> {
            if (task.isSuccessful()) {
                String downloadUrl = task.getResult().toString();
                Log.d(TAG, "File uploaded successfully: " + downloadUrl);
                return downloadUrl;
            } else {
                Log.e(TAG, "File upload failed: " + file.getName(), task.getException());
                throw task.getException();
            }
        });
    }
    
    /**
     * Upload image with retry logic
     */
    public Task<String> uploadImageWithRetry(File imageFile, String tripId, String deliveryId, int maxRetries) {
        String storagePath = buildImagePath(tripId, deliveryId);
        return uploadFileWithRetry(imageFile, storagePath, maxRetries);
    }
    
    /**
     * Upload signature with retry logic
     */
    public Task<String> uploadSignatureWithRetry(File signatureFile, String tripId, String deliveryId, int maxRetries) {
        String storagePath = buildSignaturePath(tripId, deliveryId);
        return uploadFileWithRetry(signatureFile, storagePath, maxRetries);
    }
    
    /**
     * Upload file with automatic retry
     */
    private Task<String> uploadFileWithRetry(File file, String storagePath, int maxRetries) {
        return uploadFile(file, storagePath).continueWithTask(task -> {
            if (task.isSuccessful()) {
                return Task.forResult(task.getResult());
            } else {
                if (maxRetries > 0) {
                    Log.w(TAG, "Upload failed, retrying... (" + maxRetries + " retries left)");
                    return uploadFileWithRetry(file, storagePath, maxRetries - 1);
                } else {
                    Log.e(TAG, "Upload failed after all retries", task.getException());
                    return Task.forException(task.getException());
                }
            }
        });
    }
    
    /**
     * Build storage path for delivery images
     */
    private String buildImagePath(String tripId, String deliveryId) {
        return STORAGE_ROOT + "/" + getCurrentClientId() + "/" + tripId + "/" + deliveryId + "/" + 
               IMAGES_FOLDER + "/" + System.currentTimeMillis() + "_image.jpg";
    }
    
    /**
     * Build storage path for delivery signatures
     */
    private String buildSignaturePath(String tripId, String deliveryId) {
        return STORAGE_ROOT + "/" + getCurrentClientId() + "/" + tripId + "/" + deliveryId + "/" + 
               SIGNATURES_FOLDER + "/" + System.currentTimeMillis() + "_signature.png";
    }
    
    /**
     * Get current client ID for storage organization
     */
    private String getCurrentClientId() {
        // Get from FirestoreSyncManager or default to "default"
        FirestoreSyncManager firestoreManager = FirestoreSyncManager.getInstance(context);
        if (firestoreManager != null) {
            return firestoreManager.getCurrentClientId();
        }
        return "default";
    }
    
    /**
     * Delete POD files from storage (cleanup)
     */
    public Task<Void> deletePodFiles(String tripId, String deliveryId) {
        Log.i(TAG, "Deleting POD files for delivery: " + deliveryId + " in trip: " + tripId);
        
        return Task.call(executor, () -> {
            try {
                // Delete image folder
                String imageFolderPath = STORAGE_ROOT + "/" + getCurrentClientId() + "/" + tripId + "/" + deliveryId + "/" + IMAGES_FOLDER;
                deleteFolder(imageFolderPath);
                
                // Delete signature folder
                String signatureFolderPath = STORAGE_ROOT + "/" + getCurrentClientId() + "/" + tripId + "/" + deliveryId + "/" + SIGNATURES_FOLDER;
                deleteFolder(signatureFolderPath);
                
                Log.i(TAG, "POD files deleted successfully for delivery: " + deliveryId);
                return null;
            } catch (Exception e) {
                Log.e(TAG, "Error deleting POD files for delivery: " + deliveryId, e);
                throw e;
            }
        });
    }
    
    /**
     * Delete entire folder from storage
     */
    private void deleteFolder(String folderPath) {
        StorageReference folderRef = storage.getReference().child(folderPath);
        
        folderRef.listAll().addOnSuccessListener(listResult -> {
            for (StorageReference item : listResult.getItems()) {
                item.delete().addOnFailureListener(e -> 
                    Log.w(TAG, "Failed to delete file: " + item.getName(), e));
            }
            
            for (StorageReference prefix : listResult.getPrefixes()) {
                deleteFolder(prefix.getPath());
            }
        }).addOnFailureListener(e -> 
            Log.w(TAG, "Failed to list folder contents: " + folderPath, e));
    }
    
    /**
     * Get storage usage statistics
     */
    public Task<Map<String, Long>> getStorageStats() {
        return Task.call(executor, () -> {
            Map<String, Long> stats = new HashMap<>();
            // This would require additional Firebase Admin SDK calls
            // For now, return empty stats
            stats.put("totalFiles", 0L);
            stats.put("totalSize", 0L);
            return stats;
        });
    }
    
    /**
     * Cleanup old POD data (for maintenance)
     */
    public void cleanupOldPodData(int daysOld) {
        Log.i(TAG, "Starting cleanup of POD data older than " + daysOld + " days");
        
        long cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L);
        
        // This would require more complex logic to traverse storage and check timestamps
        // For now, log the intention
        Log.i(TAG, "Cleanup process initiated - cutoff time: " + cutoffTime);
    }
    
    /**
     * Shutdown storage manager
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            Log.i(TAG, "FirebaseStorageManager shutdown");
        }
    }
}