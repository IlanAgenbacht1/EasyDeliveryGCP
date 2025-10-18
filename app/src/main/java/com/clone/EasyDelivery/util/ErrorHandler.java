package com.clone.EasyDelivery.util;

import android.util.Log;

import com.clone.EasyDelivery.repository.utils.Resource;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Standardized error handler for consistent error processing across the app
 * Consolidates error handling logic to reduce duplication
 */
public class ErrorHandler {

    private static final String TAG = "ErrorHandler";

    /**
     * Convert exception to user-friendly error message
     */
    public static String getErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error occurred";
        }

        if (throwable instanceof UnknownHostException) {
            return "No internet connection available";
        }

        if (throwable instanceof SocketTimeoutException) {
            return "Request timed out. Please try again.";
        }

        if (throwable instanceof IOException) {
            return "Network error. Please check your connection.";
        }

        String message = throwable.getMessage();
        return message != null && !message.isEmpty() ? message : "An unexpected error occurred";
    }

    /**
     * Get error code from throwable
     */
    public static int getErrorCode(Throwable throwable) {
        if (throwable instanceof UnknownHostException) {
            return -1001; // No internet
        }

        if (throwable instanceof SocketTimeoutException) {
            return -1002; // Timeout
        }

        if (throwable instanceof IOException) {
            return -1003; // Network error
        }

        return -1000; // Generic error
    }

    /**
     * Create Resource.error from throwable
     */
    public static <T> Resource<T> handleError(Throwable throwable, T data) {
        String message = getErrorMessage(throwable);
        Log.e(TAG, "Error handled: " + message, throwable);
        return Resource.error(message, data);
    }

    /**
     * Create Resource.error from message
     */
    public static <T> Resource<T> handleError(String message, T data) {
        Log.e(TAG, "Error handled: " + message);
        return Resource.error(message, data);
    }

    /**
     * Log and return standardized error
     */
    public static String logAndReturn(String tag, String message, Throwable throwable) {
        String errorMessage = getErrorMessage(throwable);
        Log.e(tag, message + ": " + errorMessage, throwable);
        return errorMessage;
    }
}