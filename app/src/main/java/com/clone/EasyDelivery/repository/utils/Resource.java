package com.clone.EasyDelivery.repository.utils;

/**
 * A generic class that holds a value with its loading status.
 * @param <T>
 */
public class Resource<T> {
    
    public enum Status {
        SUCCESS,
        ERROR,
        LOADING
    }
    
    private final Status status;
    private final T data;
    private final String message;
    private final Throwable error;
    
    private Resource(Status status, T data, String message, Throwable error) {
        this.status = status;
        this.data = data;
        this.message = message;
        this.error = error;
    }
    
    public static <T> Resource<T> success(T data) {
        return new Resource<>(Status.SUCCESS, data, null, null);
    }
    
    public static <T> Resource<T> error(String msg, T data) {
        return new Resource<>(Status.ERROR, data, msg, null);
    }
    
    public static <T> Resource<T> error(String msg, T data, Throwable error) {
        return new Resource<>(Status.ERROR, data, msg, error);
    }
    
    public static <T> Resource<T> loading(T data) {
        return new Resource<>(Status.LOADING, data, null, null);
    }
    
    public Status getStatus() {
        return status;
    }
    
    public T getData() {
        return data;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Throwable getError() {
        return error;
    }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean isError() {
        return status == Status.ERROR;
    }
    
    public boolean isLoading() {
        return status == Status.LOADING;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Resource<?> resource = (Resource<?>) o;
        
        if (status != resource.status) return false;
        if (data != null ? !data.equals(resource.data) : resource.data != null) return false;
        return message != null ? message.equals(resource.message) : resource.message == null;
    }
    
    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (data != null ? data.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }
    
    @Override
    public String toString() {
        return "Resource{" +
                "status=" + status +
                ", data=" + data +
                ", message='" + message + '\'' +
                '}';
    }
}