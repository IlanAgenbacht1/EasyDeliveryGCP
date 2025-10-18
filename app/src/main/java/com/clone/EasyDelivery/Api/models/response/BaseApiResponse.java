package com.clone.EasyDelivery.Api.models.response;

/**
 * Base API response model with common fields
 */
public class BaseApiResponse {
    private boolean success;
    private String error;
    private String message;
    
    public BaseApiResponse() {}
    
    public BaseApiResponse(boolean success, String error, String message) {
        this.success = success;
        this.error = error;
        this.message = message;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}