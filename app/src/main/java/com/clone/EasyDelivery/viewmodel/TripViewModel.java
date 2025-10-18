package com.clone.EasyDelivery.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.clone.EasyDelivery.data.entities.TripEntity;
import com.clone.EasyDelivery.repository.TripRepository;
import com.clone.EasyDelivery.repository.utils.Resource;
import com.clone.EasyDelivery.sync.models.SyncStatus;
import com.clone.EasyDelivery.viewmodel.models.UiState;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for Trip management with Hilt dependency injection
 * Provides offline-first data access with modern Android architecture
 */
@HiltViewModel
public class TripViewModel extends ViewModel {
    
    private static final String TAG = "TripViewModel";
    
    // Repository injected by Hilt
    private final TripRepository tripRepository;
    
    // LiveData for trip operations
    private final MutableLiveData<UiState<List<TripEntity>>> tripsStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<UiState<TripEntity>> currentTripStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> currentDriverId = new MutableLiveData<>();
    
    // Derived data for UI compatibility
    private final MediatorLiveData<ArrayList<String>> tripIdsForAdapter = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> isLoadingLiveData = new MediatorLiveData<>();
    
    // Current state tracking
    private String selectedTripId = null;
    
    @Inject
    public TripViewModel(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
        
        // Initialize with idle state
        tripsStateLiveData.setValue(UiState.idle());
        currentTripStateLiveData.setValue(UiState.idle());
        
        // Setup derived data
        setupDerivedData();
        
        Log.i(TAG, "TripViewModel initialized with Hilt DI");
    }
    
    private void setupDerivedData() {
        // Convert trip entities to trip IDs for adapter compatibility
        tripIdsForAdapter.addSource(tripsStateLiveData, tripsState -> {
            if (tripsState != null && tripsState.isSuccess() && tripsState.hasData()) {
                List<TripEntity> trips = tripsState.getData();
                ArrayList<String> tripIds = new ArrayList<>();
                for (TripEntity trip : trips) {
                    tripIds.add(trip.getTripId());
                }
                tripIdsForAdapter.setValue(tripIds);
                Log.d(TAG, "Updated trip IDs for adapter: " + tripIds.size() + " trips");
            } else {
                tripIdsForAdapter.setValue(new ArrayList<>());
            }
        });
        
        // Combine loading states
        isLoadingLiveData.addSource(tripsStateLiveData, state -> 
            isLoadingLiveData.setValue(isAnyStateLoading()));
        isLoadingLiveData.addSource(currentTripStateLiveData, state -> 
            isLoadingLiveData.setValue(isAnyStateLoading()));
    }
    
    private boolean isAnyStateLoading() {
        return isStateLoading(tripsStateLiveData.getValue()) || 
               isStateLoading(currentTripStateLiveData.getValue());
    }
    
    private boolean isStateLoading(UiState<?> state) {
        return state != null && state.isLoading();
    }
    
    // =================== TRIP DATA LOADING ===================
    
    /**
     * Load trips for current driver
     * Adapts new repository pattern to existing UI expectations
     */
    public void loadTripsForDriver(String driverId, boolean forceRefresh) {
        Log.i(TAG, "Loading trips for driver: " + driverId + ", forceRefresh: " + forceRefresh);
        
        if (driverId == null || driverId.trim().isEmpty()) {
            tripsStateLiveData.setValue(UiState.error("Driver ID is required"));
            return;
        }
        
        currentDriverId.setValue(driverId);
        tripsStateLiveData.setValue(UiState.loading());
        
        // Use new repository with offline-first approach
        LiveData<Resource<List<TripEntity>>> tripsResource = 
            tripRepository.getTripsForDriver(driverId, forceRefresh);
        
        // Observe and transform to UI state
        tripsResource.observeForever(resource -> {
            if (resource != null) {
                switch (resource.getStatus()) {
                    case SUCCESS:
                        if (resource.getData() != null) {
                            Log.d(TAG, "Trips loaded successfully: " + resource.getData().size() + " trips");
                            tripsStateLiveData.postValue(UiState.success(resource.getData()));
                        } else {
                            tripsStateLiveData.postValue(UiState.success(new ArrayList<>()));
                        }
                        break;
                        
                    case ERROR:
                        Log.w(TAG, "Error loading trips: " + resource.getMessage());
                        tripsStateLiveData.postValue(UiState.error(resource.getMessage()));
                        break;
                        
                    case LOADING:
                        // Keep current loading state
                        break;
                }
            }
        });
    }
    
    /**
     * Get trips formatted for existing TripAdapter
     * Maintains compatibility with current UI implementation
     */
    public LiveData<ArrayList<String>> getTripIdsForAdapter() {
        return tripIdsForAdapter;
    }
    
    /**
     * Check if we have trip data (for UI state management)
     */
    public boolean hasTripsData() {
        UiState<List<TripEntity>> state = tripsStateLiveData.getValue();
        return state != null && state.isSuccess() && state.hasData() && !state.getData().isEmpty();
    }
    
    /**
     * Get trips count for UI display
     */
    public int getTripsCount() {
        UiState<List<TripEntity>> state = tripsStateLiveData.getValue();
        if (state != null && state.isSuccess() && state.hasData()) {
            return state.getData().size();
        }
        return 0;
    }
    
    // =================== TRIP OPERATIONS ===================
    
    /**
     * Start a trip - adapts new repository to existing UI flow
     */
    public void startTrip(String tripId) {
        Log.i(TAG, "Starting trip: " + tripId);
        
        if (tripId == null || tripId.trim().isEmpty()) {
            currentTripStateLiveData.setValue(UiState.error("Trip ID cannot be empty"));
            return;
        }
        
        selectedTripId = tripId;
        currentTripStateLiveData.setValue(UiState.loading());
        
        // Use new repository for offline-first trip start
        tripRepository.startTrip(tripId)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Trip started successfully: " + tripId);
                        currentTripStateLiveData.postValue(UiState.success(result.getData()));
                    } else {
                        Log.w(TAG, "Failed to start trip: " + result.getMessage());
                        currentTripStateLiveData.postValue(UiState.error(result.getMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error starting trip: " + tripId, throwable);
                    currentTripStateLiveData.postValue(UiState.error("Failed to start trip: " + throwable.getMessage()));
                    return null;
                });
    }
    
    /**
     * Complete current trip
     */
    public void completeTrip(String tripId) {
        Log.i(TAG, "Completing trip: " + tripId);
        
        currentTripStateLiveData.setValue(UiState.loading());
        
        tripRepository.completeTrip(tripId)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Trip completed successfully: " + tripId);
                        currentTripStateLiveData.postValue(UiState.success(result.getData()));
                        // Refresh trips list to reflect changes
                        refreshTrips();
                    } else {
                        Log.w(TAG, "Failed to complete trip: " + result.getMessage());
                        currentTripStateLiveData.postValue(UiState.error(result.getMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error completing trip: " + tripId, throwable);
                    currentTripStateLiveData.postValue(UiState.error("Failed to complete trip: " + throwable.getMessage()));
                    return null;
                });
    }
    
    /**
     * Record return item for trip (for ReturnDash activity)
     */
    public void recordReturnItem(String tripId, Object returnData) {
        Log.i(TAG, "Recording return item for trip: " + tripId);
        // TODO: Implement proper return item recording logic
        // For now, just log the action to prevent compilation errors
        Log.d(TAG, "Return item recorded for trip: " + tripId + ", data: " + returnData);
    }

    /**
     * Get active trip for driver
     */
    public LiveData<TripEntity> getActiveTrip(String driverId) {
        Log.d(TAG, "Getting active trip for driver: " + driverId);
        return tripRepository.getActiveTrip(driverId);
    }
    
    // =================== UI STATE MANAGEMENT ===================
    
    /**
     * Refresh trips (force sync)
     */
    public void refreshTrips() {
        String driverId = currentDriverId.getValue();
        if (driverId != null) {
            Log.i(TAG, "Refreshing trips for driver: " + driverId);
            loadTripsForDriver(driverId, true); // Force refresh
        }
    }
    
    /**
     * Force sync trips with server
     */
    public void forceSyncTrips() {
        String driverId = currentDriverId.getValue();
        if (driverId != null) {
            Log.i(TAG, "Force syncing trips for driver: " + driverId);
            tripRepository.forceSyncTrips(driverId);
        }
    }
    
    /**
     * Get current trip state for UI
     */
    public LiveData<UiState<TripEntity>> getCurrentTripState() {
        return currentTripStateLiveData;
    }
    
    /**
     * Get trips loading state
     */
    public LiveData<UiState<List<TripEntity>>> getTripsState() {
        return tripsStateLiveData;
    }
    
    /**
     * Check if any operation is loading
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoadingLiveData;
    }
    
    /**
     * Get current error message for UI
     */
    public String getCurrentErrorMessage() {
        UiState<List<TripEntity>> tripsState = tripsStateLiveData.getValue();
        if (tripsState != null && tripsState.isError()) {
            return tripsState.getErrorMessage();
        }
        
        UiState<TripEntity> tripState = currentTripStateLiveData.getValue();
        if (tripState != null && tripState.isError()) {
            return tripState.getErrorMessage();
        }
        
        return null;
    }
    
    /**
     * Clear current trip selection
     */
    public void clearCurrentTrip() {
        selectedTripId = null;
        currentTripStateLiveData.setValue(UiState.idle());
    }
    
    /**
     * Get selected trip ID
     */
    public String getSelectedTripId() {
        return selectedTripId;
    }
    
    // =================== COMPATIBILITY METHODS ===================
    
    /**
     * Get connectivity status for UI (adapts to existing patterns)
     * Note: This should ideally inject ConnectionManager, but for compatibility we keep it simple
     */
    public boolean isConnected() {
        // TODO: Implement proper connectivity check via NetworkModule/ConnectionManager
        return true; // Placeholder - should check actual network status
    }
    
    /**
     * Get current trip ID (for compatibility with Activities)
     */
    public String getCurrentTripId() {
        TripEntity currentTrip = getCurrentTripEntity();
        return currentTrip != null ? currentTrip.getTripId() : selectedTripId;
    }
    
    /**
     * Get current trip entity
     */
    private TripEntity getCurrentTripEntity() {
        UiState<TripEntity> state = currentTripStateLiveData.getValue();
        return (state != null && state.isSuccess()) ? state.getData() : null;
    }
    
    /**
     * Load trips for driver with single parameter for compatibility
     */
    public void loadTripsForDriver(String driverId) {
        loadTripsForDriver(driverId, false);
    }
    
    /**
     * Get sync status for offline indicators
     * Uses repository sync status when available
     */
    public LiveData<SyncStatus> getSyncStatus() {
        // Try to get actual sync status from repository
        try {
            return tripRepository.getSyncStatus();
        } catch (Exception e) {
            Log.w(TAG, "Could not get repository sync status, using fallback");
            MutableLiveData<SyncStatus> syncStatus = new MutableLiveData<>();
            syncStatus.setValue(SyncStatus.IDLE);
            return syncStatus;
        }
    }
    
    /**
     * Get sync progress for UI display
     * Uses repository sync progress when available
     */
    public LiveData<String> getSyncProgress() {
        try {
            return tripRepository.getSyncProgress();
        } catch (Exception e) {
            Log.w(TAG, "Could not get repository sync progress, using fallback");
            MutableLiveData<String> syncProgress = new MutableLiveData<>();
            syncProgress.setValue("Ready");
            return syncProgress;
        }
    }
    
    /**
     * Compatibility method for existing UI patterns
     * Converts new trip entities to old format expectations
     */
    public List<String> getCurrentTripIds() {
        UiState<List<TripEntity>> state = tripsStateLiveData.getValue();
        if (state != null && state.isSuccess() && state.hasData()) {
            List<String> tripIds = new ArrayList<>();
            for (TripEntity trip : state.getData()) {
                tripIds.add(trip.getTripId());
            }
            return tripIds;
        }
        return new ArrayList<>();
    }
    
    // =================== STATISTICS & DEBUGGING ===================
    
    /**
     * Get trip statistics for debugging/monitoring
     */
    public void logTripStats(String driverId) {
        tripRepository.getTripStats(driverId)
                .thenAccept(stats -> {
                    Log.i(TAG, "Trip Stats for " + driverId + ": " + stats.toString());
                });
    }
    
    /**
     * Get unsynced trips for debugging
     */
    public LiveData<List<TripEntity>> getUnsyncedTrips() {
        return tripRepository.getUnsyncedTrips();
    }
    
    /**
     * Select trip by ID (updates current trip state)
     */
    public void selectTrip(String tripId) {
        Log.i(TAG, "Selecting trip: " + tripId);
        selectedTripId = tripId;
        
        // Load trip data when selected
        loadTripById(tripId);
    }
    
    /**
     * Load specific trip by ID
     */
    private void loadTripById(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            currentTripStateLiveData.setValue(UiState.error("Trip ID is required"));
            return;
        }
        
        currentTripStateLiveData.setValue(UiState.loading());

        LiveData<Resource<TripEntity>> tripResource = tripRepository.getTripById(tripId, false);
        tripResource.observeForever(resource -> {
            if (resource != null) {
                switch (resource.getStatus()) {
                    case SUCCESS:
                        Log.d(TAG, "Trip loaded successfully: " + tripId);
                        currentTripStateLiveData.postValue(UiState.success(resource.getData()));
                        break;
                    case ERROR:
                        Log.w(TAG, "Error loading trip: " + resource.getMessage());
                        currentTripStateLiveData.postValue(UiState.error(resource.getMessage()));
                        break;
                    case LOADING:
                        // Keep current loading state
                        break;
                }
            }
        });
    }
}