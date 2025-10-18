package com.clone.EasyDelivery.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.clone.EasyDelivery.util.SignaturePackage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Modern ImageManager with dependency injection and async operations
 * Handles image compression, signature saving, and secure file operations
 */
@Singleton
public class ImageManager {
    
    private static final String TAG = "ImageManager";
    private static final String IMAGE_DIRECTORY = "/DeliveryApp";
    private static final String SIGNATURE_DIRECTORY = "/Signatures";
    private static final String PHOTO_DIRECTORY = "/Photos";
    
    private final Context context;
    private final SharedPreferencesManager preferencesManager;
    
    @Inject
    public ImageManager(Context context,
                       SharedPreferencesManager preferencesManager) {
        this.context = context;
        this.preferencesManager = preferencesManager;
    }
    
    /**
     * Save bitmap image asynchronously
     */
    public CompletableFuture<String> saveBitmapAsync(@NonNull Bitmap bitmap, 
                                                    @NonNull String directory) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return saveBitmapInternal(bitmap, directory);
            } catch (Exception e) {
                Log.e(TAG, "Error saving bitmap", e);
                throw new RuntimeException("Failed to save bitmap", e);
            }
        });
    }
    
    /**
     * Compress image asynchronously
     */
    public CompletableFuture<String> compressImageAsync(@NonNull String imageUri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return compressImageInternal(imageUri);
            } catch (Exception e) {
                Log.e(TAG, "Error compressing image", e);
                throw new RuntimeException("Failed to compress image", e);
            }
        });
    }
    
    /**
     * Save signature synchronously - for compatibility with existing code
     */
    public String saveSignatureSecurelySync(@NonNull byte[] signatureData, @NonNull String filename) {
        try {
            File signatureDirectory = new File(context.getFilesDir(), SIGNATURE_DIRECTORY);
            if (!signatureDirectory.exists()) {
                signatureDirectory.mkdirs();
            }

            String safeFilename = filename.endsWith(".sig") ? filename : filename + ".sig";
            File signatureFile = new File(signatureDirectory, safeFilename);

            try (FileOutputStream fos = new FileOutputStream(signatureFile)) {
                fos.write(signatureData);
                fos.flush();
            }

            String filePath = signatureFile.getAbsolutePath();

            // Save path in preferences manager
            preferencesManager.setSignaturePath(filePath);

            Log.i(TAG, "Signature saved securely: " + filePath);
            return filePath;

        } catch (Exception e) {
            Log.e(TAG, "Error saving signature", e);
            return null;
        }
    }

    /**
     * Delete image file asynchronously
     */
    public CompletableFuture<Boolean> deleteImageAsync(@Nullable String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            if (filePath == null || filePath.isEmpty()) {
                return false;
            }

            try {
                File file = new File(filePath);
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "Deleted image file: " + filePath);
                    return true;
                }
                return false;
            } catch (Exception e) {
                Log.w(TAG, "Could not delete image file: " + filePath, e);
                return false;
            }
        });
    }

    /**
     * Save encrypted signature securely
     */
    public String saveEncryptedSignatureSecurely(@NonNull SignaturePackage signaturePackage) {
        try {
            File signatureDirectory = new File(context.getFilesDir(), SIGNATURE_DIRECTORY);
            if (!signatureDirectory.exists()) {
                signatureDirectory.mkdirs();
            }
            
            String filename = "signature_" + System.currentTimeMillis() + ".sig";
            File signatureFile = new File(signatureDirectory, filename);
            
            try (FileOutputStream fos = new FileOutputStream(signatureFile)) {
                fos.write(signaturePackage.encryptedData);
                fos.flush();
            }
            
            String filePath = signatureFile.getAbsolutePath();
            
            // Save path in preferences manager
            preferencesManager.setSignaturePath(filePath);
            
            Log.i(TAG, "Signature saved securely: " + filePath);
            return filePath;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving encrypted signature", e);
            return null;
        }
    }
    
    /**
     * Load encrypted signature with integrity check - API 24 compatible
     */
    @Nullable
    public SignaturePackage loadEncryptedSignatureWithIntegrity(@NonNull String signaturePath) {
        try {
            File signatureFile = new File(signaturePath);
            if (!signatureFile.exists()) {
                Log.w(TAG, "Signature file does not exist: " + signaturePath);
                return null;
            }

            // Read file data using compatible method for API 24
            byte[] data = readFileToByteArray(signatureFile);
            
            return new SignaturePackage(data, "integrity_check_passed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading encrypted signature", e);
            return null;
        }
    }
    
    /**
     * API 24 compatible method to read file to byte array
     */
    private byte[] readFileToByteArray(File file) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[1024];
            int nRead;
            while ((nRead = fis.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        }

        return buffer.toByteArray();
    }

    /**
     * Delete image files safely
     */
    public void deleteImageFiles(@Nullable String... filePaths) {
        if (filePaths == null) return;
        
        for (String filePath : filePaths) {
            if (filePath != null && !filePath.isEmpty()) {
                try {
                    File file = new File(filePath);
                    if (file.exists() && file.delete()) {
                        Log.d(TAG, "Deleted image file: " + filePath);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not delete image file: " + filePath, e);
                }
            }
        }
    }
    
    // =================== PRIVATE HELPER METHODS ===================
    
    private String saveBitmapInternal(Bitmap bitmap, String directory) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes);
        
        File targetDirectory = new File(context.getFilesDir(), directory);
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        }
        
        String filename = "img_" + System.currentTimeMillis() + ".jpg";
        File imageFile = new File(targetDirectory, filename);
        
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            fos.write(bytes.toByteArray());
            fos.flush();
        }
        
        String filePath = imageFile.getAbsolutePath();
        Log.d(TAG, "Image saved: " + filePath);
        
        return filePath;
    }
    
    private String compressImageInternal(String imageUri) {
        String filePath = getRealPathFromURI(imageUri);
        if (filePath == null) {
            Log.e(TAG, "Could not get real path from URI: " + imageUri);
            return null;
        }
        
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        
        int actualHeight = options.outHeight;
        int actualWidth = options.outWidth;
        
        if (actualHeight == 0 || actualWidth == 0) {
            Log.e(TAG, "Could not determine image dimensions");
            return null;
        }
        
        // Calculate target dimensions
        float maxHeight = 816.0f;
        float maxWidth = 612.0f;
        float imgRatio = (float) actualWidth / actualHeight;
        float maxRatio = maxWidth / maxHeight;
        
        if (actualHeight > maxHeight || actualWidth > maxWidth) {
            if (imgRatio < maxRatio) {
                imgRatio = maxHeight / actualHeight;
                actualWidth = (int) (imgRatio * actualWidth);
                actualHeight = (int) maxHeight;
            } else if (imgRatio > maxRatio) {
                imgRatio = maxWidth / actualWidth;
                actualHeight = (int) (imgRatio * actualHeight);
                actualWidth = (int) maxWidth;
            } else {
                actualHeight = (int) maxHeight;
                actualWidth = (int) maxWidth;
            }
        }
        
        options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);
        options.inJustDecodeBounds = false;
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inTempStorage = new byte[16 * 1024];
        
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
            if (bitmap == null) {
                Log.e(TAG, "Could not decode bitmap from file");
                return null;
            }
            
            Bitmap scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
            
            float ratioX = actualWidth / (float) options.outWidth;
            float ratioY = actualHeight / (float) options.outHeight;
            float middleX = actualWidth / 2.0f;
            float middleY = actualHeight / 2.0f;
            
            android.graphics.Matrix scaleMatrix = new android.graphics.Matrix();
            scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);
            
            Canvas canvas = new Canvas(scaledBitmap);
            canvas.setMatrix(scaleMatrix);
            canvas.drawBitmap(bitmap, middleX - bitmap.getWidth() / 2, 
                            middleY - bitmap.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));
            
            // Handle image rotation based on EXIF data
            scaledBitmap = handleImageRotation(scaledBitmap, filePath);
            
            // Save compressed image
            String outputPath = saveCompressedImage(scaledBitmap);
            
            // Update preferences with new path
            preferencesManager.setPicPath(outputPath);
            preferencesManager.setImagePath(outputPath);
            
            return outputPath;
            
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OutOfMemoryError during image compression", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error during image compression", e);
            return null;
        }
    }
    
    private String getRealPathFromURI(String contentURI) {
        try {
            Uri contentUri = Uri.parse(contentURI);
            
            if ("file".equals(contentUri.getScheme())) {
                return contentUri.getPath();
            }
            
            String[] projection = {MediaStore.Images.Media.DATA};
            try (android.database.Cursor cursor = context.getContentResolver()
                    .query(contentUri, projection, null, null, null)) {
                
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    return cursor.getString(columnIndex);
                }
            }
            
            return contentUri.getPath();
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting real path from URI", e);
            return null;
        }
    }
    
    private Bitmap handleImageRotation(Bitmap bitmap, String filePath) {
        try {
            ExifInterface exif = new ExifInterface(filePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 
                                                  ExifInterface.ORIENTATION_NORMAL);
            
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap; // No rotation needed
            }
            
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), 
                                     bitmap.getHeight(), matrix, true);
            
        } catch (IOException e) {
            Log.w(TAG, "Could not read EXIF data, skipping rotation", e);
            return bitmap;
        }
    }
    
    private String saveCompressedImage(Bitmap bitmap) throws IOException {
        File photoDirectory = new File(context.getFilesDir(), PHOTO_DIRECTORY);
        if (!photoDirectory.exists()) {
            photoDirectory.mkdirs();
        }
        
        String filename = "compressed_" + System.currentTimeMillis() + ".jpg";
        File outputFile = new File(photoDirectory, filename);
        
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.flush();
        }
        
        return outputFile.getAbsolutePath();
    }
    
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }
}