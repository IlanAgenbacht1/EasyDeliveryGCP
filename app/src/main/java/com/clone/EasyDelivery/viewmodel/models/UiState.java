package com.clone.EasyDelivery.viewmodel.models;

/**
 * Generic UI state wrapper for handling loading, success, and error states
 * @param <T> The type of data being wrapped
 */
public class UiState<T> {
    
    public enum Status {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }
    
    private final Status status;
    private final T data;
    private final String errorMessage;
    
    private UiState(Status status, T data, String errorMessage) {
        this.status = status;
        this.data = data;
        this.errorMessage = errorMessage;
    }
    
    // Factory methods
    public static <T> UiState<T> idle() {
        return new UiState<>(Status.IDLE, null, null);
    }
    
    public static <T> UiState<T> loading() {
        return new UiState<>(Status.LOADING, null, null);
    }
    
    public static <T> UiState<T> success(T data) {
        return new UiState<>(Status.SUCCESS, data, null);
    }
    
    public static <T> UiState<T> error(String errorMessage) {
        return new UiState<>(Status.ERROR, null, errorMessage);
    }
    
    public static <T> UiState<T> error(String errorMessage, T data) {
        return new UiState<>(Status.ERROR, data, errorMessage);
    }
    
    // Getters
    public Status getStatus() {
        return status;
    }
    
    public T getData() {
        return data;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    // State checks
    public boolean isIdle() {
        return status == Status.IDLE;
    }
    
    public boolean isLoading() {
        return status == Status.LOADING;
    }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean isError() {
        return status == Status.ERROR;
    }
    
    // Utility methods
    public boolean hasData() {
        return data != null;
    }
    
    public boolean hasError() {
        return errorMessage != null && !errorMessage.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return "UiState{" +
                "status=" + status +
                ", hasData=" + hasData() +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        UiState<?> uiState = (UiState<?>) o;
        
        if (status != uiState.status) return false;
        if (data != null ? !data.equals(uiState.data) : uiState.data != null) return false;
        return errorMessage != null ? errorMessage.equals(uiState.errorMessage) : uiState.errorMessage == null;
    }
    
    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (data != null ? data.hashCode() : 0);
        result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
        return result;
    }
}