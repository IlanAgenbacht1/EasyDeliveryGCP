package com.clone.EasyDelivery.viewmodel;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.clone.EasyDelivery.Api.models.response.BaseApiResponse;
import com.clone.EasyDelivery.Api.services.AuthService;
import com.clone.EasyDelivery.Api.utils.ApiResponseHandler;
import com.clone.EasyDelivery.data.entities.DeviceEntity;
import com.clone.EasyDelivery.data.entities.SessionEntity;
import com.clone.EasyDelivery.models.Driver;
import com.clone.EasyDelivery.models.Vehicle;
import com.clone.EasyDelivery.repository.AuthRepository;
import com.clone.EasyDelivery.sync.models.SyncStatus;
import com.clone.EasyDelivery.util.PinUtils;
import com.clone.EasyDelivery.viewmodel.models.AuthState;
import com.clone.EasyDelivery.viewmodel.models.UiState;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * ViewModel for authentication and session management with proper Hilt DI
 * Handles device registration, driver authentication, and session lifecycle
 *
 * === Main Thread Database Access Fixes (2024-10) ===
 * Fixed critical Room "Cannot access database on the main thread" exceptions:
 *
 * 1. initializeAuthState() → initializeAuthStateAsync()
 *    - Uses authRepository.hasActiveSessionAsync() & isDeviceRegisteredAsync()
 *    - Sets safe default state immediately, determines actual state async
 *
 * 2. canProceedToAuthentication() → @Deprecated
 *    - Method marked deprecated to prevent future synchronous DB access
 *    - Activities should use AuthState LiveData observation instead
 *
 * 3. logout() method updated
 *    - Replaced hasActiveSession() with AuthState checking
 *    - Uses getCurrentSessionId() (SharedPrefs only, no DB access)
 *
 * 4. Added async repository methods:
 *    - AuthRepository.isDeviceRegisteredAsync()
 *    - AuthRepository.hasActiveSessionAsync() 
 *    - AuthRepository.getCurrentClientIdAsync()
 *
 * All database operations now run on background threads with proper error handling.
 * UI updates via postValue() from background threads, setValue() on main thread.
 * =========================================================================
 */
@HiltViewModel
public class AuthViewModel extends ViewModel {
    
    private static final String TAG = "AuthViewModel";

    // Application context for resource access
    private final Context applicationContext;

    // Repository - injected by Hilt
    private final AuthRepository authRepository;
    
    // LiveData for UI state
    private final MutableLiveData<AuthState> authStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> enrollmentKeyValidationState = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> deviceRegistrationState = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> driverAuthenticationState = new MutableLiveData<>();
    private final MutableLiveData<UiState<SessionEntity>> sessionState = new MutableLiveData<>();
    private final MutableLiveData<String> pinValidationMessage = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Driver>>> availableDriversState = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Vehicle>>> availableVehiclesState = new MutableLiveData<>();
    
    // Mediated LiveData for combined states
    private final MediatorLiveData<Boolean> isLoadingLiveData = new MediatorLiveData<>();
    private final MediatorLiveData<String> errorMessageLiveData = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

    @Inject
    public AuthViewModel(@ApplicationContext Context context, AuthRepository authRepository) {
        super();
        this.applicationContext = context;
        this.authRepository = authRepository;

        // Initialize with default state first to avoid blocking main thread
        // The actual state will be determined asynchronously
        authStateLiveData.setValue(AuthState.NOT_AUTHENTICATED);

        // Initialize auth state asynchronously to avoid Room main thread exception
        initializeAuthStateAsync();

        // Setup mediated LiveData
        setupMediatedData();

        Log.i(TAG, "AuthViewModel initialized with Hilt DI");
    }
    
    // =================== INITIALIZATION ===================

    private void initializeAuthStateAsync() {
        // Perform database checks using async methods
        authRepository.hasActiveSessionAsync().thenAccept(hasActiveSession -> {
            if (hasActiveSession) {
                authStateLiveData.postValue(AuthState.AUTHENTICATED);
            } else {
                // Check device registration if no active session
                authRepository.isDeviceRegisteredAsync().thenAccept(isRegistered -> {
                    if (isRegistered) {
                        authStateLiveData.postValue(AuthState.DEVICE_REGISTERED);
                    } else {
                        authStateLiveData.postValue(AuthState.NOT_AUTHENTICATED);
                    }
                }).exceptionally(throwable -> {
                    Log.e(TAG, "Error checking device registration", (Throwable) throwable);
                    authStateLiveData.postValue(AuthState.NOT_AUTHENTICATED);
                    return null;
                });
            }
        }).exceptionally(throwable -> {
            Log.e(TAG, "Error checking active session", (Throwable) throwable);
            authStateLiveData.postValue(AuthState.NOT_AUTHENTICATED);
            return null;
        });
    }
    
    private void setupMediatedData() {
        // Combine loading states
        isLoadingLiveData.addSource(enrollmentKeyValidationState, state -> 
            isLoadingLiveData.setValue(isAnyStateLoading()));
        isLoadingLiveData.addSource(deviceRegistrationState, state -> 
            isLoadingLiveData.setValue(isAnyStateLoading()));
        isLoadingLiveData.addSource(driverAuthenticationState, state -> 
            isLoadingLiveData.setValue(isAnyStateLoading()));
        isLoadingLiveData.addSource(sessionState, state -> 
            isLoadingLiveData.setValue(isAnyStateLoading()));
        isLoadingLiveData.addSource(availableDriversState, state ->
                isLoadingLiveData.setValue(isAnyStateLoading()));
        isLoadingLiveData.addSource(availableVehiclesState, state ->
                isLoadingLiveData.setValue(isAnyStateLoading()));
        
        // Combine error messages
        errorMessageLiveData.addSource(enrollmentKeyValidationState, this::updateErrorMessage);
        errorMessageLiveData.addSource(deviceRegistrationState, this::updateErrorMessage);
        errorMessageLiveData.addSource(driverAuthenticationState, this::updateErrorMessage);
        errorMessageLiveData.addSource(sessionState, this::updateErrorMessage);
        errorMessageLiveData.addSource(availableDriversState, this::updateErrorMessage);
        errorMessageLiveData.addSource(availableVehiclesState, this::updateErrorMessage);
    }
    
    private boolean isAnyStateLoading() {
        return isStateLoading(enrollmentKeyValidationState.getValue()) ||
               isStateLoading(deviceRegistrationState.getValue()) ||
               isStateLoading(driverAuthenticationState.getValue()) ||
                isStateLoading(sessionState.getValue()) ||
                isStateLoading(availableDriversState.getValue()) ||
                isStateLoading(availableVehiclesState.getValue());
    }
    
    private boolean isStateLoading(UiState<?> state) {
        return state != null && state.isLoading();
    }
    
    private void updateErrorMessage(UiState<?> state) {
        if (state != null && state.isError()) {
            errorMessageLiveData.setValue(state.getErrorMessage());
        }
    }
    
    // =================== DEVICE ENROLLMENT & REGISTRATION ===================
    
    /**
     * Validate enrollment key and automatically register device if successful
     */
    public void validateEnrollmentKey(String enrollmentKey) {
        Log.i(TAG, "Validating enrollment key");
        
        if (enrollmentKey == null || enrollmentKey.trim().isEmpty()) {
            enrollmentKeyValidationState.setValue(UiState.error("Enrollment key cannot be empty"));
            return;
        }
        
        enrollmentKeyValidationState.setValue(UiState.loading());
        
        authRepository.validateEnrollmentKey(enrollmentKey.trim())
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Enrollment key validation successful");

                        // Store client ID from the validation response
                        String clientId = result.getData().getClientId();
                        String clientName = result.getData().getClientName();

                        Log.i(TAG, "Setting client ID: " + clientId + " (" + clientName + ")");

                        enrollmentKeyValidationState.postValue(UiState.success("Enrollment key is valid - Registering device..."));

                        // Automatically trigger device registration
                        registerDeviceAutomatically(enrollmentKey.trim(), clientId, clientName);

                    } else {
                        Log.w(TAG, "Enrollment key validation failed: " + result.getErrorMessage());
                        enrollmentKeyValidationState.postValue(UiState.error(result.getErrorMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error validating enrollment key", throwable);
                    enrollmentKeyValidationState.postValue(UiState.error("Validation failed: " + throwable.getMessage()));
                    return null;
                });
    }
    
    /**
     * Register device automatically after successful enrollment validation
     */
    private void registerDeviceAutomatically(String enrollmentKey, String clientId, String clientName) {
        Log.i(TAG, "Automatically registering device for client: " + clientId);

        // Update UI to show registration in progress
        deviceRegistrationState.setValue(UiState.loading());
        enrollmentKeyValidationState.setValue(UiState.success("Registering device for " + clientName + "..."));

        authRepository.registerDevice(enrollmentKey)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Automatic device registration successful for client: " + clientId);

                        // Store the client ID permanently in secure preferences
                        authRepository.setClientId(clientId, clientName);

                        deviceRegistrationState.postValue(UiState.success("Device registered successfully for " + clientName));
                        enrollmentKeyValidationState.postValue(UiState.success("Setup complete! Ready for driver authentication."));

                        // Update auth state to reflect successful registration
                        authStateLiveData.postValue(AuthState.DEVICE_REGISTERED);

                    } else {
                        Log.w(TAG, "Automatic device registration failed: " + result.getErrorMessage());
                        deviceRegistrationState.postValue(UiState.error(result.getErrorMessage()));
                        enrollmentKeyValidationState.postValue(UiState.error("Registration failed: " + result.getErrorMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error in automatic device registration", throwable);
                    deviceRegistrationState.postValue(UiState.error("Registration failed: " + throwable.getMessage()));
                    enrollmentKeyValidationState.postValue(UiState.error("Registration failed: " + throwable.getMessage()));
                    return null;
                });
    }

    /**
     * Manual device registration (kept for backwards compatibility)
     */
    public void registerDevice(String enrollmentKey) {
        Log.i(TAG, "Manual device registration requested");
        registerDeviceAutomatically(enrollmentKey, "manual", "Manual Registration");
    }

    // =================== DRIVER AUTHENTICATION ===================
    
    /**
     * Validate PIN format and strength
     */
    public void validatePinInput(String pin) {
        if (pin == null || pin.isEmpty()) {
            pinValidationMessage.setValue("");
            return;
        }
        
        if (!PinUtils.isValidPinFormat(pin)) {
            pinValidationMessage.setValue("PIN must be 4-6 digits");
            return;
        }
        
        String strengthMessage = PinUtils.getPinStrengthDescription(pin);
        pinValidationMessage.setValue(strengthMessage);
    }
    
    /**
     * Authenticate driver with PIN
     */
    public void authenticateDriver(String driverId, String pin) {
        Log.i(TAG, "Authenticating driver: " + driverId);
        
        if (driverId == null || driverId.trim().isEmpty()) {
            driverAuthenticationState.setValue(UiState.error("Driver ID cannot be empty"));
            return;
        }
        
        if (!PinUtils.isValidPinFormat(pin)) {
            driverAuthenticationState.setValue(UiState.error("Invalid PIN format"));
            return;
        }
        
        driverAuthenticationState.setValue(UiState.loading());
        
        authRepository.authenticateDriver(driverId.trim(), pin)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Driver authentication successful");
                        driverAuthenticationState.postValue(UiState.success("Authentication successful"));
                        authStateLiveData.postValue(AuthState.AUTHENTICATED);
                    } else {
                        Log.w(TAG, "Driver authentication failed: " + result.getErrorMessage());
                        driverAuthenticationState.postValue(UiState.error(result.getErrorMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error authenticating driver", throwable);
                    driverAuthenticationState.postValue(UiState.error("Authentication failed: " + throwable.getMessage()));
                    return null;
                });
    }

    /**
     * Setup PIN for first-time driver
     */
    public void setupDriverPin(String driverId, String pin) {
        Log.i(TAG, "Setting up PIN for driver: " + driverId);

        CompletableFuture<ApiResponseHandler.ApiResult<BaseApiResponse>> future = authRepository.setupDriverPin(driverId, pin);

        future.thenAccept(result -> {
            if (result.isSuccess()) {
                Log.i(TAG, "PIN setup successful");
                // After PIN setup, the driver is now authenticated
                driverAuthenticationState.postValue(UiState.success("PIN setup successful"));
            } else {
                Log.w(TAG, "PIN setup failed: " + result.getErrorMessage());
                driverAuthenticationState.postValue(UiState.error(result.getErrorMessage()));
            }
        }).exceptionally(throwable -> {
            Log.e(TAG, "PIN setup error", throwable);
            driverAuthenticationState.postValue(UiState.error("PIN setup failed: " + throwable.getMessage()));
            return null;
        });
    }

    // =================== DRIVER SELECTION ===================

    public void loadAvailableDrivers() {
        Log.i(TAG, "Loading available drivers");
        availableDriversState.setValue(UiState.loading());
        authRepository.getAvailableDrivers()
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Available drivers loaded successfully");
                        List<Driver> drivers = result.getData().getDrivers();
                        availableDriversState.postValue(UiState.success(drivers));
                    } else {
                        Log.w(TAG, "Failed to load available drivers: " + result.getErrorMessage());
                        availableDriversState.postValue(UiState.error(result.getErrorMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error loading available drivers", throwable);
                    availableDriversState.postValue(UiState.error("Failed to load drivers: " + throwable.getMessage()));
                    return null;
                });
    }

    // =================== VEHICLE SELECTION ===================

    public void loadAvailableVehicles() {
        Log.i(TAG, "Loading available vehicles");
        availableVehiclesState.setValue(UiState.loading());
        authRepository.getAvailableVehicles()
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Available vehicles loaded successfully");
                        AuthService.VehiclesResponse vehiclesResponse = result.getData();
                        List<Vehicle> vehicles = vehiclesResponse.getVehicles();
                        availableVehiclesState.postValue(UiState.success(vehicles));
                    } else {
                        Log.w(TAG, "Failed to load available vehicles: " + result.getErrorMessage());
                        availableVehiclesState.postValue(UiState.error(result.getErrorMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error loading available vehicles", throwable);
                    availableVehiclesState.postValue(UiState.error("Failed to load vehicles: " + throwable.getMessage()));
                    return null;
                });
    }

    // =================== SESSION MANAGEMENT ===================
    
    /**
     * Start a new session with selected vehicle
     */
    public void startSession(String vehicleId) {
        Log.i(TAG, "Starting new session with vehicle: " + vehicleId);
        
        sessionState.setValue(UiState.loading());

        authRepository.startSession(vehicleId)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Session started successfully");
                        sessionState.postValue(UiState.success(result.getData()));
                        authStateLiveData.postValue(AuthState.SESSION_ACTIVE);
                    } else {
                        Log.w(TAG, "Session start failed: " + result.getErrorMessage());
                        sessionState.postValue(UiState.error(result.getErrorMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error starting session", throwable);
                    sessionState.postValue(UiState.error("Failed to start session: " + throwable.getMessage()));
                    return null;
                });
    }
    
    /**
     * End current session
     */
    public void endSession() {
        Log.i(TAG, "Ending current session");
        
        sessionState.setValue(UiState.loading());
        
        authRepository.endSession()
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Session ended successfully");
                        sessionState.postValue(UiState.success(null));
                        authStateLiveData.postValue(AuthState.AUTHENTICATED);
                    } else {
                        Log.w(TAG, "Session end failed: " + result.getErrorMessage());
                        sessionState.postValue(UiState.error(result.getErrorMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error ending session", throwable);
                    sessionState.postValue(UiState.error("Failed to end session: " + throwable.getMessage()));
                    return null;
                });
    }
    
    /**
     * Logout (clear authentication and cleanup)
     */
    public void logout() {
        Log.i(TAG, "Logging out");

        // Check current auth state instead of making synchronous DB call
        AuthState currentState = authStateLiveData.getValue();

        // End session if we appear to have an active session
        if (currentState == AuthState.SESSION_ACTIVE ||
                (getCurrentSessionId() != null && !getCurrentSessionId().isEmpty())) {
            endSession();
        }
        
        // Cleanup sync services
        authRepository.cleanupSyncServices();
        
        // Reset auth state
        authStateLiveData.setValue(AuthState.NOT_AUTHENTICATED);
        
        // Clear all states
        clearAllStates();
    }
    
    private void clearAllStates() {
        enrollmentKeyValidationState.setValue(UiState.idle());
        deviceRegistrationState.setValue(UiState.idle());
        driverAuthenticationState.setValue(UiState.idle());
        sessionState.setValue(UiState.idle());
        pinValidationMessage.setValue("");
        availableDriversState.setValue(UiState.idle());
        availableVehiclesState.setValue(UiState.idle());
    }
    
    // =================== SYNC STATUS ===================
    
    /**
     * Get sync status for UI display
     */
    public LiveData<SyncStatus> getSyncStatus() {
        return authRepository.getSyncStatus();
    }
    
    /**
     * Get sync progress for UI display
     */
    public LiveData<String> getSyncProgress() {
        return authRepository.getSyncProgress();
    }
    
    /**
     * Force manual sync
     */
    public void forceSync() {
        Log.i(TAG, "Force sync requested by user");
        authRepository.forceSyncNow();
    }
    
    /**
     * Check if sync is in progress
     */
    public boolean isSyncInProgress() {
        return authRepository.isSyncInProgress();
    }
    
    // =================== GETTERS FOR UI STATE ===================
    
    public LiveData<AuthState> getAuthState() {
        return authStateLiveData;
    }
    
    public LiveData<UiState<String>> getEnrollmentKeyValidationState() {
        return enrollmentKeyValidationState;
    }
    
    public LiveData<UiState<String>> getDeviceRegistrationState() {
        return deviceRegistrationState;
    }
    
    public LiveData<UiState<String>> getDriverAuthenticationState() {
        return driverAuthenticationState;
    }
    
    public LiveData<UiState<SessionEntity>> getSessionState() {
        return sessionState;
    }
    
    public LiveData<String> getPinValidationMessage() {
        return pinValidationMessage;
    }

    public LiveData<UiState<List<Driver>>> getAvailableDriversState() {
        return availableDriversState;
    }

    public LiveData<UiState<List<Vehicle>>> getAvailableVehiclesState() {
        return availableVehiclesState;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoadingLiveData;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessageLiveData;
    }
    
    /**
     * Get device info for UI display
     */
    public LiveData<DeviceEntity> getDeviceInfo() {
        return authRepository.getDeviceLiveData();
    }
    
    /**
     * Get current session info
     */
    public String getCurrentSessionId() {
        return authRepository.getCurrentSessionId();
    }
    
    public String getCurrentDriverId() {
        return authRepository.getAuthenticatedDriverId();
    }
    
    public long getLastSyncTime() {
        return authRepository.getLastSyncTime();
    }
    
    // =================== UTILITY METHODS ===================
    
    /**
     * Reset specific state (for UI cleanup)
     */
    public void resetEnrollmentState() {
        enrollmentKeyValidationState.setValue(UiState.idle());
    }
    
    public void resetRegistrationState() {
        deviceRegistrationState.setValue(UiState.idle());
    }
    
    public void resetAuthenticationState() {
        driverAuthenticationState.setValue(UiState.idle());
    }
    
    public void resetSessionState() {
        sessionState.setValue(UiState.idle());
    }

    public void resetAvailableDriversState() {
        availableDriversState.setValue(UiState.idle());
    }

    public void resetAvailableVehiclesState() {
        availableVehiclesState.setValue(UiState.idle());
    }

    /**
     * Check if user can proceed to next step
     */
    public boolean canProceedToRegistration() {
        UiState<String> state = enrollmentKeyValidationState.getValue();
        return state != null && state.isSuccess();
    }

    /**
     * @deprecated This method performs synchronous database access on main thread.
     * Use AuthState LiveData observation instead via getAuthState().
     * Will be removed in future version.
     */
    @Deprecated
    public boolean canProceedToAuthentication() {
        return authRepository.isDeviceRegistered();
    }
    
    public boolean canStartSession() {
        AuthState state = authStateLiveData.getValue();
        return state == AuthState.AUTHENTICATED;
    }
}