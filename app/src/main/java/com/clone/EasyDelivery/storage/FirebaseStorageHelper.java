package com.clone.EasyDelivery.storage;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Helper class for Firebase Storage operations
 * Handles photo and signature uploads for delivery proof
 */
public class FirebaseStorageHelper {
    
    private static final String TAG = "FirebaseStorageHelper";
    
    // Storage paths
    private static final String PHOTOS_PATH = "delivery-photos";
    private static final String SIGNATURES_PATH = "delivery-signatures";

    private FirebaseStorage storage;
    private StorageReference storageRef;
    
    public FirebaseStorageHelper() {
        try {
            // Check if Firebase is initialized
            if (com.google.firebase.FirebaseApp.getApps(null).isEmpty()) {
                Log.w(TAG, "Firebase not initialized. FirebaseStorageHelper will not function.");
                this.storage = null;
                this.storageRef = null;
                return;
            }

            this.storage = FirebaseStorage.getInstance();
            this.storageRef = storage.getReference();

            Log.i(TAG, "FirebaseStorageHelper initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize FirebaseStorageHelper: " + e.getMessage());
            this.storage = null;
            this.storageRef = null;
        }
    }
    
    /**
     * Check if Firebase Storage is available
     */
    private boolean isFirebaseAvailable() {
        if (storage == null || storageRef == null) {
            Log.w(TAG, "Firebase Storage not available. Please check Firebase configuration.");
            return false;
        }
        return true;
    }

    /**
     * Upload a delivery photo to Firebase Storage
     * @param deliveryId The delivery ID
     * @param photoFile The local photo file
     * @return CompletableFuture with the download URL
     */
    public CompletableFuture<String> uploadDeliveryPhoto(String deliveryId, File photoFile) {
        if (!isFirebaseAvailable()) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Firebase Storage is not available"));
            return future;
        }

        Log.i(TAG, "Uploading delivery photo for delivery: " + deliveryId);
        
        CompletableFuture<String> future = new CompletableFuture<>();
        
        if (photoFile == null || !photoFile.exists()) {
            future.completeExceptionally(new IllegalArgumentException("Photo file does not exist"));
            return future;
        }
        
        try {
            // Create unique filename
            String fileName = deliveryId + "_photo_" + System.currentTimeMillis() + ".jpg";
            StorageReference photoRef = storageRef.child(PHOTOS_PATH).child(fileName);
            
            // Upload file
            Uri photoUri = Uri.fromFile(photoFile);
            UploadTask uploadTask = photoRef.putFile(photoUri);
            
            uploadTask
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Photo upload successful: " + fileName);
                    
                    // Get download URL
                    photoRef.getDownloadUrl()
                        .addOnSuccessListener(downloadUri -> {
                            String downloadUrl = downloadUri.toString();
                            Log.i(TAG, "Photo download URL obtained: " + downloadUrl);
                            future.complete(downloadUrl);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get photo download URL", e);
                            future.completeExceptionally(e);
                        });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Photo upload failed: " + fileName, e);
                    future.completeExceptionally(e);
                })
                .addOnProgressListener(taskSnapshot -> {
                    double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                    Log.d(TAG, "Photo upload progress: " + (int) progress + "%");
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error starting photo upload", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Upload a delivery signature to Firebase Storage
     * @param deliveryId The delivery ID
     * @param signatureFile The local signature file
     * @return CompletableFuture with the download URL
     */
    public CompletableFuture<String> uploadDeliverySignature(String deliveryId, File signatureFile) {
        if (!isFirebaseAvailable()) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Firebase Storage is not available"));
            return future;
        }

        Log.i(TAG, "Uploading delivery signature for delivery: " + deliveryId);
        
        CompletableFuture<String> future = new CompletableFuture<>();
        
        if (signatureFile == null || !signatureFile.exists()) {
            future.completeExceptionally(new IllegalArgumentException("Signature file does not exist"));
            return future;
        }
        
        try {
            // Create unique filename
            String fileName = deliveryId + "_signature_" + System.currentTimeMillis() + ".png";
            StorageReference signatureRef = storageRef.child(SIGNATURES_PATH).child(fileName);
            
            // Upload file
            Uri signatureUri = Uri.fromFile(signatureFile);
            UploadTask uploadTask = signatureRef.putFile(signatureUri);
            
            uploadTask
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Signature upload successful: " + fileName);
                    
                    // Get download URL
                    signatureRef.getDownloadUrl()
                        .addOnSuccessListener(downloadUri -> {
                            String downloadUrl = downloadUri.toString();
                            Log.i(TAG, "Signature download URL obtained: " + downloadUrl);
                            future.complete(downloadUrl);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get signature download URL", e);
                            future.completeExceptionally(e);
                        });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Signature upload failed: " + fileName, e);
                    future.completeExceptionally(e);
                })
                .addOnProgressListener(taskSnapshot -> {
                    double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                    Log.d(TAG, "Signature upload progress: " + (int) progress + "%");
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error starting signature upload", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Upload both photo and signature for a delivery
     * @param deliveryId The delivery ID
     * @param photoFile The local photo file (can be null)
     * @param signatureFile The local signature file (can be null)
     * @return CompletableFuture with UploadResult containing both URLs
     */
    public CompletableFuture<UploadResult> uploadDeliveryProof(String deliveryId, File photoFile, File signatureFile) {
        if (!isFirebaseAvailable()) {
            CompletableFuture<UploadResult> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Firebase Storage is not available"));
            return future;
        }

        Log.i(TAG, "Uploading delivery proof for delivery: " + deliveryId);
        
        CompletableFuture<String> photoFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<String> signatureFuture = CompletableFuture.completedFuture(null);
        
        // Upload photo if provided
        if (photoFile != null && photoFile.exists()) {
            photoFuture = uploadDeliveryPhoto(deliveryId, photoFile);
        }
        
        // Upload signature if provided
        if (signatureFile != null && signatureFile.exists()) {
            signatureFuture = uploadDeliverySignature(deliveryId, signatureFile);
        }
        
        // Combine both futures
        return photoFuture.thenCombine(signatureFuture, (photoUrl, signatureUrl) -> {
            UploadResult result = new UploadResult(photoUrl, signatureUrl);
            Log.i(TAG, "Delivery proof upload completed: " + result);
            return result;
        });
    }
    
    /**
     * Result class for upload operations
     */
    public static class UploadResult {
        private final String photoUrl;
        private final String signatureUrl;
        
        public UploadResult(String photoUrl, String signatureUrl) {
            this.photoUrl = photoUrl;
            this.signatureUrl = signatureUrl;
        }
        
        public String getPhotoUrl() {
            return photoUrl;
        }
        
        public String getSignatureUrl() {
            return signatureUrl;
        }
        
        public boolean hasPhoto() {
            return photoUrl != null && !photoUrl.trim().isEmpty();
        }
        
        public boolean hasSignature() {
            return signatureUrl != null && !signatureUrl.trim().isEmpty();
        }
        
        @Override
        public String toString() {
            return "UploadResult{" +
                    "photoUrl='" + photoUrl + '\'' +
                    ", signatureUrl='" + signatureUrl + '\'' +
                    '}';
        }
    }
}