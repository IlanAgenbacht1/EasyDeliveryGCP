package com.clone.EasyDelivery.Api.utils;

import android.util.Log;

import com.clone.EasyDelivery.Api.models.response.BaseApiResponse;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import retrofit2.Response;

/**
 * Utility class for handling API responses and errors consistently
 */
public class ApiResponseHandler {
    
    private static final String TAG = "ApiResponseHandler";
    
    /**
     * Handle API response and extract result or error
     */
    public static <T extends BaseApiResponse> ApiResult<T> handleResponse(Response<T> response) {
        if (response.isSuccessful() && response.body() != null) {
            T body = response.body();

            // For successful HTTP responses (2xx), consider the response successful
            // unless there's an explicit error field present
            if (body.isSuccess() || (response.code() >= 200 && response.code() < 300 && body.getError() == null)) {
                return ApiResult.success(body);
            } else {
                String errorMessage = body.getError() != null ? body.getError() : "Unknown API error";
                return ApiResult.error(errorMessage, response.code());
            }
        } else {
            String errorMessage = "HTTP " + response.code() + ": " + response.message();
            return ApiResult.error(errorMessage, response.code());
        }
    }
    
    /**
     * Handle API exceptions and convert to user-friendly messages
     */
    public static <T> ApiResult<T> handleException(Throwable throwable) {
        Log.e(TAG, "API Exception: ", throwable);
        
        String errorMessage;
        int errorCode = -1;
        
        if (throwable instanceof UnknownHostException) {
            errorMessage = "Cannot reach server. Please check your internet connection.";
        } else if (throwable instanceof ConnectException) {
            errorMessage = "Connection failed. Please try again later.";
        } else if (throwable instanceof SocketTimeoutException) {
            errorMessage = "Request timed out. Please try again.";
        } else if (throwable instanceof IOException) {
            String message = throwable.getMessage();
            if (message != null && message.contains("No internet connection")) {
                errorMessage = "No internet connection available.";
            } else {
                errorMessage = "Network error occurred. Please try again.";
            }
        } else {
            errorMessage = "An unexpected error occurred: " + throwable.getMessage();
        }
        
        return ApiResult.error(errorMessage, errorCode);
    }
    
    /**
     * Check if error indicates a network/connectivity issue
     */
    public static boolean isNetworkError(Throwable throwable) {
        return throwable instanceof UnknownHostException ||
               throwable instanceof ConnectException ||
               throwable instanceof SocketTimeoutException ||
               (throwable instanceof IOException && 
                throwable.getMessage() != null && 
                throwable.getMessage().contains("No internet connection"));
    }
    
    /**
     * Check if error indicates authentication/session issue
     */
    public static boolean isAuthError(int httpCode) {
        return httpCode == 401 || httpCode == 403;
    }
    
    /**
     * Result wrapper class for API responses
     */
    public static class ApiResult<T> {
        private final boolean isSuccess;
        private final T data;
        private final String errorMessage;
        private final int errorCode;
        
        private ApiResult(boolean isSuccess, T data, String errorMessage, int errorCode) {
            this.isSuccess = isSuccess;
            this.data = data;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
        }
        
        public static <T> ApiResult<T> success(T data) {
            return new ApiResult<>(true, data, null, 0);
        }
        
        public static <T> ApiResult<T> error(String errorMessage, int errorCode) {
            return new ApiResult<>(false, null, errorMessage, errorCode);
        }
        
        public boolean isSuccess() {
            return isSuccess;
        }
        
        public T getData() {
            return data;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
        
        public boolean isNetworkError() {
            return errorCode == -1 || errorMessage != null && (
                errorMessage.contains("internet connection") ||
                errorMessage.contains("Connection failed") ||
                errorMessage.contains("Network error") ||
                errorMessage.contains("timed out")
            );
        }
        
        public boolean isAuthError() {
            return ApiResponseHandler.isAuthError(errorCode);
        }
    }
}