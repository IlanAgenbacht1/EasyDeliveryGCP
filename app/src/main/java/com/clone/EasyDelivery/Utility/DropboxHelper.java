package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

/**
 * @deprecated This class is deprecated and replaced by FirestoreSyncManager.
 * 
 * DropboxHelper is no longer used in the application. All trip management,
 * file storage, and synchronization is now handled by Firebase/Firestore.
 * 
 * Migration path:
 * - Trip management: Use FirestoreSyncManager
 * - File storage: Use FirebaseStorageManager  
 * - Sync operations: Native Firestore offline persistence
 * 
 * This class is kept temporarily for reference during migration.
 * It will be removed in a future version.
 */
@Deprecated

import com.clone.EasyDelivery.BuildConfig;
import com.clone.EasyDelivery.Database.DeliveryDb;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DownloadErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.WriteMode;
import com.clone.EasyDelivery.Security.AuditLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;

public class DropboxHelper {

    private static final String TAG = "DropboxHelper";
    private static final String CLIENT_PATH = "dropbox/";
    private static final String LOCAL_IMAGE_PATH = "/DeliveryApp/DeliveryImage/";
    private static final String LOCAL_SIGNATURE_PATH = "/DeliveryApp/DeliverySignature/";
    
    /**
     * Get the dynamic customer path based on current COMPANY value
     * This must be called dynamically to use the current AppConstant.COMPANY value
     */
    private static String getCustomerPath() {
        if (AppConstant.COMPANY == null || AppConstant.COMPANY.trim().isEmpty()) {
            Log.e(TAG, "COMPANY not set - using 'DEFAULT' as fallback");
            return "/Customers/DEFAULT/";
        }
        return "/Customers/" + AppConstant.COMPANY.trim() + "/";
    }
    
    // Regex pattern for parsing claimed trip filenames safely
    private static final Pattern CLAIM_PATTERN = 
        Pattern.compile("^([A-Za-z0-9_-]+)_CLAIMED_BY_([A-Za-z0-9_-]+)_AT_([0-9]+)\\.json$");

    private static DbxClientV2 dropboxClient;
    private static SecurityManager securityManager;
    
    /**
     * Immutable class to hold parsed claim information
     * Made public for orphaned trip recovery functionality
     */
    public static class ClaimInfo {
        final String tripId;
        final String deviceId;
        final long timestamp;
        final boolean isValidClaim;
        
        ClaimInfo(String tripId, String deviceId, long timestamp, boolean isValidClaim) {
            this.tripId = tripId;
            this.deviceId = deviceId;
            this.timestamp = timestamp;
            this.isValidClaim = isValidClaim;
        }
        
        // Public getter methods for external access
        public String getTripId() { return tripId; }
        public String getDeviceId() { return deviceId; }
        public long getTimestamp() { return timestamp; }
        public boolean isValidClaim() { return isValidClaim; }
        
        @Override
        public String toString() {
            return "ClaimInfo{trip=" + tripId + ", device=" + deviceId + ", time=" + timestamp + ", valid=" + isValidClaim + "}";
        }
    }

    /**
     * Initialize SecurityManager and migrate credentials from BuildConfig if needed
     */
    private static void initializeSecurity(Context context) {
        if (securityManager == null) {
            securityManager = SecurityManager.getInstance(context);

            // Migrate from BuildConfig to secure storage if credentials are missing
            if (securityManager.getDropboxRefreshToken() == null) {
                try {
                    // Try to get from BuildConfig for migration
                    String refreshToken = com.clone.EasyDelivery.BuildConfig.DROPBOX_REFRESH_TOKEN;
                    String appKey = com.clone.EasyDelivery.BuildConfig.DROPBOX_APP_KEY;
                    String appSecret = com.clone.EasyDelivery.BuildConfig.DROPBOX_APP_SECRET;

                    if (refreshToken != null && !refreshToken.isEmpty()) {
                        securityManager.initializeFromBuildConfig(refreshToken, appKey, appSecret);
                        Log.d(TAG, "Migrated Dropbox credentials from BuildConfig to secure storage");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to migrate credentials from BuildConfig", e);
                }
            }
        }
    }


    public static DbxClientV2 getClient(Context context) {
        initializeSecurity(context);

        if (dropboxClient == null) {
            // Get credentials from secure storage
            String refreshToken = securityManager.getDropboxRefreshToken();
            String appKey = securityManager.getDropboxAppKey();
            String appSecret = securityManager.getDropboxAppSecret();

            // Validate credentials before creating client
            if (refreshToken == null || refreshToken.isEmpty() ||
                    appKey == null || appKey.isEmpty() ||
                    appSecret == null || appSecret.isEmpty()) {

                Log.e(TAG, "Dropbox credentials are missing or invalid");
                Log.d(TAG, "Security Status:\n" + securityManager.getSecurityStatus());
                return null;
            }

            try {
                DbxRequestConfig config = DbxRequestConfig.newBuilder(CLIENT_PATH).build();
                DbxCredential credential = new DbxCredential("", 0L, refreshToken, appKey, appSecret);
                dropboxClient = new DbxClientV2(config, credential);

                Log.d(TAG, "Dropbox client initialized successfully with secure credentials");
            } catch (Exception e) {
                Log.e(TAG, "Failed to create Dropbox client", e);
                return null;
            }
        }

        return dropboxClient;
    }


    /**
     * Download all available trips from Dropbox
     */
    public static void downloadAllTrips(Context context) {
        Log.i(TAG, "Starting downloadAllTrips");
        
        DbxClientV2 client = getClient(context);
        if (client == null) {
            Log.w(TAG, "Cannot download trips - Dropbox client not available");
            return;
        }
        
        try {
            String availablePath = getCustomerPath() + "available";
            Log.d(TAG, "Listing available trips from: " + availablePath);
            
            ListFolderResult result = client.files().listFolder(availablePath);
            
            // Create trip directory if it doesn't exist
            File tripDir = new File(context.getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                tripDir.mkdirs();
            }
            
            int downloadCount = 0;
            for (com.dropbox.core.v2.files.Metadata entry : result.getEntries()) {
                if (entry instanceof com.dropbox.core.v2.files.FileMetadata) {
                    String filename = entry.getName();
                    if (filename.endsWith(".json")) {
                        File localFile = new File(tripDir, filename);
                        
                        // Only download if local file doesn't exist or is empty
                        if (!localFile.exists() || localFile.length() == 0) {
                            downloadFile(context, filename);
                            downloadCount++;
                        }
                    }
                }
            }
            
            Log.i(TAG, "downloadAllTrips completed. Downloaded " + downloadCount + " trips");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in downloadAllTrips: " + e.getMessage(), e);
        }
    }


    public static void downloadFile(Context context, String tripName) {
        
        DbxClientV2 client = getClient(context);
        if (client == null) {
            Log.w(TAG, "Cannot download file " + tripName + " - Dropbox client not available");
            return;
        }

        File tripDir = new File(context.getFilesDir() + "/Trip/");
        if (!tripDir.exists()) {
            tripDir.mkdirs();
        }

        File finalFile = new File(tripDir, tripName);
        File tempFile = new File(tripDir, tripName + ".tmp");

        try {
            // Download to a temporary file from the available folder
            try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                Log.i(TAG, "Download starting for " + tripName + " from available folder to temp file.");
                String downloadPath = getCustomerPath() + "available/" + tripName;
                client.files().downloadBuilder(downloadPath).download(outputStream);
                Log.i(TAG, "Download completed for " + tripName + " from available folder to temp file.");
            }

            // Atomically rename the temp file to the final file
            if (tempFile.renameTo(finalFile)) {
                Log.i(TAG, "Successfully renamed temp file to " + tripName);
            } else {
                Log.w(TAG, "Failed to rename temp file for " + tripName + " - trying alternative approach");
                // Alternative approach: copy and delete instead of rename  
                try {
                    copyFile(tempFile, finalFile);
                    tempFile.delete();
                    Log.i(TAG, "Successfully copied temp file to " + tripName);
                } catch (Exception copyEx) {
                    Log.w(TAG, "Copy fallback also failed for " + tripName + ": " + copyEx.getMessage());
                }
            }

        } catch (DbxException | IOException e) {
            Log.w(TAG, "Error downloading " + tripName + ": " + e.getMessage());
            // Clean up the temp file on error
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * 🔒 SYNCHRONIZED: Download file with file locking to prevent race conditions
     * 🔧 FIXED: Handles multiple concurrent downloads and file conflicts properly
     */
    public static void downloadFileWithLocking(Context context, String tripName) {
        String lockKey = "download_" + tripName;
        
        synchronized (lockKey.intern()) { // Use interned string for consistent locking
            Log.i(TAG, "🔒 LOCKED_DOWNLOAD: Acquired lock for " + tripName);
            
            // 🔧 FIXED: Declare files at broader scope for proper success checking
            File finalFile = null;
            File tempFile = null;
            boolean downloadSuccess = false;
            
            try {
                DbxClientV2 client = getClient(context);
                if (client == null) {
                    Log.w(TAG, "🔒 LOCKED_DOWNLOAD: Cannot download " + tripName + " - Dropbox client not available");
                    return;
                }

                // Validate COMPANY value to prevent malformed paths
                if (AppConstant.COMPANY == null || AppConstant.COMPANY.trim().isEmpty() || AppConstant.COMPANY.equals(" ")) {
                    Log.e(TAG, "🔒 LOCKED_DOWNLOAD: Cannot download - COMPANY is not set properly. Current value: '" + AppConstant.COMPANY + "'");
                    Log.e(TAG, "🔒 LOCKED_DOWNLOAD: This will cause malformed Dropbox paths. Please configure the app properly.");
                    throw new RuntimeException("COMPANY not configured - cannot download from Dropbox");
                }

                File tripDir = new File(context.getFilesDir() + "/Trip/");
                if (!tripDir.exists()) {
                    tripDir.mkdirs();
                }

                finalFile = new File(tripDir, tripName);
                tempFile = new File(tripDir, tripName + ".tmp");
                
                // Double-check if file already exists (another thread might have completed it)
                if (finalFile.exists() && finalFile.length() > 0) {
                    Log.i(TAG, "🔒 LOCKED_DOWNLOAD: File already exists and valid: " + tripName);
                    return;
                }
                
                // Clean up any existing temp files first
                if (tempFile.exists()) {
                    Log.i(TAG, "🔒 LOCKED_DOWNLOAD: Cleaning up existing temp file: " + tripName);
                    tempFile.delete();
                }

                try {
                    // Download to temporary file
                    try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                        Log.i(TAG, "🔒 LOCKED_DOWNLOAD: Starting download for " + tripName);
                        String downloadPath = getCustomerPath() + "available/" + tripName;
                        Log.d(TAG, "🔒 LOCKED_DOWNLOAD: Download path: " + downloadPath);
                        client.files().downloadBuilder(downloadPath).download(outputStream);
                        Log.i(TAG, "🔒 LOCKED_DOWNLOAD: Download completed for " + tripName);
                        downloadSuccess = true;
                    }
                    
                    // Validate downloaded file
                    if (!tempFile.exists() || tempFile.length() == 0) {
                        Log.w(TAG, "🔒 LOCKED_DOWNLOAD: Downloaded file is invalid for " + tripName);
                        return;
                    }

                    // Atomic rename with multiple fallback strategies
                    boolean renamed = false;
                    
                    // Strategy 1: Direct rename
                    if (tempFile.renameTo(finalFile)) {
                        renamed = true;
                        Log.i(TAG, "🔒 LOCKED_DOWNLOAD: Successfully renamed temp file to " + tripName);
                    }
                    // Strategy 2: Copy and delete
                    else {
                        try {
                            // Delete target file if it exists and is empty/corrupted
                            if (finalFile.exists() && finalFile.length() == 0) {
                                finalFile.delete();
                            }
                            
                            copyFile(tempFile, finalFile);
                            tempFile.delete();
                            renamed = true;
                            Log.i(TAG, "🔒 LOCKED_DOWNLOAD: Successfully copied temp file to " + tripName);
                        } catch (Exception copyEx) {
                            Log.w(TAG, "🔒 LOCKED_DOWNLOAD: Copy fallback failed for " + tripName + ": " + copyEx.getMessage());
                        }
                    }
                    
                    // Strategy 3: Manual byte copy as last resort
                    if (!renamed && !finalFile.exists()) {
                        try {
                            Log.i(TAG, "🔒 LOCKED_DOWNLOAD: Trying manual copy for " + tripName);
                            copyFileManually(tempFile, finalFile);
                            tempFile.delete();
                            renamed = true;
                            Log.i(TAG, "🔒 LOCKED_DOWNLOAD: Manual copy succeeded for " + tripName);
                        } catch (Exception manualEx) {
                            Log.w(TAG, "🔒 LOCKED_DOWNLOAD: Manual copy failed for " + tripName + ": " + manualEx.getMessage());
                        }
                    }
                    
                    if (renamed) {
                        Log.i(TAG, "🔒 LOCKED_DOWNLOAD: File successfully saved: " + tripName + " (" + finalFile.length() + " bytes)");
                        downloadSuccess = true;
                    } else {
                        Log.w(TAG, "🔒 LOCKED_DOWNLOAD: All rename strategies failed for " + tripName);
                        downloadSuccess = false;
                    }

                } catch (DbxException | IOException e) {
                    Log.w(TAG, "🔒 LOCKED_DOWNLOAD: Error downloading " + tripName + ": " + e.getMessage());
                    downloadSuccess = false;
                } finally {
                    // Always clean up temp file
                    if (tempFile.exists()) {
                        tempFile.delete();
                        Log.i(TAG, "🔒 LOCKED_DOWNLOAD: Cleaned up temp file for " + tripName);
                    }
                }
                
            } finally {
                Log.i(TAG, "🔒 LOCKED_DOWNLOAD: Released lock for " + tripName);
            }
        }
    }
    
    /**
     * Manual file copy as fallback when rename/NIO copy fails
     */
    private static void copyFileManually(File source, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest)) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();
        }
    }
    
    /**
     * 🔍 DIAGNOSTIC: Check Dropbox configuration and paths
     */
    public static String diagnoseDropboxConfiguration() {
        StringBuilder diagnosis = new StringBuilder();
        diagnosis.append("=== DROPBOX CONFIGURATION DIAGNOSIS ===\n");
        
        // Check COMPANY value
        diagnosis.append("AppConstant.COMPANY: '").append(AppConstant.COMPANY).append("'\n");
        diagnosis.append("COMPANY is null: ").append(AppConstant.COMPANY == null).append("\n");
        diagnosis.append("COMPANY is empty: ").append(AppConstant.COMPANY != null && AppConstant.COMPANY.isEmpty()).append("\n");
        diagnosis.append("COMPANY is single space: ").append(" ".equals(AppConstant.COMPANY)).append("\n");
        diagnosis.append("COMPANY trimmed is empty: ").append(AppConstant.COMPANY != null && AppConstant.COMPANY.trim().isEmpty()).append("\n");
        
        // Check resulting paths
        String customerPath = getCustomerPath();
        diagnosis.append("\nCUSTOMER_PATH: '").append(customerPath).append("'\n");
        diagnosis.append("Sample download path: '").append(customerPath).append("available/ORD000001.json'\n");
        
        // Check if paths are valid
        boolean pathValid = AppConstant.COMPANY != null && 
                           !AppConstant.COMPANY.trim().isEmpty() && 
                           !AppConstant.COMPANY.equals(" ");
        diagnosis.append("\nPaths valid: ").append(pathValid).append("\n");
        
        if (!pathValid) {
            diagnosis.append("\n🚈 CRITICAL ISSUE: COMPANY is not configured properly!\n");
            diagnosis.append("This will cause 'malformed_path' errors in Dropbox API calls.\n");
            diagnosis.append("\nSOLUTION: Configure the app through the login screen:\n");
            diagnosis.append("1. Enter a valid company name (e.g., 'TestCompany')\n");
            diagnosis.append("2. Complete the login form\n");
            diagnosis.append("3. The app will save the company name and trips should download\n");
        }
        
        return diagnosis.toString();
    }
    
    /**
     * 🔍 TEST: Log current Dropbox paths to verify fix
     */
    public static void logCurrentPaths() {
        try {
            Log.i(TAG, "=== DROPBOX PATH VERIFICATION ===");
            Log.i(TAG, "AppConstant.COMPANY: '" + AppConstant.COMPANY + "'");
            Log.i(TAG, "Dynamic customer path: '" + getCustomerPath() + "'");
            Log.i(TAG, "Sample download path: '" + getCustomerPath() + "available/ORD000001.json'");
            Log.i(TAG, "Path is valid: " + (AppConstant.COMPANY != null && !AppConstant.COMPANY.trim().isEmpty() && !AppConstant.COMPANY.equals(" ")));
            Log.i(TAG, "=== END VERIFICATION ===");
        } catch (Exception e) {
            Log.e(TAG, "Error logging paths", e);
        }
    }


    public static void downloadReturnFile(Context context) {
        
        DbxClientV2 client = getClient(context);
        if (client == null) {
            Log.e("Dropbox", "Cannot download return file - Dropbox client not available");
            return;
        }

        File file = new File(context.getFilesDir() + "/Return/");

        if (!file.exists()) {

            file.mkdirs();
        }

        try (OutputStream outputStream = new FileOutputStream(new File(file.getPath()))) {

            Log.i("Dropbox", "Download starting...");

            client.files().downloadBuilder(getCustomerPath() + "Returns/" + "returns.json").download(outputStream);

            Log.i("Dropbox", "Download completed.");

        } catch (FileNotFoundException e) {
            Log.w(TAG, "Returns file not found - may not exist yet: " + e.getMessage());
        } catch (DownloadErrorException e) {
            Log.w(TAG, "Download error for returns file: " + e.getMessage());
        } catch (IOException | DbxException e) {
            Log.w(TAG, "Error downloading returns file: " + e.getMessage());
        }
    }


    public static boolean uploadReturnsFile(Context context) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e("Dropbox", "Cannot upload returns file - Dropbox client not available");
                return false;
            }

            try (InputStream inputStream = new FileInputStream(new File(context.getFilesDir() + "/Return/", "returns.json"))) {

                client.files().uploadBuilder(getCustomerPath() + "Returns/" + "returns.json").withMode(WriteMode.OVERWRITE).uploadAndFinish(inputStream);

                ToastLogger.message(context, "Uploaded return");
            }

            return true;

        } catch(Exception e) {
            Log.w(TAG, "Error uploading returns file: " + e.getMessage());
            ToastLogger.exception(context, e);
            return false;
        }
    }


    // moveTripInProgress() method removed - use UnifiedTripManager.claimTrip() instead
    
    // moveTripInProgressLegacy() method removed - functionality moved to UnifiedTripManager
    
    /**
     * Safely claim a trip with comprehensive error handling and validation
     * Returns true if successfully claimed, false otherwise
     */
    private static boolean claimTripSafely(Context context, DbxClientV2 client, String tripId, String deviceId, String timestamp) {
        try {
            // Validate inputs
            if (tripId == null || tripId.isEmpty()) {
                Log.e("TripClaiming", "Cannot claim - trip ID is null or empty");
                return false;
            }
            
            if (deviceId == null || deviceId.isEmpty()) {
                Log.e("TripClaiming", "Cannot claim - device ID is null or empty");
                return false;
            }
            
            // Check if trip is already claimed by another device
            if (isTripClaimedByOtherDevice(context, client, tripId, deviceId)) {
                Log.w("TripClaiming", "🚨 Trip " + tripId + " is already claimed by another device - aborting claim");
                return false;
            }
            
            // Prepare file paths - use new folder structure
            String fromFile = getCustomerPath() + "available/" + tripId + ".json";
            String claimedName = createClaimedTripName(tripId, deviceId, timestamp);
            String toFolder = getCustomerPath() + "claimed/" + claimedName + ".json";
            
            Log.d("TripClaiming", "Attempting to claim trip: " + fromFile + " -> " + toFolder);
            
            // Perform the move operation
            client.files().moveV2(fromFile, toFolder);
            Log.i("TripClaiming", "✓ Successfully claimed and moved " + tripId + " from available to claimed with device ID: " + deviceId);
            return true;
            
        } catch (com.dropbox.core.v2.files.RelocationErrorException e) {
            // Handle specific Dropbox move errors
            Log.e("TripClaiming", "Dropbox move error for trip " + tripId + ": " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("not_found")) {
                Log.w("TripClaiming", "Source file not found - trip may have been moved by another device");
            } else if (e.getMessage() != null && e.getMessage().contains("conflict")) {
                Log.w("TripClaiming", "File conflict - trip may already be claimed");
            }
            return false;
            
        } catch (com.dropbox.core.NetworkIOException e) {
            Log.e("TripClaiming", "Network error claiming trip " + tripId + " - will retry on next sync", e);
            return false;
            
        } catch (Exception e) {
            Log.e("TripClaiming", "Unexpected error claiming trip " + tripId, e);
            return false;
        }
    }


    // moveIncompleteTrip() method removed - use UnifiedTripManager trip cleanup instead
    
    // shouldCleanupTrip() method removed - cleanup logic moved to UnifiedTripManager
    
    // moveIncompleteTripLegacy() method removed - functionality moved to UnifiedTripManager


    // uploadDeliveryMetadata() method removed - business logic moved to SyncDeliveryDataOperation
    // Use SyncDeliveryDataOperation for uploading delivery metadata instead
    
    // createMetadataUploadFolder() method removed - business logic moved to SyncDeliveryDataOperation
    
    /**
     * 🏁 Create completed trip folder structure
     */
    private static void createCompletedTripFolder(Context context, String tripName) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot create completed trip folder - Dropbox client not available");
                return;
            }
            
            String completedPath = getCustomerPath() + "completed/";
            String tripPath = completedPath + tripName + "/";
            
            try {
                // Check if trip folder already exists
                client.files().getMetadata(tripPath.substring(0, tripPath.length() - 1)); // Remove trailing slash
                Log.d(TAG, "Completed trip folder already exists: " + tripName);
            } catch (Exception e) {
                // Folder doesn't exist, create it
                try {
                    client.files().createFolderV2(completedPath + tripName);
                    Log.i(TAG, "Created completed trip folder: " + tripName);
                } catch (Exception createEx) {
                    Log.e(TAG, "Failed to create completed trip folder: " + tripName, createEx);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in createCompletedTripFolder", e);
        }
    }
    
    // createUploadFolders() method removed - use createMetadataUploadFolder() instead


    // moveCompletedTrip() method removed - use UnifiedTripManager.completeTrip() instead
    
    // moveCompletedTripLegacy() method removed - functionality moved to UnifiedTripManager


    // updateListInProgressTrips() method removed - use UnifiedTripManager in-progress trip management instead
    
    // updateInProgressTripsLegacy() method removed - functionality moved to UnifiedTripManager
    
    // ensureDropboxFolderStructure() method removed - server-side setup required for main folder structure
    
    /**
     * Get consistent device identifier for trip claiming
     * 🔧 FIXED: Now generates consistent ID across sessions for single-device operation
     * Made public for orphaned trip recovery functionality
     */
    public static String getDeviceId(Context context) {
        try {
            // Get stable device identifiers (consistent across app sessions)
            String androidId = android.provider.Settings.Secure.getString(
                context.getContentResolver(), 
                android.provider.Settings.Secure.ANDROID_ID
            );
            
            String driverName = AppConstant.DRIVER != null ? AppConstant.DRIVER.replaceAll("[^A-Za-z0-9]", "") : "Driver";
            
            // Build STABLE composite identifier (no timestamp for consistency)
            StringBuilder composite = new StringBuilder();
            composite.append(driverName).append("_");
            
            // Add Android ID (sanitized) - this is stable across sessions
            if (androidId != null && androidId.length() > 4) {
                composite.append(androidId.substring(0, Math.min(8, androidId.length())));
            } else {
                composite.append("DEV");
            }
            
            // Add device model hash for extra uniqueness (but stable)
            if (android.os.Build.MODEL != null) {
                int modelHash = Math.abs(android.os.Build.MODEL.hashCode() % 10000);
                composite.append("_").append(modelHash);
            }
            
            // 🔧 REMOVED timestamp to make device ID consistent across sessions
            
            String deviceId = composite.toString();
            Log.d("DeviceId", "Stable device ID generated: " + deviceId + " (consistent across sessions)");
            return deviceId;
            
        } catch (Exception e) {
            Log.e("DeviceId", "Error generating device ID, using fallback", e);
            // Fallback to a stable identifier
            return "Device_FALLBACK";
        }
    }
    
    /**
     * Create claimed trip filename with device and timestamp info
     */
    private static String createClaimedTripName(String tripId, String deviceId, String timestamp) {
        return tripId + "_CLAIMED_BY_" + deviceId + "_AT_" + timestamp;
    }
    
    /**
     * Check if trip is already claimed by another device
     * Uses fail-safe approach - defaults to preventing claims on errors
     */
    private static boolean isTripClaimedByOtherDevice(Context context, DbxClientV2 client, String tripId, String currentDeviceId) {
        if (tripId == null || tripId.isEmpty() || currentDeviceId == null || currentDeviceId.isEmpty()) {
            Log.e("TripClaiming", "Invalid parameters for claim check - tripId: " + tripId + ", deviceId: " + currentDeviceId);
            return true; // Fail safe - prevent claim if parameters are invalid
        }
        
        try {
            // Check both claimed and in_progress folders for existing claims
            String[] foldersToCheck = {"claimed/", "in_progress/"};
            
            for (String folder : foldersToCheck) {
                String folderPath = getCustomerPath() + folder;
                
                try {
                    ListFolderResult result = client.files().listFolder(folderPath);
                    
                    if (result != null && result.getEntries() != null) {
                        for (int i = 0; i < result.getEntries().size(); i++) {
                            String filename = result.getEntries().get(i).getName();
                            
                            if (filename == null) {
                                Log.w("TripClaiming", "Null filename encountered, skipping");
                                continue;
                            }
                            
                            // Use safe parsing instead of brittle string operations
                            ClaimInfo claimInfo = parseClaimInfo(filename);
                            
                            // Check if this file is a claimed version of our trip
                            if (claimInfo.isValidClaim && tripId.equals(claimInfo.tripId)) {
                                if (!currentDeviceId.equals(claimInfo.deviceId)) {
                                    Log.w("TripClaiming", "Trip " + tripId + " is claimed by device: " + claimInfo.deviceId + " in " + folder);
                                    
                                    // Check if claim is stale (older than 30 minutes)
                                    long currentTime = System.currentTimeMillis();
                                    long ageMinutes = (currentTime - claimInfo.timestamp) / (1000 * 60);
                                    
                                    if (ageMinutes > 30) {
                                        Log.w("TripClaiming", "Claim is stale (" + ageMinutes + " minutes old) - allowing override");
                                        return false; // Stale claim - allow override
                                    } else {
                                        Log.i("TripClaiming", "Active claim found (" + ageMinutes + " minutes old) - preventing duplicate claim");
                                        return true; // Active claim - prevent duplicate
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception folderEx) {
                    Log.d("TripClaiming", "Error checking folder " + folder + ": " + folderEx.getMessage());
                }
            }
            
            Log.d("TripClaiming", "No conflicting claims found for trip: " + tripId);
            return false; // Trip not claimed by other devices
            
        } catch (Exception e) {
            Log.e("TripClaiming", "Error checking trip claims for " + tripId + " - failing safe (preventing claim)", e);
            return true; // FAIL SAFE: Assume claimed if we can't check (prevents race conditions)
        }
    }
    
    /**
     * Parse claim information from filename using safe regex validation
     * Returns ClaimInfo object with all parsed data
     * Made public for orphaned trip recovery functionality
     */
    public static ClaimInfo parseClaimInfo(String filename) {
        try {
            if (filename == null || filename.isEmpty()) {
                Log.w("TripClaiming", "Filename is null or empty");
                return new ClaimInfo("UNKNOWN", null, 0, false);
            }
            
            // Try to match claimed filename pattern
            Matcher matcher = CLAIM_PATTERN.matcher(filename);
            if (matcher.matches()) {
                // Valid claimed filename
                String tripId = matcher.group(1);
                String deviceId = matcher.group(2);
                long timestamp = Long.parseLong(matcher.group(3));
                
                Log.d("TripClaiming", "Parsed claimed filename: " + tripId + " claimed by " + deviceId + " at " + timestamp);
                return new ClaimInfo(tripId, deviceId, timestamp, true);
            }
            
            // Not a claimed filename - treat as regular trip filename
            if (filename.endsWith(".json")) {
                String tripId = filename.substring(0, filename.length() - 5);
                Log.d("TripClaiming", "Parsed regular filename: " + tripId);
                return new ClaimInfo(tripId, null, 0, false);
            }
            
            // Unknown format
            Log.w("TripClaiming", "Unknown filename format: " + filename);
            return new ClaimInfo(filename.replaceAll("\\.json$", ""), null, 0, false);
            
        } catch (NumberFormatException e) {
            Log.e("TripClaiming", "Invalid timestamp in filename: " + filename, e);
            return new ClaimInfo("INVALID", null, 0, false);
        } catch (Exception e) {
            Log.e("TripClaiming", "Error parsing filename: " + filename, e);
            return new ClaimInfo("ERROR", null, 0, false);
        }
    }
    
    /**
     * Extract trip ID from filename (safe wrapper for backward compatibility)
     */
    private static String extractTripId(String filename) {
        return parseClaimInfo(filename).tripId;
    }
    
    /**
     * Extract claiming device ID from filename (safe wrapper for backward compatibility)
     */
    private static String extractClaimingDevice(String filename) {
        return parseClaimInfo(filename).deviceId;
    }
    
    /**
     * Extract claim timestamp from filename (safe wrapper for backward compatibility)
     */
    private static long extractClaimTimestamp(String filename) {
        return parseClaimInfo(filename).timestamp;
    }
    
    // forceUnclaimCurrentDeviceTrips() method removed - legacy method with wrong folder names
    // Use UnifiedTripManager.releaseTrip() instead
    
    // unclaimSpecificTrip() method removed - legacy method with wrong folder names
    // Use UnifiedTripManager.releaseTrip() instead
    
    // ================== UNIFIED SYNC INTEGRATION METHODS ==================
    
    /**
     * 🎯 UNIFIED: Direct trip claiming method for UnifiedTripManager
     */
    public static boolean claimTripDirectly(Context context, String tripId, String deviceId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot claim trip directly - Dropbox client not available");
                return false;
            }
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            return claimTripSafely(context, client, tripId, deviceId, timestamp);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in direct trip claiming", e);
            return false;
        }
    }
    
    /**
     * 🚀 UNIFIED: Direct trip starting method for UnifiedTripManager
     */
    public static boolean startTripDirectly(Context context, String tripId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot start trip directly - Dropbox client not available");
                return false;
            }
            
            // Find the claimed file in claimed folder and move to in_progress
            String claimedPath = getCustomerPath() + "claimed/";
            String inProgressPath = getCustomerPath() + "in_progress/";
            
            ListFolderResult claimedFiles = client.files().listFolder(claimedPath);
            
            for (int i = 0; i < claimedFiles.getEntries().size(); i++) {
                String filename = claimedFiles.getEntries().get(i).getName();
                ClaimInfo claimInfo = parseClaimInfo(filename);
                
                if (claimInfo.tripId.equals(tripId)) {
                    String fromPath = claimedPath + filename;
                    String toPath = inProgressPath + filename;
                    
                    client.files().moveV2(fromPath, toPath);
                    Log.i(TAG, "✅ Trip started: " + tripId + " moved from claimed to in_progress");
                    return true;
                }
            }
            
            Log.w(TAG, "Trip not found in claimed folder: " + tripId);
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in direct trip starting", e);
            return false;
        }
    }
    
    /**
     * 🏁 UNIFIED: Direct trip completion method for UnifiedTripManager
     */
    public static boolean completeTripDirectly(Context context, String tripId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot complete trip directly - Dropbox client not available");
                return false;
            }
            
            Log.i(TAG, "🏁 UNIFIED: Starting direct trip completion for: " + tripId);
            
            // Find the trip file in in_progress folder
            String inProgressPath = getCustomerPath() + "in_progress/";
            String actualFilename = null;
            
            try {
                ListFolderResult result = client.files().listFolder(inProgressPath);
                
                if (result != null && !result.getEntries().isEmpty()) {
                    for (int i = 0; i < result.getEntries().size(); i++) {
                        String filename = result.getEntries().get(i).getName();
                        ClaimInfo claimInfo = parseClaimInfo(filename);
                        
                        if (tripId.equals(claimInfo.tripId)) {
                            actualFilename = filename;
                            Log.i(TAG, "🔍 UNIFIED: Found trip file: " + filename);
                            break;
                        }
                    }
                }
            } catch (Exception searchEx) {
                Log.w(TAG, "Error searching in_progress folder", searchEx);
            }
            
            if (actualFilename == null) {
                Log.w(TAG, "⚠️ UNIFIED: Trip not found in in_progress, trying fallback locations");
                return completeTripFallback(context, client, tripId);
            }
            
            // Create completed folder structure
            createCompletedTripFolder(context, tripId);
            
            // Move from in_progress to completed
            String fromPath = getCustomerPath() + "in_progress/" + actualFilename;
            String toPath = getCustomerPath() + "completed/" + tripId + "/" + tripId + ".json";
            
            client.files().moveV2(fromPath, toPath);
            
            Log.i(TAG, "✅ UNIFIED: Trip completed successfully - " + tripId);
            Log.i(TAG, "FROM: " + fromPath);
            Log.i(TAG, "TO: " + toPath);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in unified trip completion", e);
            return false;
        }
    }
    
    /**
     * 🛠️ UNIFIED: Enhanced fallback completion method with comprehensive folder search
     * 🔧 FIXED: Searches all possible locations and handles race conditions properly
     */
    private static boolean completeTripFallback(Context context, DbxClientV2 client, String tripId) {
        Log.i(TAG, "🛠️ ENHANCED FALLBACK: Starting comprehensive completion attempt for: " + tripId);
        
        // 🛡️ FIRST: Check if trip is already completed (with case-insensitive check)
        if (isTripAlreadyCompleted(context, client, tripId)) {
            Log.i(TAG, "🛡️ RACE PROTECTION: Trip " + tripId + " already completed - marking as success");
            return true;
        }
        
        // 🔍 COMPREHENSIVE SEARCH: Check ALL possible folders where trip might exist
        String[] comprehensiveFolders = {
            "in_progress/",   // Most likely location for active trips
            "claimed/",      // Original fallback location
            "available/",    // Original fallback location  
            ""               // Root customer folder (legacy trips)
        };
        
        for (String folder : comprehensiveFolders) {
            try {
                String folderPath = getCustomerPath() + folder;
                Log.d(TAG, "🔍 ENHANCED FALLBACK: Searching folder: " + folderPath);
                
                ListFolderResult result = client.files().listFolder(folderPath);
                
                if (result != null && !result.getEntries().isEmpty()) {
                    for (int i = 0; i < result.getEntries().size(); i++) {
                        String filename = result.getEntries().get(i).getName();
                        ClaimInfo claimInfo = parseClaimInfo(filename);
                        
                        // 🎯 ENHANCED MATCHING: Multiple matching strategies
                        boolean isTargetTrip = false;
                        
                        // Strategy 1: Exact trip ID match from claim info
                        if (tripId.equals(claimInfo.tripId)) {
                            isTargetTrip = true;
                            Log.d(TAG, "🎯 MATCH STRATEGY 1: Found via claim info: " + filename);
                        }
                        // Strategy 2: Direct filename match
                        else if (filename.equals(tripId + ".json")) {
                            isTargetTrip = true;
                            Log.d(TAG, "🎯 MATCH STRATEGY 2: Found via direct filename: " + filename);
                        }
                        // Strategy 3: Partial filename match (handles various naming conventions)
                        else if (filename.startsWith(tripId + "_") || filename.startsWith(tripId + ".")) {
                            isTargetTrip = true;
                            Log.d(TAG, "🎯 MATCH STRATEGY 3: Found via partial match: " + filename);
                        }
                        
                        if (isTargetTrip) {
                            // 🛡️ DOUBLE-CHECK: Verify trip isn't already being completed
                            if (isTripAlreadyCompleted(context, client, tripId)) {
                                Log.i(TAG, "🛡️ RACE PROTECTION: Trip completed during search - success");
                                return true;
                            }
                            
                            // 📁 Create completed folder structure
                            createCompletedTripFolder(context, tripId);
                            
                            // 🚀 Move to completed folder
                            String fromPath = folderPath + filename;
                            String toPath = getCustomerPath() + "completed/" + tripId + "/" + tripId + ".json";
                            
                            try {
                                client.files().moveV2(fromPath, toPath);
                                
                                Log.i(TAG, "✅ ENHANCED FALLBACK: Completion successful from " + folder + "!");
                                Log.i(TAG, "📂 FROM: " + fromPath);
                                Log.i(TAG, "📂 TO: " + toPath);
                                Log.i(TAG, "🎉 ENHANCED FALLBACK: Trip " + tripId + " successfully completed via comprehensive search");
                                
                                return true;
                                
                            } catch (Exception moveEx) {
                                Log.w(TAG, "⚠️ Move failed from " + folder + ", checking if already completed: " + moveEx.getMessage());
                                
                                // Check if move failed because target already exists (another device completed it)
                                if (isTripAlreadyCompleted(context, client, tripId)) {
                                    Log.i(TAG, "🛡️ RACE PROTECTION: Target exists, trip completed by another process");
                                    return true;
                                }
                                
                                // Continue searching other folders
                                Log.d(TAG, "🔄 CONTINUE SEARCH: Will try other locations");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "🔍 SEARCH: No accessible files in " + folder + " folder: " + e.getMessage());
            }
        }
        
        // 🚫 FINAL CHECK: One last verification before giving up
        if (isTripAlreadyCompleted(context, client, tripId)) {
            Log.i(TAG, "🛡️ FINAL PROTECTION: Trip completed during comprehensive search process");
            return true;
        }
        
        Log.e(TAG, "🚫 ENHANCED FALLBACK: Comprehensive search failed for: " + tripId);
        Log.e(TAG, "📊 SEARCHED FOLDERS: " + java.util.Arrays.toString(comprehensiveFolders));
        return false;
    }
    
    /**
     * 🛡️ Enhanced race condition protection with comprehensive completion check
     * 🔧 FIXED: Handles case variations and multiple folder structures  
     */
    private static boolean isTripAlreadyCompleted(Context context, DbxClientV2 client, String tripId) {
        if (tripId == null || tripId.isEmpty()) {
            return false;
        }
        
        try {
            // Check completed folder with consistent lowercase naming
            String customerPath = getCustomerPath();
            String[] completedPaths = {
                customerPath + "completed/" + tripId + "/" + tripId + ".json",    // Standard structure
                customerPath + "completed/" + tripId + ".json"                   // Flat structure fallback
            };
            
            for (String completedPath : completedPaths) {
                try {
                    client.files().getMetadata(completedPath);
                    Log.i(TAG, "🛡️ COMPLETION CHECK: Found completed trip at: " + completedPath);
                    return true; // Trip already completed
                } catch (Exception e) {
                    // Continue checking other paths
                }
            }
            
            return false; // Trip not completed
            
        } catch (Exception e) {
            Log.w(TAG, "🛡️ COMPLETION CHECK: Error checking completion status for " + tripId + " - assuming not completed", e);
            return false;
        }
    }
    
    // createCompletedTripFolder method removed - duplicate definition
    
    /**
     * ↩️ UNIFIED: Direct trip release method for UnifiedTripManager
     */
    public static boolean releaseTripDirectly(Context context, String tripId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot release trip directly - Dropbox client not available");
                return false;
            }
            
            // Try to find and move the trip back to available from any folder
            String[] folders = {"claimed/", "in_progress/"};
            
            for (String folder : folders) {
                String folderPath = getCustomerPath() + folder;
                
                try {
                    ListFolderResult files = client.files().listFolder(folderPath);
                    
                    for (int i = 0; i < files.getEntries().size(); i++) {
                        String filename = files.getEntries().get(i).getName();
                        ClaimInfo claimInfo = parseClaimInfo(filename);
                        
                        if (claimInfo.tripId.equals(tripId)) {
                            String fromPath = folderPath + filename;
                            String toPath = getCustomerPath() + "available/" + tripId + ".json";
                            
                            client.files().moveV2(fromPath, toPath);
                            Log.i(TAG, "✅ Trip released: " + tripId + " moved back to available");
                            return true;
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "No files in folder: " + folder);
                }
            }
            
            Log.w(TAG, "Trip not found in any claimable folder: " + tripId);
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in direct trip release", e);
            return false;
        }
    }
    
    /**
     * 🛡️ RACE CONDITION PROTECTION: Check if trip is being processed by another device
     * This helps prevent multiple devices from moving the same completed trip
     * 🔧 UPDATED: Now uses enhanced completion check with comprehensive path validation
     */
    public static boolean isTripBeingProcessed(Context context, String tripId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.w("TripCompletion", "Cannot check trip processing status - Dropbox client not available");
                return false;
            }
            
            // 🔧 ENHANCED: Use comprehensive completion check
            boolean isCompleted = isTripAlreadyCompleted(context, client, tripId);
            
            if (isCompleted) {
                Log.i("TripCompletion", "🛡️ RACE PROTECTION: Trip " + tripId + " already completed by another device");
                return true; // Already processed
            }
            
            return false; // Trip not completed yet
            
        } catch (Exception e) {
            Log.w("TripCompletion", "Error checking trip processing status for " + tripId, e);
            return false; // Assume not being processed
        }
    }
    
    /**
     * 🔍 Public wrapper for checking if a trip is already completed in Dropbox
     * Used by operation classes to detect if operations should be skipped
     */
    public static boolean isTripCompletedInDropbox(Context context, String tripId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.w(TAG, "Cannot check trip completion - Dropbox client not available");
                return false;
            }
            
            return isTripAlreadyCompleted(context, client, tripId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking if trip is completed in Dropbox: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Copy file using traditional InputStream/OutputStream for API 24 compatibility
     * This replaces java.nio.file.Files.copy() which requires API 26
     */
    private static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.getParentFile().exists()) {
            destFile.getParentFile().mkdirs();
        }
        
        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(destFile)) {
            
            byte[] buffer = new byte[8192]; // 8KB buffer
            int length;
            
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            
            out.flush();
        }
    }

}
