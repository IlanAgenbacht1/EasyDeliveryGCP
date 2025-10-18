package com.clone.EasyDelivery.Api.services;

import com.clone.EasyDelivery.Api.models.request.ValidateKeyRequest;
import com.clone.EasyDelivery.Api.models.request.RegisterDeviceRequest;
import com.clone.EasyDelivery.Api.models.request.LoginRequest;
import com.clone.EasyDelivery.Api.models.request.StartSessionRequest;
import com.clone.EasyDelivery.Api.models.request.SetupPinRequest;
import com.clone.EasyDelivery.Api.models.response.ValidateKeyResponse;
import com.clone.EasyDelivery.Api.models.response.BaseApiResponse;
import com.clone.EasyDelivery.models.Driver;
import com.clone.EasyDelivery.models.Vehicle;
import com.clone.EasyDelivery.models.SessionInfo;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Retrofit service interface for authentication operations
 * Maps to AuthController in DeliveryCloudApi
 */
public interface AuthService {
    
    /**
     * Validate enrollment key against client
     */
    @POST("auth/validate-key")
    Call<ValidateKeyResponse> validateEnrollmentKey(@Body ValidateKeyRequest request);
    
    /**
     * Register new device with client
     */
    @POST("auth/register-device") 
    Call<BaseApiResponse> registerDevice(@Body RegisterDeviceRequest request);
    
    /**
     * Check if device is approved for use
     */
    @GET("auth/device-status")
    Call<DeviceStatusResponse> getDeviceStatus(@Query("deviceId") String deviceId);
    
    /**
     * Get available drivers for client
     */
    @GET("auth/drivers")
    Call<DriversResponse> getDrivers(@Query("clientId") String clientId);
    
    /**
     * Get available vehicles for client
     */
    @GET("auth/vehicles")
    Call<VehiclesResponse> getVehicles(@Query("clientId") String clientId);

    /**
     * Setup PIN for driver (first time)
     */
    @POST("auth/setup-pin")
    Call<BaseApiResponse> setupDriverPin(@Body SetupPinRequest request);

    /**
     * Driver login with PIN
     */
    @POST("auth/login")
    Call<BaseApiResponse> driverLogin(@Body LoginRequest request);
    
    /**
     * Start session with vehicle info
     */
    @POST("auth/start-session")
    Call<StartSessionResponse> startSession(@Body StartSessionRequest request);
    
    /**
     * End session
     */
    @POST("auth/end-session")
    Call<BaseApiResponse> endSession(@Query("sessionId") String sessionId);
    
    // Inner classes for specific response types
    public static class DeviceStatusResponse extends BaseApiResponse {
        private String deviceId;
        private String clientId;
        private String deviceType;
        private boolean isApproved;
        private String registeredAt;
        private String lastSeenAt;
        
        // Getters and setters
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
        public boolean isApproved() { return isApproved; }
        public void setApproved(boolean approved) { isApproved = approved; }
        public String getRegisteredAt() { return registeredAt; }
        public void setRegisteredAt(String registeredAt) { this.registeredAt = registeredAt; }
        public String getLastSeenAt() { return lastSeenAt; }
        public void setLastSeenAt(String lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    }
    
    public static class DriversResponse extends BaseApiResponse {
        private String clientId;
        private List<Driver> drivers;
        private int count;
        
        // Getters and setters
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public List<Driver> getDrivers() {
            return drivers;
        }

        public void setDrivers(List<Driver> drivers) {
            this.drivers = drivers;
        }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static class VehiclesResponse extends BaseApiResponse {
        private String clientId;
        private List<Vehicle> vehicles;
        private int count;

        // Getters and setters
        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public List<Vehicle> getVehicles() {
            return vehicles;
        }

        public void setVehicles(List<Vehicle> vehicles) {
            this.vehicles = vehicles;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    public static class StartSessionResponse extends BaseApiResponse {
        private String sessionId;
        private SessionInfo sessionInfo;
        
        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public SessionInfo getSessionInfo() {
            return sessionInfo;
        }

        public void setSessionInfo(SessionInfo sessionInfo) {
            this.sessionInfo = sessionInfo;
        }
    }
}