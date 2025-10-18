package com.clone.EasyDelivery.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Modern FileManager with dependency injection and secure file operations
 * Handles file cleanup, management, and secure deletion operations
 */
@Singleton
public class FileManager {
    
    private static final String TAG = "FileManager";
    private static final String APP_FOLDER_NAME = "EasyDelivery";
    
    public enum CleanupResult {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED,
        NO_FILES_TO_CLEAN
    }
    
    public static class CleanupReport {
        public final CleanupResult result;
        public final int filesDeleted;
        public final int filesSkipped;
        public final long spaceClearedBytes;
        public final List<String> errors;
        
        public CleanupReport(CleanupResult result, int filesDeleted, int filesSkipped, 
                           long spaceClearedBytes, List<String> errors) {
            this.result = result;
            this.filesDeleted = filesDeleted;
            this.filesSkipped = filesSkipped;
            this.spaceClearedBytes = spaceClearedBytes;
            this.errors = new ArrayList<>(errors);
        }
        
        @NonNull
        @Override
        public String toString() {
            return "CleanupReport{" +
                    "result=" + result +
                    ", filesDeleted=" + filesDeleted +
                    ", filesSkipped=" + filesSkipped +
                    ", spaceClearedMB=" + (spaceClearedBytes / (1024 * 1024)) +
                    ", errors=" + errors.size() +
                    '}';
        }
    }
    
    private final Context context;
    private final SharedPreferencesManager preferencesManager;
    
    @Inject
    public FileManager(@ApplicationContext Context context,
                      SharedPreferencesManager preferencesManager) {
        this.context = context;
        this.preferencesManager = preferencesManager;
    }
    
    /**
     * Clean up temporary files asynchronously
     */
    public CompletableFuture<CleanupReport> cleanupTemporaryFilesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Log.i(TAG, "Starting cleanup of temporary files");
            
            List<String> errors = new ArrayList<>();
            int filesDeleted = 0;
            int filesSkipped = 0;
            long spaceClearedBytes = 0;
            
            try {
                // Clean app cache directory
                File cacheDir = context.getCacheDir();
                CleanupReport cacheResult = cleanupDirectoryContents(cacheDir, errors);
                filesDeleted += cacheResult.filesDeleted;
                filesSkipped += cacheResult.filesSkipped;
                spaceClearedBytes += cacheResult.spaceClearedBytes;
                
                // Clean external cache directory if available
                File externalCacheDir = context.getExternalCacheDir();
                if (externalCacheDir != null && externalCacheDir.exists()) {
                    CleanupReport externalCacheResult = cleanupDirectoryContents(externalCacheDir, errors);
                    filesDeleted += externalCacheResult.filesDeleted;
                    filesSkipped += externalCacheResult.filesSkipped;
                    spaceClearedBytes += externalCacheResult.spaceClearedBytes;
                }
                
                // Clean temporary image files in app-specific directories
                CleanupReport tempImageResult = cleanupTemporaryImages(errors);
                filesDeleted += tempImageResult.filesDeleted;
                filesSkipped += tempImageResult.filesSkipped;
                spaceClearedBytes += tempImageResult.spaceClearedBytes;
                
            } catch (Exception e) {
                Log.e(TAG, "Error during cleanup", e);
                errors.add("General cleanup error: " + e.getMessage());
            }
            
            CleanupResult result;
            if (filesDeleted == 0 && filesSkipped == 0) {
                result = CleanupResult.NO_FILES_TO_CLEAN;
            } else if (errors.isEmpty()) {
                result = CleanupResult.SUCCESS;
            } else if (filesDeleted > 0) {
                result = CleanupResult.PARTIAL_SUCCESS;
            } else {
                result = CleanupResult.FAILED;
            }
            
            CleanupReport report = new CleanupReport(result, filesDeleted, filesSkipped, 
                                                   spaceClearedBytes, errors);
            Log.i(TAG, "Cleanup completed: " + report);
            return report;
        });
    }
    
    /**
     * Securely delete a specific file
     */
    public CompletableFuture<Boolean> deleteFileSecurelyAsync(@NonNull String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = new File(filePath);
                if (!file.exists()) {
                    Log.d(TAG, "File already deleted: " + filePath);
                    return true;
                }
                
                // Log file info before deletion
                long fileSize = file.length();
                Log.d(TAG, "Deleting file: " + filePath + " (size: " + fileSize + " bytes)");
                
                boolean deleted = file.delete();
                if (deleted) {
                    Log.i(TAG, "Successfully deleted file: " + filePath);
                } else {
                    Log.w(TAG, "Failed to delete file: " + filePath);
                }
                
                return deleted;
            } catch (Exception e) {
                Log.e(TAG, "Error deleting file: " + filePath, e);
                return false;
            }
        });
    }
    
    /**
     * Get app-specific storage directory
     */
    @NonNull
    public File getAppStorageDirectory() {
        File appDir = new File(context.getFilesDir(), APP_FOLDER_NAME);
        if (!appDir.exists()) {
            boolean created = appDir.mkdirs();
            Log.d(TAG, "App storage directory created: " + created + " at " + appDir.getAbsolutePath());
        }
        return appDir;
    }
    
    /**
     * Get app-specific external storage directory
     */
    @Nullable
    public File getAppExternalStorageDirectory() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File externalDir = new File(context.getExternalFilesDir(null), APP_FOLDER_NAME);
            if (!externalDir.exists()) {
                boolean created = externalDir.mkdirs();
                Log.d(TAG, "App external storage directory created: " + created + 
                          " at " + externalDir.getAbsolutePath());
            }
            return externalDir;
        }
        return null;
    }
    
    /**
     * Create a temporary file in app cache directory
     */
    @NonNull
    public File createTempFile(@NonNull String prefix, @NonNull String suffix) throws IOException {
        File tempFile = File.createTempFile(prefix, suffix, context.getCacheDir());
        Log.d(TAG, "Created temp file: " + tempFile.getAbsolutePath());
        return tempFile;
    }
    
    /**
     * Copy file to app storage directory
     */
    public CompletableFuture<Boolean> copyFileToAppStorageAsync(@NonNull String sourcePath, 
                                                               @NonNull String destinationFileName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File sourceFile = new File(sourcePath);
                if (!sourceFile.exists()) {
                    Log.w(TAG, "Source file does not exist: " + sourcePath);
                    return false;
                }
                
                File destinationFile = new File(getAppStorageDirectory(), destinationFileName);
                Files.copy(sourceFile.toPath(), destinationFile.toPath(), 
                          StandardCopyOption.REPLACE_EXISTING);
                
                Log.i(TAG, "File copied successfully: " + sourcePath + " -> " + 
                          destinationFile.getAbsolutePath());
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error copying file: " + sourcePath, e);
                return false;
            }
        });
    }
    
    /**
     * Get storage usage statistics
     */
    public CompletableFuture<StorageStats> getStorageStatsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long cacheSize = calculateDirectorySize(context.getCacheDir());
                long filesSize = calculateDirectorySize(context.getFilesDir());
                long externalCacheSize = 0;
                long externalFilesSize = 0;
                
                File externalCacheDir = context.getExternalCacheDir();
                if (externalCacheDir != null) {
                    externalCacheSize = calculateDirectorySize(externalCacheDir);
                }
                
                File externalFilesDir = context.getExternalFilesDir(null);
                if (externalFilesDir != null) {
                    externalFilesSize = calculateDirectorySize(externalFilesDir);
                }
                
                return new StorageStats(cacheSize, filesSize, externalCacheSize, externalFilesSize);
            } catch (Exception e) {
                Log.e(TAG, "Error calculating storage stats", e);
                return new StorageStats(0, 0, 0, 0);
            }
        });
    }
    
    public static class StorageStats {
        public final long cacheSizeBytes;
        public final long filesSizeBytes;
        public final long externalCacheSizeBytes;
        public final long externalFilesSizeBytes;
        
        public StorageStats(long cacheSizeBytes, long filesSizeBytes, 
                          long externalCacheSizeBytes, long externalFilesSizeBytes) {
            this.cacheSizeBytes = cacheSizeBytes;
            this.filesSizeBytes = filesSizeBytes;
            this.externalCacheSizeBytes = externalCacheSizeBytes;
            this.externalFilesSizeBytes = externalFilesSizeBytes;
        }
        
        public long getTotalSizeBytes() {
            return cacheSizeBytes + filesSizeBytes + externalCacheSizeBytes + externalFilesSizeBytes;
        }
        
        public double getTotalSizeMB() {
            return getTotalSizeBytes() / (1024.0 * 1024.0);
        }
        
        @NonNull
        @Override
        public String toString() {
            return "StorageStats{" +
                    "total=" + String.format("%.2f MB", getTotalSizeMB()) +
                    ", cache=" + String.format("%.2f MB", cacheSizeBytes / (1024.0 * 1024.0)) +
                    ", files=" + String.format("%.2f MB", filesSizeBytes / (1024.0 * 1024.0)) +
                    ", externalCache=" + String.format("%.2f MB", externalCacheSizeBytes / (1024.0 * 1024.0)) +
                    ", externalFiles=" + String.format("%.2f MB", externalFilesSizeBytes / (1024.0 * 1024.0)) +
                    '}';
        }
    }
    
    // =================== PRIVATE HELPER METHODS ===================
    
    private CleanupReport cleanupDirectoryContents(File directory, List<String> errors) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return new CleanupReport(CleanupResult.NO_FILES_TO_CLEAN, 0, 0, 0, errors);
        }
        
        int filesDeleted = 0;
        int filesSkipped = 0;
        long spaceClearedBytes = 0;
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    if (file.isFile()) {
                        long fileSize = file.length();
                        if (file.delete()) {
                            filesDeleted++;
                            spaceClearedBytes += fileSize;
                            Log.d(TAG, "Deleted file: " + file.getAbsolutePath());
                        } else {
                            filesSkipped++;
                            errors.add("Failed to delete: " + file.getAbsolutePath());
                        }
                    } else if (file.isDirectory()) {
                        // Recursively clean subdirectories
                        CleanupReport subResult = cleanupDirectoryContents(file, errors);
                        filesDeleted += subResult.filesDeleted;
                        filesSkipped += subResult.filesSkipped;
                        spaceClearedBytes += subResult.spaceClearedBytes;
                        
                        // Try to delete empty directory
                        if (file.listFiles() == null || file.listFiles().length == 0) {
                            if (file.delete()) {
                                Log.d(TAG, "Deleted empty directory: " + file.getAbsolutePath());
                            }
                        }
                    }
                } catch (Exception e) {
                    filesSkipped++;
                    errors.add("Error processing " + file.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
        
        return new CleanupReport(CleanupResult.SUCCESS, filesDeleted, filesSkipped, spaceClearedBytes, new ArrayList<>());
    }
    
    private CleanupReport cleanupTemporaryImages(List<String> errors) {
        int filesDeleted = 0;
        int filesSkipped = 0;
        long spaceClearedBytes = 0;
        
        // Clean temporary signature files
        String signatureDir = preferencesManager.getSignatureDirectory();
        if (signatureDir != null) {
            File sigDir = new File(signatureDir);
            CleanupReport sigResult = cleanupOldFiles(sigDir, 7, errors); // Files older than 7 days
            filesDeleted += sigResult.filesDeleted;
            filesSkipped += sigResult.filesSkipped;
            spaceClearedBytes += sigResult.spaceClearedBytes;
        }
        
        return new CleanupReport(CleanupResult.SUCCESS, filesDeleted, filesSkipped, spaceClearedBytes, new ArrayList<>());
    }
    
    private CleanupReport cleanupOldFiles(File directory, int maxAgeDays, List<String> errors) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return new CleanupReport(CleanupResult.NO_FILES_TO_CLEAN, 0, 0, 0, new ArrayList<>());
        }
        
        int filesDeleted = 0;
        int filesSkipped = 0;
        long spaceClearedBytes = 0;
        long cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24L * 60L * 60L * 1000L);
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    if (file.isFile() && file.lastModified() < cutoffTime) {
                        long fileSize = file.length();
                        if (file.delete()) {
                            filesDeleted++;
                            spaceClearedBytes += fileSize;
                            Log.d(TAG, "Deleted old file: " + file.getAbsolutePath());
                        } else {
                            filesSkipped++;
                            errors.add("Failed to delete old file: " + file.getAbsolutePath());
                        }
                    }
                } catch (Exception e) {
                    filesSkipped++;
                    errors.add("Error processing old file " + file.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
        
        return new CleanupReport(CleanupResult.SUCCESS, filesDeleted, filesSkipped, spaceClearedBytes, new ArrayList<>());
    }
    
    private long calculateDirectorySize(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return 0;
        }
        
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                }
            }
        }
        return size;
    }
}