package com.clone.EasyDelivery.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.clone.EasyDelivery.data.entities.DeliveryEntity;
import com.clone.EasyDelivery.repository.DeliveryRepository;
import com.clone.EasyDelivery.repository.utils.Resource;
import com.clone.EasyDelivery.sync.models.SyncStatus;
import com.clone.EasyDelivery.viewmodel.models.UiState;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for Delivery management with Hilt dependency injection
 * Provides offline-first delivery operations with modern Android architecture
 */
@HiltViewModel
public class DeliveryViewModel extends ViewModel {
    
    private static final String TAG = "DeliveryViewModel";
    
    // Repository injected by Hilt
    private final DeliveryRepository deliveryRepository;
    
    // LiveData for delivery operations
    private final MutableLiveData<UiState<List<DeliveryEntity>>> deliveriesStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<UiState<DeliveryEntity>> currentDeliveryStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> currentTripId = new MutableLiveData<>();
    
    // Derived data for UI compatibility
    private final MediatorLiveData<Boolean> isLoadingLiveData = new MediatorLiveData<>();
    
    // Current state tracking
    private String selectedDeliveryId = null;
    
    @Inject
    public DeliveryViewModel(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
        
        // Initialize with idle state
        deliveriesStateLiveData.setValue(UiState.idle());
        currentDeliveryStateLiveData.setValue(UiState.idle());
        
        // Setup derived data
        setupDerivedData();
        
        Log.i(TAG, "DeliveryViewModel initialized with Hilt DI");
    }
    
    private void setupDerivedData() {
        // Combine loading states
        isLoadingLiveData.addSource(deliveriesStateLiveData, state -> 
            isLoadingLiveData.setValue(isAnyStateLoading()));
        isLoadingLiveData.addSource(currentDeliveryStateLiveData, state -> 
            isLoadingLiveData.setValue(isAnyStateLoading()));
    }
    
    private boolean isAnyStateLoading() {
        return isStateLoading(deliveriesStateLiveData.getValue()) || 
               isStateLoading(currentDeliveryStateLiveData.getValue());
    }
    
    private boolean isStateLoading(UiState<?> state) {
        return state != null && state.isLoading();
    }
    
    // =================== DELIVERY DATA LOADING ===================
    
    /**
     * Load deliveries for a specific trip
     * Adapts new repository pattern to existing UI expectations
     */
    public void loadDeliveriesForTrip(String tripId, boolean forceRefresh) {
        Log.i(TAG, "Loading deliveries for trip: " + tripId + ", forceRefresh: " + forceRefresh);
        
        if (tripId == null || tripId.trim().isEmpty()) {
            deliveriesStateLiveData.setValue(UiState.error("Trip ID is required"));
            return;
        }
        
        currentTripId.setValue(tripId);
        deliveriesStateLiveData.setValue(UiState.loading());
        
        // Use new repository with offline-first approach
        LiveData<Resource<List<DeliveryEntity>>> deliveriesResource = 
            deliveryRepository.getDeliveriesForTrip(tripId, forceRefresh);
        
        // Observe and transform to UI state
        deliveriesResource.observeForever(resource -> {
            if (resource != null) {
                switch (resource.getStatus()) {
                    case SUCCESS:
                        if (resource.getData() != null) {
                            Log.d(TAG, "Deliveries loaded successfully: " + resource.getData().size() + " deliveries");
                            deliveriesStateLiveData.postValue(UiState.success(resource.getData()));
                        } else {
                            deliveriesStateLiveData.postValue(UiState.success(new ArrayList<>()));
                        }
                        break;
                        
                    case ERROR:
                        Log.w(TAG, "Error loading deliveries: " + resource.getMessage());
                        deliveriesStateLiveData.postValue(UiState.error(resource.getMessage()));
                        break;
                        
                    case LOADING:
                        // Keep current loading state
                        break;
                }
            }
        });
    }
    
    /**
     * Get single delivery by ID
     */
    public void loadDeliveryById(String deliveryId, boolean forceRefresh) {
        Log.i(TAG, "Loading delivery by ID: " + deliveryId);
        
        if (deliveryId == null || deliveryId.trim().isEmpty()) {
            currentDeliveryStateLiveData.setValue(UiState.error("Delivery ID is required"));
            return;
        }
        
        selectedDeliveryId = deliveryId;
        currentDeliveryStateLiveData.setValue(UiState.loading());
        
        LiveData<Resource<DeliveryEntity>> deliveryResource = 
            deliveryRepository.getDeliveryById(deliveryId, forceRefresh);
        
        deliveryResource.observeForever(resource -> {
            if (resource != null) {
                switch (resource.getStatus()) {
                    case SUCCESS:
                        Log.d(TAG, "Delivery loaded successfully: " + deliveryId);
                        currentDeliveryStateLiveData.postValue(UiState.success(resource.getData()));
                        break;
                        
                    case ERROR:
                        Log.w(TAG, "Error loading delivery: " + resource.getMessage());
                        currentDeliveryStateLiveData.postValue(UiState.error(resource.getMessage()));
                        break;
                        
                    case LOADING:
                        // Keep current loading state
                        break;
                }
            }
        });
    }
    
    // =================== DELIVERY OPERATIONS ===================
    
    /**
     * Mark delivery as delivered with proof
     */
    public void markAsDelivered(String deliveryId, String deliveryNotes, String proofOfDeliveryPath) {
        Log.i(TAG, "Marking delivery as delivered: " + deliveryId);
        
        currentDeliveryStateLiveData.setValue(UiState.loading());
        
        deliveryRepository.markAsDelivered(deliveryId, deliveryNotes, proofOfDeliveryPath)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Delivery marked as delivered successfully: " + deliveryId);
                        currentDeliveryStateLiveData.postValue(UiState.success(result.getData()));
                        // Refresh deliveries list to reflect changes
                        refreshDeliveries();
                    } else {
                        Log.w(TAG, "Failed to mark delivery as delivered: " + result.getMessage());
                        currentDeliveryStateLiveData.postValue(UiState.error(result.getMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error marking delivery as delivered: " + deliveryId, throwable);
                    currentDeliveryStateLiveData.postValue(UiState.error("Failed to mark as delivered: " + throwable.getMessage()));
                    return null;
                });
    }
    
    /**
     * Mark delivery as failed
     */
    public void markAsFailed(String deliveryId, String failureReason) {
        Log.i(TAG, "Marking delivery as failed: " + deliveryId);
        
        currentDeliveryStateLiveData.setValue(UiState.loading());
        
        deliveryRepository.markAsFailed(deliveryId, failureReason)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Delivery marked as failed successfully: " + deliveryId);
                        currentDeliveryStateLiveData.postValue(UiState.success(result.getData()));
                        // Refresh deliveries list to reflect changes
                        refreshDeliveries();
                    } else {
                        Log.w(TAG, "Failed to mark delivery as failed: " + result.getMessage());
                        currentDeliveryStateLiveData.postValue(UiState.error(result.getMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error marking delivery as failed: " + deliveryId, throwable);
                    currentDeliveryStateLiveData.postValue(UiState.error("Failed to mark as failed: " + throwable.getMessage()));
                    return null;
                });
    }
    
    /**
     * Start delivery attempt
     */
    public void startDelivery(String deliveryId) {
        Log.i(TAG, "Starting delivery: " + deliveryId);
        
        currentDeliveryStateLiveData.setValue(UiState.loading());
        
        deliveryRepository.startDelivery(deliveryId)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Delivery started successfully: " + deliveryId);
                        currentDeliveryStateLiveData.postValue(UiState.success(result.getData()));
                    } else {
                        Log.w(TAG, "Failed to start delivery: " + result.getMessage());
                        currentDeliveryStateLiveData.postValue(UiState.error(result.getMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error starting delivery: " + deliveryId, throwable);
                    currentDeliveryStateLiveData.postValue(UiState.error("Failed to start delivery: " + throwable.getMessage()));
                    return null;
                });
    }
    
    /**
     * Update delivery details (for existing Dash activity integration)
     */
    public void updateDelivery(DeliveryEntity delivery) {
        Log.i(TAG, "Updating delivery: " + delivery.getDeliveryId());
        
        currentDeliveryStateLiveData.setValue(UiState.loading());
        
        deliveryRepository.updateDelivery(delivery)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Delivery updated successfully: " + delivery.getDeliveryId());
                        currentDeliveryStateLiveData.postValue(UiState.success(result.getData()));
                    } else {
                        Log.w(TAG, "Failed to update delivery: " + result.getMessage());
                        currentDeliveryStateLiveData.postValue(UiState.error(result.getMessage()));
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error updating delivery: " + delivery.getDeliveryId(), throwable);
                    currentDeliveryStateLiveData.postValue(UiState.error("Failed to update delivery: " + throwable.getMessage()));
                    return null;
                });
    }
    
    // =================== QUERY OPERATIONS ===================
    
    /**
     * Get deliveries by status for trip
     */
    public LiveData<List<DeliveryEntity>> getDeliveriesByStatus(String tripId, String status) {
        Log.d(TAG, "Getting deliveries by status - Trip: " + tripId + ", Status: " + status);
        return deliveryRepository.getDeliveriesByStatus(tripId, status);
    }
    
    /**
     * Get pending deliveries
     */
    public LiveData<List<DeliveryEntity>> getPendingDeliveries(String tripId) {
        return deliveryRepository.getPendingDeliveries(tripId);
    }
    
    /**
     * Get completed deliveries
     */
    public LiveData<List<DeliveryEntity>> getCompletedDeliveries(String tripId) {
        return deliveryRepository.getCompletedDeliveries(tripId);
    }
    
    /**
     * Search deliveries by customer name
     */
    public LiveData<List<DeliveryEntity>> searchDeliveriesByCustomer(String customerName) {
        return deliveryRepository.searchDeliveriesByCustomer(customerName);
    }
    
    /**
     * Search deliveries by address
     */
    public LiveData<List<DeliveryEntity>> searchDeliveriesByAddress(String address) {
        return deliveryRepository.searchDeliveriesByAddress(address);
    }
    
    // =================== UI STATE MANAGEMENT ===================
    
    /**
     * Get deliveries state for UI
     */
    public LiveData<UiState<List<DeliveryEntity>>> getDeliveriesState() {
        return deliveriesStateLiveData;
    }
    
    /**
     * Get current delivery state for UI
     */
    public LiveData<UiState<DeliveryEntity>> getCurrentDeliveryState() {
        return currentDeliveryStateLiveData;
    }
    
    /**
     * Check if any operation is loading
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoadingLiveData;
    }
    
    /**
     * Check if we have deliveries data
     */
    public boolean hasDeliveriesData() {
        UiState<List<DeliveryEntity>> state = deliveriesStateLiveData.getValue();
        return state != null && state.isSuccess() && state.hasData() && !state.getData().isEmpty();
    }
    
    /**
     * Get deliveries count for UI display
     */
    public int getDeliveriesCount() {
        UiState<List<DeliveryEntity>> state = deliveriesStateLiveData.getValue();
        if (state != null && state.isSuccess() && state.hasData()) {
            return state.getData().size();
        }
        return 0;
    }
    
    /**
     * Refresh deliveries (force sync)
     */
    public void refreshDeliveries() {
        String tripId = currentTripId.getValue();
        if (tripId != null) {
            Log.i(TAG, "Refreshing deliveries for trip: " + tripId);
            loadDeliveriesForTrip(tripId, true); // Force refresh
        }
    }
    
    /**
     * Force sync deliveries with server
     */
    public void forceSyncDeliveries() {
        String tripId = currentTripId.getValue();
        if (tripId != null) {
            Log.i(TAG, "Force syncing deliveries for trip: " + tripId);
            deliveryRepository.forceSyncDeliveries(tripId);
        }
    }
    
    /**
     * Get current error message for UI
     */
    public String getCurrentErrorMessage() {
        UiState<List<DeliveryEntity>> deliveriesState = deliveriesStateLiveData.getValue();
        if (deliveriesState != null && deliveriesState.isError()) {
            return deliveriesState.getErrorMessage();
        }
        
        UiState<DeliveryEntity> deliveryState = currentDeliveryStateLiveData.getValue();
        if (deliveryState != null && deliveryState.isError()) {
            return deliveryState.getErrorMessage();
        }
        
        return null;
    }
    
    /**
     * Clear current delivery selection
     */
    public void clearCurrentDelivery() {
        selectedDeliveryId = null;
        currentDeliveryStateLiveData.setValue(UiState.idle());
    }
    
    /**
     * Get selected delivery ID
     */
    public String getSelectedDeliveryId() {
        return selectedDeliveryId;
    }
    
    // =================== SYNC STATUS ===================
    
    /**
     * Get sync status for offline indicators
     */
    public LiveData<SyncStatus> getSyncStatus() {
        MutableLiveData<SyncStatus> syncStatus = new MutableLiveData<>();
        syncStatus.setValue(SyncStatus.IDLE); // Placeholder
        return syncStatus;
    }
    
    /**
     * Get sync progress for UI display
     */
    public LiveData<String> getSyncProgress() {
        MutableLiveData<String> syncProgress = new MutableLiveData<>();
        syncProgress.setValue("Ready"); // Placeholder
        return syncProgress;
    }
    
    /**
     * Get unsynced deliveries for debugging
     */
    public LiveData<List<DeliveryEntity>> getUnsyncedDeliveries() {
        return deliveryRepository.getUnsyncedDeliveries();
    }

    // =================== DELIVERY OPERATIONS (MODERN) ===================
    
    /**
     * Complete delivery with proper modern data
     */
    public void completeDelivery(String deliveryId, String deliveryNotes, String signaturePath, String photoPath) {
        Log.i(TAG, "Completing delivery: " + deliveryId);
        markAsDelivered(deliveryId, deliveryNotes, photoPath);
    }
    
    /**
     * Select delivery by ID (modern approach)
     */
    public void selectDelivery(String deliveryId) {
        Log.i(TAG, "Selecting delivery: " + deliveryId);
        selectedDeliveryId = deliveryId;
        loadDeliveryById(deliveryId, false);
    }
    
    /**
     * Get current trip ID
     */
    public String getCurrentTripId() {
        return currentTripId.getValue();
    }
    
    /**
     * Load delivery by ID with single parameter for compatibility
     */
    public void loadDeliveryById(String deliveryId) {
        loadDeliveryById(deliveryId, false);
    }
    
    /**
     * Load deliveries for trip with single parameter for compatibility
     */
    public void loadDeliveriesForTrip(String tripId) {
        loadDeliveriesForTrip(tripId, false);
    }
    
    /**
     * Get delivery entity by ID (direct access to modern data)
     */
    public DeliveryEntity getCurrentDeliveryEntity() {
        UiState<DeliveryEntity> state = currentDeliveryStateLiveData.getValue();
        return (state != null && state.isSuccess()) ? state.getData() : null;
    }
    
    /**
     * Check if we have a current delivery loaded
     */
    public boolean hasCurrentDelivery() {
        return getCurrentDeliveryEntity() != null;
    }
    
    /**
     * Get customer name from current delivery
     */
    public String getCurrentCustomerName() {
        DeliveryEntity entity = getCurrentDeliveryEntity();
        return entity != null ? entity.getCustomerName() : "";
    }
    
    /**
     * Get delivery address from current delivery
     */
    public String getCurrentDeliveryAddress() {
        DeliveryEntity entity = getCurrentDeliveryEntity();
        return entity != null ? entity.getDeliveryAddress() : "";
    }
    
    // =================== STATISTICS & DEBUGGING ===================
    
    /**
     * Get delivery statistics for debugging/monitoring
     */
    public void logDeliveryStats(String tripId) {
        deliveryRepository.getDeliveryStats(tripId)
                .thenAccept(stats -> {
                    Log.i(TAG, "Delivery Stats for trip " + tripId + ": " + stats.toString());
                });
    }
}
