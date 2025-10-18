package com.clone.EasyDelivery.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.clone.EasyDelivery.Api.models.request.LoginRequest;
import com.clone.EasyDelivery.Api.models.request.RegisterDeviceRequest;
import com.clone.EasyDelivery.Api.models.request.StartSessionRequest;
import com.clone.EasyDelivery.Api.models.request.SetupPinRequest;
import com.clone.EasyDelivery.Api.models.request.ValidateKeyRequest;
import com.clone.EasyDelivery.Api.models.response.ValidateKeyResponse;
import com.clone.EasyDelivery.Api.models.response.BaseApiResponse;
import com.clone.EasyDelivery.Api.services.AuthService;
import com.clone.EasyDelivery.Api.utils.ApiResponseHandler;
import com.clone.EasyDelivery.data.DeliveryDatabase;
import com.clone.EasyDelivery.data.dao.DeviceDao;
import com.clone.EasyDelivery.data.dao.SessionDao;
import com.clone.EasyDelivery.data.entities.DeviceEntity;
import com.clone.EasyDelivery.data.entities.SessionEntity;
import com.clone.EasyDelivery.repository.utils.NetworkBoundResource;
import com.clone.EasyDelivery.repository.utils.Resource;
import com.clone.EasyDelivery.util.DeviceUtils;
import com.clone.EasyDelivery.util.PinUtils;
import com.clone.EasyDelivery.util.SecurePreferences;
import com.clone.EasyDelivery.sync.SimpleRetryManager;
import com.clone.EasyDelivery.sync.SyncWorker;
import com.clone.EasyDelivery.viewmodel.models.AuthState;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for handling authentication operations with proper Hilt dependency injection
 * Mediates between API services and local database
 */
@Singleton
public class AuthRepository {

    private static final String TAG = "AuthRepository";
    
    private final Context context;
    private final AuthService authService;
    private final DeliveryDatabase database;
    private final DeviceDao deviceDao;
    private final SessionDao sessionDao;
    private final SecurePreferences securePreferences;
    private final SimpleRetryManager retryManager;
    private final ExecutorService executor;
    
    // Current device info
    private final String deviceId;
    private final String deviceType;

    @Inject
    public AuthRepository(@ApplicationContext Context context,
                          AuthService authService,
                          DeliveryDatabase database,
                          SimpleRetryManager retryManager) {
        this.context = context;
        this.authService = authService;
        this.database = database;
        this.deviceDao = database.deviceDao();
        this.sessionDao = database.sessionDao();
        this.securePreferences = SecurePreferences.getInstance(context);
        this.retryManager = retryManager;
        this.executor = Executors.newFixedThreadPool(2);
        
        // Get device information
        this.deviceId = DeviceUtils.getDeviceId(context);
        this.deviceType = DeviceUtils.getDeviceType();

        Log.i(TAG, "AuthRepository initialized with Hilt DI for device: " + deviceId);
    }
    
    // =================== ENROLLMENT & DEVICE REGISTRATION ===================
    
    /**
     * Validate enrollment key and get client information
     */
    public CompletableFuture<ApiResponseHandler.ApiResult<ValidateKeyResponse>> validateEnrollmentKey(String enrollmentKey) {
        Log.i(TAG, "Validating enrollment key");
        
        ValidateKeyRequest request = new ValidateKeyRequest(enrollmentKey, deviceId);
        
        CompletableFuture<ApiResponseHandler.ApiResult<ValidateKeyResponse>> future = new CompletableFuture<>();
        
        authService.validateEnrollmentKey(request).enqueue(new Callback<ValidateKeyResponse>() {
            @Override
            public void onResponse(Call<ValidateKeyResponse> call, Response<ValidateKeyResponse> response) {
                ApiResponseHandler.ApiResult<ValidateKeyResponse> result = ApiResponseHandler.handleResponse(response);
                future.complete(result);
            }
            
            @Override
            public void onFailure(Call<ValidateKeyResponse> call, Throwable t) {
                ApiResponseHandler.ApiResult<ValidateKeyResponse> result = ApiResponseHandler.handleException(t);
                future.complete(result);
            }
        });
        
        return future;
    }
    
    /**
     * Register device with validated enrollment key
     */
    public CompletableFuture<ApiResponseHandler.ApiResult<AuthService.DeviceStatusResponse>> registerDevice(String enrollmentKey) {
        Log.i(TAG, "Registering device with enrollment key");
        
        RegisterDeviceRequest request = new RegisterDeviceRequest(enrollmentKey, deviceId, deviceType);
        
        CompletableFuture<ApiResponseHandler.ApiResult<AuthService.DeviceStatusResponse>> future = new CompletableFuture<>();

        authService.registerDevice(request).enqueue(new Callback<com.clone.EasyDelivery.Api.models.response.BaseApiResponse>() {
            @Override
            public void onResponse(Call<com.clone.EasyDelivery.Api.models.response.BaseApiResponse> call, Response<com.clone.EasyDelivery.Api.models.response.BaseApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    // After successful registration, get device status
                    getDeviceStatus().thenAccept(deviceResult -> {
                        if (deviceResult.isSuccess()) {
                            // Store device info locally with the client ID from enrollment
                            executor.execute(() -> {
                                // Use the stored client ID from enrollment (single source of truth)
                                String clientId = getStoredClientId();

                                DeviceEntity device = new DeviceEntity(
                                    deviceId,
                                        clientId,  // Client ID comes from enrollment preferences
                                    deviceType,
                                    deviceResult.getData().isApproved()
                                );
                                deviceDao.insertDevice(device);
                            });
                        }
                        future.complete(deviceResult);
                    });
                } else {
                    ApiResponseHandler.ApiResult<com.clone.EasyDelivery.Api.models.response.BaseApiResponse> result = ApiResponseHandler.handleResponse(response);
                    // Convert BaseApiResponse error to DeviceStatusResponse error
                    ApiResponseHandler.ApiResult<AuthService.DeviceStatusResponse> deviceResult = 
                        ApiResponseHandler.ApiResult.error(result.getErrorMessage(), result.getErrorCode());
                    future.complete(deviceResult);
                }
            }
            
            @Override
            public void onFailure(Call<com.clone.EasyDelivery.Api.models.response.BaseApiResponse> call, Throwable t) {
                ApiResponseHandler.ApiResult<AuthService.DeviceStatusResponse> result = ApiResponseHandler.handleException(t);
                future.complete(result);
            }
        });
        
        return future;
    }
    
    /**
     * Get current device status from API and update local cache
     */
    public CompletableFuture<ApiResponseHandler.ApiResult<AuthService.DeviceStatusResponse>> getDeviceStatus() {
        Log.i(TAG, "Getting device status for: " + deviceId);
        
        CompletableFuture<ApiResponseHandler.ApiResult<AuthService.DeviceStatusResponse>> future = new CompletableFuture<>();
        
        authService.getDeviceStatus(deviceId).enqueue(new Callback<AuthService.DeviceStatusResponse>() {
            @Override
            public void onResponse(Call<AuthService.DeviceStatusResponse> call, Response<AuthService.DeviceStatusResponse> response) {
                ApiResponseHandler.ApiResult<AuthService.DeviceStatusResponse> result = ApiResponseHandler.handleResponse(response);
                
                if (result.isSuccess()) {
                    // Update local device info
                    executor.execute(() -> {
                        AuthService.DeviceStatusResponse data = result.getData();
                        DeviceEntity device = deviceDao.getDevice(deviceId);

                        // Use the client ID from enrollment (single source of truth)
                        String enrollmentClientId = getStoredClientId();

                        if (device == null) {
                            // Create new device with enrollment client ID
                            device = new DeviceEntity(deviceId, enrollmentClientId, data.getDeviceType(), data.isApproved());
                        } else {
                            // Update existing device - always use enrollment client ID if available
                            if (enrollmentClientId != null) {
                                device.setClientId(enrollmentClientId);
                            }
                            device.setApproved(data.isApproved());
                            device.setLastSeenAt(System.currentTimeMillis());
                            device.markAsSynced();
                        }
                        
                        deviceDao.insertDevice(device);
                        Log.i(TAG, "Device status updated locally with client ID from enrollment");
                    });
                }
                
                future.complete(result);
            }
            
            @Override
            public void onFailure(Call<AuthService.DeviceStatusResponse> call, Throwable t) {
                ApiResponseHandler.ApiResult<AuthService.DeviceStatusResponse> result = ApiResponseHandler.handleException(t);
                future.complete(result);
            }
        });
        
        return future;
    }
    
    /**
     * Check if device is registered and approved
     */
    public boolean isDeviceRegistered() {
        DeviceEntity device = deviceDao.getDevice(deviceId);
        return device != null && device.isApproved();
    }

    /**
     * Async version of isDeviceRegistered for use in ViewModels
     */
    public CompletableFuture<Boolean> isDeviceRegisteredAsync() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                DeviceEntity device = deviceDao.getDevice(deviceId);
                boolean isRegistered = device != null && device.isApproved();
                future.complete(isRegistered);
            } catch (Exception e) {
                Log.e(TAG, "Error checking device registration", e);
                future.complete(false);
            }
        });
        return future;
    }

    public LiveData<DeviceEntity> getDeviceLiveData() {
        return deviceDao.getDeviceLiveData(deviceId);
    }

    // =================== CLIENT ID MANAGEMENT ===================

    /**
     * Set client ID permanently for this device
     */
    public void setClientId(String clientId, String clientName) {
        Log.i(TAG, "Setting client ID: " + clientId + " (Name: " + clientName + ")");
        securePreferences.storeString("client_id", clientId);
        securePreferences.storeString("client_name", clientName);
        securePreferences.storeString("client_setup_completed", "true");
    }

    /**
     * Get the stored client ID
     */
    public String getStoredClientId() {
        return securePreferences.getString("client_id", null);
    }

    /**
     * Get the stored client name
     */
    public String getStoredClientName() {
        return securePreferences.getString("client_name", null);
    }

    /**
     * Check if client setup is completed
     */
    public boolean isClientSetupCompleted() {
        return "true".equals(securePreferences.getString("client_setup_completed", "false"));
    }

    public String getCurrentClientId() {
        // Primary source: Client ID from successful enrollment key validation
        String enrollmentClientId = getStoredClientId();
        if (enrollmentClientId != null) {
            return enrollmentClientId;
        }

        // Fallback: Check if device has client ID stored (for existing installations)
        // This ensures compatibility but enrollment is still the primary source
        DeviceEntity device = deviceDao.getDevice(deviceId);
        String deviceClientId = device != null ? device.getClientId() : null;

        // If we found a client ID in device but not in enrollment preferences,
        // store it in preferences for consistency (migration scenario)
        if (deviceClientId != null && enrollmentClientId == null) {
            Log.i(TAG, "Migrating client ID from device to enrollment preferences: " + deviceClientId);
            securePreferences.storeString("client_id", deviceClientId);
            securePreferences.storeString("client_setup_completed", "true");
        }

        return deviceClientId;
    }

    // =================== DRIVER AUTHENTICATION ===================
    
    /**
     * Get available drivers for current client
     */
    public CompletableFuture<ApiResponseHandler.ApiResult<AuthService.DriversResponse>> getAvailableDrivers() {
        CompletableFuture<ApiResponseHandler.ApiResult<AuthService.DriversResponse>> future = new CompletableFuture<>();

        // Get client ID asynchronously to avoid main thread database access
        executor.execute(() -> {
            String clientId = getCurrentClientId();
            if (clientId == null) {
                future.complete(ApiResponseHandler.ApiResult.error("Device not registered", -1));
                return;
            }

            Log.i(TAG, "Getting available drivers for client: " + clientId);

            authService.getDrivers(clientId).enqueue(new Callback<AuthService.DriversResponse>() {
                @Override
                public void onResponse(Call<AuthService.DriversResponse> call, Response<AuthService.DriversResponse> response) {
                    ApiResponseHandler.ApiResult<AuthService.DriversResponse> result = ApiResponseHandler.handleResponse(response);
                    future.complete(result);
                }

                @Override
                public void onFailure(Call<AuthService.DriversResponse> call, Throwable t) {
                    ApiResponseHandler.ApiResult<AuthService.DriversResponse> result = ApiResponseHandler.handleException(t);
                    future.complete(result);
                }
            });
        });
        
        return future;
    }
    
    /**
     * Get available vehicles for current client
     */
    public CompletableFuture<ApiResponseHandler.ApiResult<AuthService.VehiclesResponse>> getAvailableVehicles() {
        CompletableFuture<ApiResponseHandler.ApiResult<AuthService.VehiclesResponse>> future = new CompletableFuture<>();

        // Get client ID asynchronously to avoid main thread database access
        executor.execute(() -> {
            String clientId = getCurrentClientId();
            if (clientId == null) {
                future.complete(ApiResponseHandler.ApiResult.error("Device not registered", -1));
                return;
            }

            Log.i(TAG, "Getting available vehicles for client: " + clientId);

            authService.getVehicles(clientId).enqueue(new Callback<AuthService.VehiclesResponse>() {
                @Override
                public void onResponse(Call<AuthService.VehiclesResponse> call, Response<AuthService.VehiclesResponse> response) {
                    ApiResponseHandler.ApiResult<AuthService.VehiclesResponse> result = ApiResponseHandler.handleResponse(response);
                    future.complete(result);
                }

                @Override
                public void onFailure(Call<AuthService.VehiclesResponse> call, Throwable t) {
                    ApiResponseHandler.ApiResult<AuthService.VehiclesResponse> result = ApiResponseHandler.handleException(t);
                    future.complete(result);
                }
            });
        });

        return future;
    }

    /**
     * Authenticate driver with PIN
     * First tries server validation, falls back to offline cache if network unavailable
     */
    public CompletableFuture<ApiResponseHandler.ApiResult<Void>> authenticateDriver(String driverId, String pin) {
        Log.i(TAG, "Authenticating driver: " + driverId);
        
        // Validate PIN format first
        if (!PinUtils.isValidPinFormat(pin)) {
            CompletableFuture<ApiResponseHandler.ApiResult<Void>> future = new CompletableFuture<>();
            future.complete(ApiResponseHandler.ApiResult.error("Invalid PIN format", 400));
            return future;
        }
        
        // Prepare PIN for server transmission
        String serverPin = PinUtils.preparePinForServer(pin);
        LoginRequest request = new LoginRequest(driverId, serverPin, deviceId);
        
        CompletableFuture<ApiResponseHandler.ApiResult<Void>> future = new CompletableFuture<>();

        authService.driverLogin(request).enqueue(new Callback<BaseApiResponse>() {
            @Override
            public void onResponse(Call<BaseApiResponse> call, Response<BaseApiResponse> response) {
                ApiResponseHandler.ApiResult<BaseApiResponse> result = ApiResponseHandler.handleResponse(response);
                
                if (result.isSuccess()) {
                    // Server validation successful
                    securePreferences.storeString("authenticated_driver_id", driverId);
                    
                    // Cache PIN hash for offline authentication
                    String offlineHash = PinUtils.hashPinForOfflineCache(pin);
                    if (offlineHash != null) {
                        securePreferences.storeString("driver_" + driverId + "_pin_hash", offlineHash);
                    }
                    
                    Log.i(TAG, "Driver authenticated successfully via server");
                    
                    // Initialize sync services after successful authentication
                    initializeSyncServices();
                    
                    future.complete(ApiResponseHandler.ApiResult.success(null));
                } else {
                    future.complete(ApiResponseHandler.ApiResult.error(result.getErrorMessage(), result.getErrorCode()));
                }
            }
            
            @Override
            public void onFailure(Call<BaseApiResponse> call, Throwable t) {
                Log.w(TAG, "Server authentication failed, trying offline cache", t);
                
                // Try offline authentication as fallback
                String cachedHash = securePreferences.getString("driver_" + driverId + "_pin_hash", null);
                if (cachedHash != null && PinUtils.verifyPinAgainstCache(pin, cachedHash)) {
                    securePreferences.storeString("authenticated_driver_id", driverId);
                    Log.i(TAG, "Driver authenticated successfully via offline cache");
                    
                    // Initialize sync services for offline authentication too
                    initializeSyncServices();
                    
                    future.complete(ApiResponseHandler.ApiResult.success(null));
                } else {
                    Log.e(TAG, "Offline authentication also failed");
                    ApiResponseHandler.ApiResult<Void> result = ApiResponseHandler.handleException(t);
                    future.complete(result);
                }
            }
        });
        
        return future;
    }

    /**
     * Setup PIN for first-time driver
     */
    public CompletableFuture<ApiResponseHandler.ApiResult<BaseApiResponse>> setupDriverPin(String driverId, String pin) {
        CompletableFuture<ApiResponseHandler.ApiResult<BaseApiResponse>> future = new CompletableFuture<>();

        SetupPinRequest request = new SetupPinRequest(driverId, pin);

        authService.setupDriverPin(request).enqueue(new retrofit2.Callback<BaseApiResponse>() {
            @Override
            public void onResponse(retrofit2.Call<BaseApiResponse> call, retrofit2.Response<BaseApiResponse> response) {
                ApiResponseHandler.ApiResult<BaseApiResponse> result = ApiResponseHandler.handleResponse(response);
                future.complete(result);
            }

            @Override
            public void onFailure(retrofit2.Call<BaseApiResponse> call, Throwable t) {
                Log.e(TAG, "PIN setup request failed", t);
                future.complete(ApiResponseHandler.ApiResult.error("Network error: " + t.getMessage(), -1));
            }
        });

        return future;
    }

    // =================== SESSION MANAGEMENT ===================
    
    /**
     * Start a new session for authenticated driver with selected vehicle
     */
    public CompletableFuture<ApiResponseHandler.ApiResult<SessionEntity>> startSession(String vehicleId) {
        String driverId = getAuthenticatedDriverId();
        String clientId = getCurrentClientId();
        
        if (driverId == null || clientId == null) {
            CompletableFuture<ApiResponseHandler.ApiResult<SessionEntity>> future = new CompletableFuture<>();
            future.complete(ApiResponseHandler.ApiResult.error("Driver not authenticated or device not registered", -1));
            return future;
        }

        Log.i(TAG, "Starting session for driver: " + driverId + " with vehicle: " + vehicleId);

        StartSessionRequest request = new StartSessionRequest(driverId, deviceId, clientId, vehicleId);
        
        CompletableFuture<ApiResponseHandler.ApiResult<SessionEntity>> future = new CompletableFuture<>();
        
        authService.startSession(request).enqueue(new Callback<AuthService.StartSessionResponse>() {
            @Override
            public void onResponse(Call<AuthService.StartSessionResponse> call, Response<AuthService.StartSessionResponse> response) {
                ApiResponseHandler.ApiResult<AuthService.StartSessionResponse> result = ApiResponseHandler.handleResponse(response);
                
                if (result.isSuccess()) {
                    AuthService.StartSessionResponse data = result.getData();
                    String sessionId = data.getSessionId();
                    
                    // Create local session entity
                    SessionEntity session = new SessionEntity(sessionId, driverId, deviceId, clientId, vehicleId);
                    
                    executor.execute(() -> {
                        // End any existing active sessions for this driver/device
                        sessionDao.endInactiveSessions(System.currentTimeMillis() - 1000, System.currentTimeMillis());
                        
                        // Insert new session
                        session.markAsSynced(); // Already synced since it came from server
                        sessionDao.insertSession(session);
                        
                        // Store current session info
                        securePreferences.storeString("current_session_id", sessionId);
                        // Session management is handled by SecurePreferences
                        
                        // Schedule periodic sync now that we have an active session
                        SyncWorker.schedulePeriodicSync(context);
                        
                        Log.i(TAG, "Session started successfully: " + sessionId + ", periodic sync scheduled");
                    });
                    
                    future.complete(ApiResponseHandler.ApiResult.success(session));
                } else {
                    future.complete(ApiResponseHandler.ApiResult.error(result.getErrorMessage(), result.getErrorCode()));
                }
            }
            
            @Override
            public void onFailure(Call<AuthService.StartSessionResponse> call, Throwable t) {
                Log.w(TAG, "Server session start failed, creating offline session", t);
                
                // Create offline session as fallback
                executor.execute(() -> {
                    try {
                        // Generate temporary session ID for offline mode
                        String offlineSessionId = "offline_" + driverId + "_" + System.currentTimeMillis();
                        
                        // Create offline session entity
                        SessionEntity offlineSession = new SessionEntity(offlineSessionId, driverId, deviceId, clientId, vehicleId);
                        offlineSession.markAsUnsynced(); // Will be synced when network is available
                        
                        // End any existing active sessions
                        sessionDao.endInactiveSessions(System.currentTimeMillis() - 1000, System.currentTimeMillis());
                        
                        // Insert offline session
                        sessionDao.insertSession(offlineSession);
                        
                        // Store session info
                        securePreferences.storeString("current_session_id", offlineSessionId);
                        
                        // Queue offline action for sync when network is available
                        if (retryManager != null) {
                            retryManager.queueFailedRequest("SESSION", offlineSessionId, "CREATE", null);
                        } else {
                            Log.w(TAG, "RetryManager is null, skipping failed request queue");
                        }
                        
                        // Schedule network sync to sync session when network becomes available
                        SyncWorker.scheduleNetworkSync(context);
                        
                        Log.i(TAG, "Offline session created: " + offlineSessionId);
                        future.complete(ApiResponseHandler.ApiResult.success(offlineSession));
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to create offline session", e);
                        ApiResponseHandler.ApiResult<SessionEntity> result = ApiResponseHandler.handleException(t);
                        future.complete(result);
                    }
                });
            }
        });
        
        return future;
    }
    
    /**
     * End current session
     */
    public CompletableFuture<ApiResponseHandler.ApiResult<Void>> endSession() {
        String sessionId = getCurrentSessionId();
        if (sessionId == null) {
            CompletableFuture<ApiResponseHandler.ApiResult<Void>> future = new CompletableFuture<>();
            future.complete(ApiResponseHandler.ApiResult.error("No active session", -1));
            return future;
        }
        
        Log.i(TAG, "Ending session: " + sessionId);
        
        CompletableFuture<ApiResponseHandler.ApiResult<Void>> future = new CompletableFuture<>();

        authService.endSession(sessionId).enqueue(new Callback<BaseApiResponse>() {
            @Override
            public void onResponse(Call<BaseApiResponse> call, Response<BaseApiResponse> response) {
                // End session locally regardless of API response
                executor.execute(() -> {
                    sessionDao.endSession(sessionId, System.currentTimeMillis());
                    securePreferences.removeKey("current_session_id");
                    // Session ending is handled by SecurePreferences
                    
                    // Cancel periodic sync when session ends
                    SyncWorker.cancelPeriodicSync(context);
                    
                    // Trigger final sync to upload any pending changes
                    if (retryManager != null) {
                        retryManager.forceSyncNow();
                    } else {
                        Log.w(TAG, "RetryManager is null, skipping final sync");
                    }
                });

                ApiResponseHandler.ApiResult<BaseApiResponse> result = ApiResponseHandler.handleResponse(response);
                future.complete(result.isSuccess() ? 
                    ApiResponseHandler.ApiResult.success(null) : 
                    ApiResponseHandler.ApiResult.error(result.getErrorMessage(), result.getErrorCode()));
            }
            
            @Override
            public void onFailure(Call<BaseApiResponse> call, Throwable t) {
                // Still end session locally on network failure
                executor.execute(() -> {
                    sessionDao.endSession(sessionId, System.currentTimeMillis());
                    securePreferences.removeKey("current_session_id");
                    // Session ending is handled by SecurePreferences
                    
                    // Queue offline action to end session on server when network is available
                    if (retryManager != null) {
                        retryManager.queueFailedRequest("SESSION", sessionId, "END_SESSION", null);
                    } else {
                        Log.w(TAG, "RetryManager is null, skipping failed request queue");
                    }
                    
                    // Cancel periodic sync
                    SyncWorker.cancelPeriodicSync(context);
                    
                    // Schedule network sync to sync session end when network becomes available
                    SyncWorker.scheduleNetworkSync(context);
                });
                
                ApiResponseHandler.ApiResult<Void> result = ApiResponseHandler.handleException(t);
                future.complete(result);
            }
        });
        
        return future;
    }
    
    // =================== SESSION STATE ===================
    
    public String getCurrentSessionId() {
        return securePreferences.getString("current_session_id", null);
    }
    
    public String getAuthenticatedDriverId() {
        return securePreferences.getString("authenticated_driver_id", null);
    }
    
    /**
     * Async version of getCurrentClientId for use in ViewModels
     */
    public CompletableFuture<String> getCurrentClientIdAsync() {
        CompletableFuture<String> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                DeviceEntity device = deviceDao.getDevice(deviceId);
                String clientId = device != null ? device.getClientId() : null;
                future.complete(clientId);
            } catch (Exception e) {
                Log.e(TAG, "Error getting client ID", e);
                future.complete(null);
            }
        });
        return future;
    }

    public boolean hasActiveSession() {
        String sessionId = getCurrentSessionId();
        return sessionId != null && sessionDao.isSessionActive(sessionId);
    }

    /**
     * Async version of hasActiveSession for use in ViewModels
     */
    public CompletableFuture<Boolean> hasActiveSessionAsync() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                String sessionId = getCurrentSessionId();
                boolean hasActive = sessionId != null && sessionDao.isSessionActive(sessionId);
                future.complete(hasActive);
            } catch (Exception e) {
                Log.e(TAG, "Error checking active session", e);
                future.complete(false);
            }
        });
        return future;
    }

    // =================== SYNC INTEGRATION ===================
    
    /**
     * Initialize sync services after successful authentication
     * This should be called after driver authentication is successful
     */
    public void initializeSyncServices() {
        Log.i(TAG, "Initializing sync services");

        if (retryManager == null) {
            Log.w(TAG, "RetryManager is null, skipping sync initialization");
            return;
        }

        // Schedule network sync to handle any pending offline actions
        SyncWorker.scheduleNetworkSync(context);
        
        // Perform immediate sync to get latest data
        executor.execute(() -> {
            retryManager.forceSyncNow().thenAccept(result -> {
                if (result.isSuccess()) {
                    Log.d(TAG, "Initial sync completed successfully");
                } else {
                    Log.w(TAG, "Initial sync failed: " + result.getErrorMessage());
                }
            });
        });
    }
    
    /**
     * Cleanup sync services (called on logout or session end)
     */
    public void cleanupSyncServices() {
        Log.i(TAG, "Cleaning up sync services");
        
        // Cancel all scheduled sync work
        SyncWorker.cancelAllSyncWork(context);
        
        // Clear sync manager state
        if (retryManager != null) {
            retryManager.cleanup();
        } else {
            Log.w(TAG, "RetryManager is null, skipping cleanup");
        }
    }
    
    /**
     * Force sync now (for manual sync requests)
     */
    public CompletableFuture<Boolean> forceSyncNow() {
        Log.i(TAG, "Force sync requested by user");

        if (retryManager == null) {
            Log.w(TAG, "RetryManager is null, returning false for force sync");
            return CompletableFuture.completedFuture(false);
        }

        return retryManager.forceSyncNow().thenApply(result -> {
            if (result.isSuccess()) {
                Log.d(TAG, "Force sync completed: " + result.getSummary());
                return true;
            } else {
                Log.e(TAG, "Force sync failed: " + result.getErrorMessage());
                return false;
            }
        });
    }
    
    /**
     * Get sync status for UI display
     */
    public LiveData<com.clone.EasyDelivery.sync.models.SyncStatus> getSyncStatus() {
        if (retryManager != null) {
            return retryManager.getSyncStatus();
        } else {
            Log.w(TAG, "RetryManager is null, returning empty LiveData for sync status");
            return new MediatorLiveData<>();
        }
    }
    
    /**
     * Get sync progress for UI display
     */
    public LiveData<String> getSyncProgress() {
        if (retryManager != null) {
            return retryManager.getSyncProgress();
        } else {
            Log.w(TAG, "RetryManager is null, returning empty LiveData for sync progress");
            return new MediatorLiveData<>();
        }
    }
    
    /**
     * Check if sync is currently in progress
     */
    public boolean isSyncInProgress() {
        if (retryManager != null) {
            return retryManager.isSyncInProgress();
        } else {
            Log.w(TAG, "RetryManager is null, returning false for sync in progress");
            return false;
        }
    }
    
    /**
     * Get last sync time for UI display
     */
    public long getLastSyncTime() {
        if (retryManager != null) {
            return retryManager.getLastFullSyncTime();
        } else {
            Log.w(TAG, "RetryManager is null, returning 0 for last sync time");
            return 0;
        }
    }
    
    public LiveData<SessionEntity> getCurrentSessionLiveData() {
        String sessionId = getCurrentSessionId();
        if (sessionId != null) {
            return sessionDao.getSessionLiveData(sessionId);
        }
        return new MediatorLiveData<>(); // Empty LiveData
    }
    
    public SessionEntity getCurrentSession() {
        String sessionId = getCurrentSessionId();
        return sessionId != null ? sessionDao.getSession(sessionId) : null;
    }
    
    // =================== LOGOUT & CLEANUP ===================
    
    /**
     * Complete logout - end session and clear authentication
     */
    public CompletableFuture<Void> logout() {
        Log.i(TAG, "Performing logout");
        
        return endSession().thenCompose(result -> {
            // Clear authentication data
            executor.execute(() -> {
                securePreferences.removeKey("authenticated_driver_id");
                securePreferences.removeKey("current_session_id");
                // Session clearing is handled by SecurePreferences
            });
            
            return CompletableFuture.completedFuture(null);
        });
    }
    
    /**
     * Clear all authentication data (for debugging/reset)
     */
    public void clearAllAuthData() {
        Log.w(TAG, "Clearing all authentication data");
        
        executor.execute(() -> {
            securePreferences.removeKey("authenticated_driver_id");
            securePreferences.removeKey("current_session_id");
            // Session clearing is handled by SecurePreferences
            
            // End all active sessions
            sessionDao.endInactiveSessions(System.currentTimeMillis() + 1000, System.currentTimeMillis());
        });
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public String getDeviceType() {
        return deviceType;
    }

    // =================== AUTH STATE CACHING ===================

    /**
     * Check if we have a cached auth state that's still valid
     */
    public AuthState getCachedAuthState() {
        String cachedState = securePreferences.getString("cached_auth_state", null);
        long cacheTime = securePreferences.getLong("auth_state_cache_time", 0);
        long cacheValidDuration = 24 * 60 * 60 * 1000; // 24 hours

        // Check if cache is still valid
        if (cachedState != null && (System.currentTimeMillis() - cacheTime) < cacheValidDuration) {
            Log.i(TAG, "Using cached auth state: " + cachedState);
            try {
                return AuthState.valueOf(cachedState);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid cached auth state, will fetch fresh", e);
            }
        }

        Log.i(TAG, "No valid cached auth state, will fetch from API");
        return null;
    }

    /**
     * Cache the current auth state
     */
    public void cacheAuthState(AuthState authState) {
        Log.i(TAG, "Caching auth state: " + authState);
        securePreferences.storeString("cached_auth_state", authState.toString());
        securePreferences.storeLong("auth_state_cache_time", System.currentTimeMillis());
    }

    /**
     * Clear cached auth state (when device registration changes)
     */
    public void clearCachedAuthState() {
        Log.i(TAG, "Clearing cached auth state");
        securePreferences.removeKey("cached_auth_state");
        securePreferences.removeKey("auth_state_cache_time");
    }
}