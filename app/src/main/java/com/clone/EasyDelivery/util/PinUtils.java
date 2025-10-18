package com.clone.EasyDelivery.util;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utility class for PIN hashing and validation
 * 
 * PIN Authentication Flow:
 * 1. ONLINE: PINs are validated server-side via API calls to Firestore
 * 2. OFFLINE: Local PIN cache allows authentication when network unavailable
 * 3. PIN Setup: New PINs are sent to server and cached locally after successful save
 */
public class PinUtils {
    
    private static final String TAG = "PinUtils";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;
    
    /**
     * Hash a PIN with a randomly generated salt for LOCAL OFFLINE storage only
     * Server-side PINs are managed by Firestore Authentication
     */
    public static String hashPinForOfflineCache(String pin) {
        try {
            // Generate random salt
            byte[] salt = generateSalt();
            
            // Hash PIN with salt
            String hash = hashPinWithSalt(pin, salt);
            
            // Return salt + hash combined
            return bytesToHex(salt) + ":" + hash;
            
        } catch (Exception e) {
            Log.e(TAG, "Error hashing PIN", e);
            return null;
        }
    }
    
    /**
     * Verify a PIN against a locally cached hash (for offline authentication)
     */
    public static boolean verifyPinAgainstCache(String pin, String storedHash) {
        try {
            if (pin == null || storedHash == null) {
                return false;
            }
            
            // Split salt and hash
            String[] parts = storedHash.split(":");
            if (parts.length != 2) {
                Log.e(TAG, "Invalid stored hash format");
                return false;
            }
            
            byte[] salt = hexToBytes(parts[0]);
            String expectedHash = parts[1];
            
            // Hash the input PIN with the stored salt
            String actualHash = hashPinWithSalt(pin, salt);
            
            // Compare hashes
            return expectedHash.equals(actualHash);
            
        } catch (Exception e) {
            Log.e(TAG, "Error verifying PIN", e);
            return false;
        }
    }
    
    /**
     * Generate a secure random salt
     */
    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }
    
    /**
     * Hash PIN with provided salt
     */
    private static String hashPinWithSalt(String pin, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        
        // Add salt to digest
        digest.update(salt);
        
        // Hash PIN
        byte[] hash = digest.digest(pin.getBytes());
        
        return bytesToHex(hash);
    }
    
    /**
     * Convert byte array to hexadecimal string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Convert hexadecimal string to byte array
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
    
    /**
     * Validate PIN format (4-6 digits)
     */
    public static boolean isValidPinFormat(String pin) {
        if (pin == null) return false;
        return pin.matches("\\d{4,6}");
    }
    
    /**
     * Prepare PIN data for server transmission (for PIN setup/change)
     * Returns the raw PIN - server will handle hashing with its own salt
     */
    public static String preparePinForServer(String pin) {
        // In production, you might want to encrypt this for transmission
        // For now, return as-is since HTTPS will encrypt in transit
        return pin;
    }
    
    /**
     * Create a session token hash for API authentication
     * This combines driver ID with current timestamp for session validation
     */
    public static String generateSessionToken(String driverId) {
        try {
            long timestamp = System.currentTimeMillis();
            String combined = driverId + ":" + timestamp + ":session";
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(combined.getBytes());
            return bytesToHex(hash).substring(0, 32); // Return first 32 characters
        } catch (Exception e) {
            Log.e(TAG, "Error generating session token", e);
            return null;
        }
    }
    
    /**
     * Validate PIN strength (basic rules)
     */
    public static boolean isPinSecure(String pin) {
        if (!isValidPinFormat(pin)) return false;
        
        // Check for common weak PINs
        String[] weakPins = {"0000", "1111", "2222", "3333", "4444", "5555", 
                            "6666", "7777", "8888", "9999", "1234", "4321", 
                            "0123", "3210", "1212", "2121"};
        
        for (String weak : weakPins) {
            if (pin.equals(weak)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get PIN strength description
     */
    public static String getPinStrengthDescription(String pin) {
        if (!isValidPinFormat(pin)) {
            return "PIN must be 4-6 digits";
        }
        
        if (!isPinSecure(pin)) {
            return "PIN is too common. Choose a different combination.";
        }
        
        if (pin.length() >= 6) {
            return "Strong PIN";
        } else if (pin.length() >= 5) {
            return "Good PIN";
        } else {
            return "Basic PIN";
        }
    }
}