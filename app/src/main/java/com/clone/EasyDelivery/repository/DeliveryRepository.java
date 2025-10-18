package com.clone.EasyDelivery.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.clone.EasyDelivery.Api.services.DeliveryService;
import com.clone.EasyDelivery.Api.utils.ApiResponseHandler;
import com.clone.EasyDelivery.data.dao.DeliveryDao;
import com.clone.EasyDelivery.data.dao.OfflineActionDao;
import com.clone.EasyDelivery.data.entities.DeliveryEntity;
import com.clone.EasyDelivery.data.entities.OfflineActionEntity;
import com.clone.EasyDelivery.models.Delivery;
import com.clone.EasyDelivery.repository.utils.NetworkBoundResource;
import com.clone.EasyDelivery.repository.utils.Resource;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * MODERN Repository for Delivery data with offline-first architecture
 * Uses unified Delivery model - NO MORE DTO CONVERSION HELL!
 */
@Singleton
public class DeliveryRepository {
    
    private static final String TAG = "DeliveryRepository";
    
    // Dependencies
    private final DeliveryDao deliveryDao;
    private final OfflineActionDao offlineActionDao;
    private final DeliveryService deliveryService;
    private final ExecutorService executor;
    
    @Inject
    public DeliveryRepository(DeliveryDao deliveryDao, 
                            OfflineActionDao offlineActionDao,
                            DeliveryService deliveryService,
                            ExecutorService executorService) {
        this.deliveryDao = deliveryDao;
        this.offlineActionDao = offlineActionDao;
        this.deliveryService = deliveryService;
        this.executor = executorService;

        Log.i(TAG, "DeliveryRepository initialized with MODERN unified model architecture");
    }
    
    // =================== DELIVERY CRUD OPERATIONS ===================
    
    /**
     * Get all deliveries for a trip with offline-first approach
     */
    public LiveData<Resource<List<DeliveryEntity>>> getDeliveriesForTrip(String tripId, boolean forceRefresh) {
        return new NetworkBoundResource<List<DeliveryEntity>, List<Delivery>>(executor) {
            @Override
            protected void saveCallResult(List<Delivery> item) {
                Log.d(TAG, "Saving " + item.size() + " deliveries to local database");
                long currentTime = System.currentTimeMillis();
                for (Delivery delivery : item) {
                    // Convert unified model to entity and save
                    DeliveryEntity entity = convertDeliveryToEntity(delivery);
                    // Set sync metadata properly
                    entity.setStatus("synced"); // Use existing status field
                    entity.setUpdatedAt(currentTime);
                    deliveryDao.insertDelivery(entity);
                }
            }
            
            @Override
            protected boolean shouldFetch(List<DeliveryEntity> data) {
                if (forceRefresh) {
                    Log.d(TAG, "Force refresh requested");
                    return true;
                }
                
                // Fetch if no local data or data is stale
                boolean shouldFetch = data == null || data.isEmpty() || isDataStale(data);
                Log.d(TAG, "Should fetch from API: " + shouldFetch);
                return shouldFetch;
            }
            
            @Override
            protected LiveData<List<DeliveryEntity>> loadFromDb() {
                Log.d(TAG, "Loading deliveries for trip " + tripId + " from database");
                return deliveryDao.getDeliveriesByTripLiveData(tripId);
            }
            
            @Override
            protected CompletableFuture<ApiResponseHandler.ApiResult<List<Delivery>>> createCall() {
                Log.d(TAG, "Fetching deliveries for trip " + tripId + " from API");

                return CompletableFuture.supplyAsync(() -> {
                    try {
                        // Execute the Retrofit Call using unified Delivery model
                        retrofit2.Response<DeliveryService.DeliveriesResponse> response =
                                deliveryService.getDeliveriesForTrip(tripId).execute();

                        if (response.isSuccessful() && response.body() != null) {
                            DeliveryService.DeliveriesResponse apiResponse = response.body();

                            // Use unified Delivery models directly - NO MORE DTO CONVERSION!
                            List<Delivery> deliveries = apiResponse.getDeliveries();

                            if (deliveries == null) {
                                deliveries = new java.util.ArrayList<>();
                            }

                            Log.d(TAG, "Successfully fetched " + deliveries.size() + " deliveries from API");
                            return ApiResponseHandler.ApiResult.success(deliveries);
                        } else {
                            String errorMsg = "API call failed: " + response.code() + " " + response.message();
                            return ApiResponseHandler.ApiResult.error(errorMsg, response.code());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error calling delivery API", e);
                        return ApiResponseHandler.ApiResult.error("Network error: " + e.getMessage(), -1);
                    }
                }, executor);
            }
        }.asLiveData();
    }
    
    /**
     * Get a specific delivery by ID
     */
    public LiveData<Resource<DeliveryEntity>> getDeliveryById(String deliveryId, boolean forceRefresh) {
        return new NetworkBoundResource<DeliveryEntity, Delivery>(executor) {
            @Override
            protected void saveCallResult(Delivery item) {
                Log.d(TAG, "Saving delivery " + deliveryId + " to local database");
                DeliveryEntity entity = convertDeliveryToEntity(item);
                entity.setStatus("synced");
                entity.setUpdatedAt(System.currentTimeMillis());
                deliveryDao.insertDelivery(entity);
            }
            
            @Override
            protected boolean shouldFetch(DeliveryEntity data) {
                if (forceRefresh) return true;
                return data == null || !"synced".equals(data.getStatus());
            }
            
            @Override
            protected LiveData<DeliveryEntity> loadFromDb() {
                Log.d(TAG, "Loading delivery " + deliveryId + " from database");
                return deliveryDao.getDeliveryLiveData(deliveryId);
            }
            
            @Override
            protected CompletableFuture<ApiResponseHandler.ApiResult<Delivery>> createCall() {
                Log.d(TAG, "Fetching delivery " + deliveryId + " from API");
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        // Use the correct DeliveryService response type
                        retrofit2.Response<DeliveryService.DeliveryResponse> response =
                                deliveryService.getDeliveryById(deliveryId).execute();

                        if (response.isSuccessful() && response.body() != null) {
                            DeliveryService.DeliveryResponse apiResponse = response.body();
                            // Extract the Delivery from the response wrapper
                            Delivery delivery = apiResponse.getDelivery();
                            return ApiResponseHandler.ApiResult.success(delivery);
                        } else {
                            String errorMsg = "API call failed: " + response.code() + " " + response.message();
                            return ApiResponseHandler.ApiResult.error(errorMsg, response.code());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error calling delivery API", e);
                        return ApiResponseHandler.ApiResult.error("Network error: " + e.getMessage(), -1);
                    }
                }, executor);
            }
        }.asLiveData();
    }

    /**
     * Create a new delivery (offline-first)
     */
    public CompletableFuture<Resource<DeliveryEntity>> createDelivery(DeliveryEntity delivery) {
        Log.i(TAG, "Creating new delivery: " + delivery.getDeliveryId());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Save locally first
                delivery.setStatus("pending");
                delivery.setCreatedAt(System.currentTimeMillis());
                delivery.setUpdatedAt(System.currentTimeMillis());
                deliveryDao.insertDelivery(delivery);
                
                // Queue offline action for sync
                OfflineActionEntity offlineAction = new OfflineActionEntity(
                    "DELIVERY", 
                    delivery.getDeliveryId(), 
                    "CREATE", 
                    null,
                    System.currentTimeMillis()
                );
                offlineActionDao.insertAction(offlineAction);
                
                Log.d(TAG, "Delivery created locally and queued for sync: " + delivery.getDeliveryId());
                return Resource.success(delivery);
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating delivery locally", e);
                return Resource.error("Failed to create delivery: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    /**
     * Update an existing delivery (offline-first)
     */
    public CompletableFuture<Resource<DeliveryEntity>> updateDelivery(DeliveryEntity delivery) {
        Log.i(TAG, "Updating delivery: " + delivery.getDeliveryId());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Update locally first
                delivery.setUpdatedAt(System.currentTimeMillis());
                delivery.setStatus("pending");
                deliveryDao.updateDelivery(delivery);
                
                // Queue offline action for sync
                OfflineActionEntity offlineAction = new OfflineActionEntity(
                    "DELIVERY", 
                    delivery.getDeliveryId(), 
                    "UPDATE", 
                    null,
                    System.currentTimeMillis()
                );
                offlineActionDao.insertAction(offlineAction);
                
                Log.d(TAG, "Delivery updated locally and queued for sync: " + delivery.getDeliveryId());
                return Resource.success(delivery);
                
            } catch (Exception e) {
                Log.e(TAG, "Error updating delivery locally", e);
                return Resource.error("Failed to update delivery: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    /**
     * Delete a delivery (offline-first)
     */
    public CompletableFuture<Resource<Void>> deleteDelivery(String deliveryId) {
        Log.i(TAG, "Deleting delivery: " + deliveryId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Mark as deleted locally (soft delete for sync)
                DeliveryEntity delivery = deliveryDao.getDelivery(deliveryId);
                if (delivery != null) {
                    delivery.setUpdatedAt(System.currentTimeMillis());
                    delivery.setDeleted(true);
                    delivery.setStatus("pending");
                    deliveryDao.updateDelivery(delivery);
                    
                    // Queue offline action for sync
                    OfflineActionEntity offlineAction = new OfflineActionEntity(
                        "DELIVERY", 
                        deliveryId, 
                        "DELETE", 
                        null,
                        System.currentTimeMillis()
                    );
                    offlineActionDao.insertAction(offlineAction);
                    
                    Log.d(TAG, "Delivery marked as deleted and queued for sync: " + deliveryId);
                    return Resource.success(null);
                } else {
                    return Resource.error("Delivery not found", null);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error deleting delivery", e);
                return Resource.error("Failed to delete delivery: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    // =================== DELIVERY STATUS OPERATIONS ===================
    
    /**
     * Mark delivery as delivered
     */
    public CompletableFuture<Resource<DeliveryEntity>> markAsDelivered(String deliveryId, String deliveryNotes, String proofOfDeliveryPath) {
        Log.i(TAG, "Marking delivery as delivered: " + deliveryId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                DeliveryEntity delivery = deliveryDao.getDelivery(deliveryId);
                if (delivery == null) {
                    return Resource.error("Delivery not found", null);
                }
                
                // Update delivery status and details
                delivery.setStatus("delivered");
                delivery.setDeliveryTime(System.currentTimeMillis());
                delivery.setComment(deliveryNotes);
                delivery.setProofOfDeliveryPath(proofOfDeliveryPath);
                delivery.setUpdatedAt(System.currentTimeMillis());
                delivery.setStatus("pending");
                
                deliveryDao.updateDelivery(delivery);
                
                // Queue for sync
                OfflineActionEntity offlineAction = new OfflineActionEntity(
                    "DELIVERY", 
                    deliveryId, 
                    "UPDATE", 
                    null,
                    System.currentTimeMillis()
                );
                offlineActionDao.insertAction(offlineAction);
                
                Log.d(TAG, "Delivery marked as delivered: " + deliveryId);
                return Resource.success(delivery);
                
            } catch (Exception e) {
                Log.e(TAG, "Error marking delivery as delivered", e);
                return Resource.error("Failed to mark delivery as delivered: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    /**
     * Mark delivery as failed
     */
    public CompletableFuture<Resource<DeliveryEntity>> markAsFailed(String deliveryId, String failureReason) {
        Log.i(TAG, "Marking delivery as failed: " + deliveryId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                DeliveryEntity delivery = deliveryDao.getDelivery(deliveryId);
                if (delivery == null) {
                    return Resource.error("Delivery not found", null);
                }
                
                // Update delivery status
                delivery.setStatus("failed");
                delivery.setComment(failureReason);
                delivery.setUpdatedAt(System.currentTimeMillis());
                delivery.setStatus("pending");
                
                deliveryDao.updateDelivery(delivery);
                
                // Queue for sync
                OfflineActionEntity offlineAction = new OfflineActionEntity(
                    "DELIVERY", 
                    deliveryId, 
                    "UPDATE", 
                    null,
                    System.currentTimeMillis()
                );
                offlineActionDao.insertAction(offlineAction);
                
                Log.d(TAG, "Delivery marked as failed: " + deliveryId);
                return Resource.success(delivery);
                
            } catch (Exception e) {
                Log.e(TAG, "Error marking delivery as failed", e);
                return Resource.error("Failed to mark delivery as failed: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    /**
     * Start delivery (when driver begins delivery attempt)
     */
    public CompletableFuture<Resource<DeliveryEntity>> startDelivery(String deliveryId) {
        Log.i(TAG, "Starting delivery: " + deliveryId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                DeliveryEntity delivery = deliveryDao.getDelivery(deliveryId);
                if (delivery == null) {
                    return Resource.error("Delivery not found", null);
                }
                
                // Update delivery status
                delivery.setStatus("in_progress");
                delivery.setUpdatedAt(System.currentTimeMillis());
                delivery.setStatus("pending");
                
                deliveryDao.updateDelivery(delivery);
                
                // Queue for sync
                OfflineActionEntity offlineAction = new OfflineActionEntity(
                    "DELIVERY", 
                    deliveryId, 
                    "UPDATE", 
                    null,
                    System.currentTimeMillis()
                );
                offlineActionDao.insertAction(offlineAction);
                
                Log.d(TAG, "Delivery started: " + deliveryId);
                return Resource.success(delivery);
                
            } catch (Exception e) {
                Log.e(TAG, "Error starting delivery", e);
                return Resource.error("Failed to start delivery: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    // =================== QUERY OPERATIONS ===================
    
    /**
     * Get deliveries by status
     */
    public LiveData<List<DeliveryEntity>> getDeliveriesByStatus(String tripId, String status) {
        Log.d(TAG, "Getting deliveries by status - Trip: " + tripId + ", Status: " + status);
        return deliveryDao.getDeliveriesByTripAndStatusLiveData(tripId, status);
    }
    
    /**
     * Get pending deliveries for a trip
     */
    public LiveData<List<DeliveryEntity>> getPendingDeliveries(String tripId) {
        Log.d(TAG, "Getting pending deliveries for trip: " + tripId);
        return deliveryDao.getDeliveriesByTripAndStatusLiveData(tripId, "pending");
    }
    
    /**
     * Get completed deliveries for a trip
     */
    public LiveData<List<DeliveryEntity>> getCompletedDeliveries(String tripId) {
        Log.d(TAG, "Getting completed deliveries for trip: " + tripId);
        return deliveryDao.getCompletedDeliveriesByTripLiveData(tripId);
    }
    
    /**
     * Get deliveries in date range
     */
    public LiveData<List<DeliveryEntity>> getDeliveriesInDateRange(String tripId, long startTime, long endTime) {
        Log.d(TAG, "Getting deliveries in date range - Trip: " + tripId + ", Range: " + startTime + " to " + endTime);
        // Note: This method doesn't exist in DAO, would need to be added
        return deliveryDao.getDeliveriesByTripLiveData(tripId);
    }
    
    /**
     * Get unsynced deliveries (for debugging)
     */
    public LiveData<List<DeliveryEntity>> getUnsyncedDeliveries() {
        Log.d(TAG, "Getting unsynced deliveries");
        // Use the existing DAO method that returns unsynced deliveries
        return Transformations.map(
                deliveryDao.getAllDeliveriesLiveData(),
                deliveries -> {
                    List<DeliveryEntity> unsynced = new java.util.ArrayList<>();
                    for (DeliveryEntity delivery : deliveries) {
                        // Filter for unsynced deliveries (pending sync status)
                        if (!"synced".equals(delivery.getStatus())) {
                            unsynced.add(delivery);
                        }
                    }
                    return unsynced;
                }
        );
    }
    
    /**
     * Search deliveries by customer name
     */
    public LiveData<List<DeliveryEntity>> searchDeliveriesByCustomer(String customerName) {
        Log.d(TAG, "Searching deliveries by customer: " + customerName);
        // Use the actual search method from DAO
        return Transformations.map(
                deliveryDao.getAllDeliveriesLiveData(),
                deliveries -> {
                    List<DeliveryEntity> filtered = new java.util.ArrayList<>();
                    for (DeliveryEntity delivery : deliveries) {
                        if (delivery.getCustomerName() != null &&
                                delivery.getCustomerName().toLowerCase().contains(customerName.toLowerCase())) {
                            filtered.add(delivery);
                        }
                    }
                    return filtered;
                }
        );
    }
    
    /**
     * Search deliveries by address
     */
    public LiveData<List<DeliveryEntity>> searchDeliveriesByAddress(String address) {
        Log.d(TAG, "Searching deliveries by address: " + address);
        // Use the actual search method from DAO
        return Transformations.map(
                deliveryDao.getAllDeliveriesLiveData(),
                deliveries -> {
                    List<DeliveryEntity> filtered = new java.util.ArrayList<>();
                    for (DeliveryEntity delivery : deliveries) {
                        if (delivery.getAddress() != null &&
                                delivery.getAddress().toLowerCase().contains(address.toLowerCase())) {
                            filtered.add(delivery);
                        }
                    }
                    return filtered;
                }
        );
    }
    
    // =================== SYNC OPERATIONS ===================
    
    /**
     * Force sync deliveries for trip
     */
    public CompletableFuture<Resource<Void>> forceSyncDeliveries(String tripId) {
        Log.i(TAG, "Force syncing deliveries for trip: " + tripId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get all pending sync deliveries and mark them for immediate sync
                List<DeliveryEntity> pendingDeliveries = deliveryDao.getDeliveriesNeedingSync();
                
                for (DeliveryEntity delivery : pendingDeliveries) {
                    if (delivery.getTripId().equals(tripId)) {
                        // Queue offline action for immediate sync
                        OfflineActionEntity offlineAction = new OfflineActionEntity(
                            "DELIVERY", 
                            delivery.getDeliveryId(), 
                            "SYNC", 
                            null,
                            System.currentTimeMillis()
                        );
                        offlineActionDao.insertAction(offlineAction);
                    }
                }
                
                Log.d(TAG, "Force sync queued for trip: " + tripId);
                return Resource.success(null);
                
            } catch (Exception e) {
                Log.e(TAG, "Error forcing delivery sync", e);
                return Resource.error("Failed to sync deliveries: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    // =================== BATCH OPERATIONS ===================
    
    /**
     * Update multiple deliveries at once (bulk operation)
     */
    public CompletableFuture<Resource<List<DeliveryEntity>>> updateDeliveries(List<DeliveryEntity> deliveries) {
        Log.i(TAG, "Bulk updating " + deliveries.size() + " deliveries");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                
                for (DeliveryEntity delivery : deliveries) {
                    delivery.setUpdatedAt(currentTime);
                    delivery.setStatus("pending");
                    deliveryDao.updateDelivery(delivery);
                    
                    // Queue each for sync
                    OfflineActionEntity offlineAction = new OfflineActionEntity(
                        "DELIVERY", 
                        delivery.getDeliveryId(), 
                        "UPDATE", 
                        null,
                        currentTime
                    );
                    offlineActionDao.insertAction(offlineAction);
                }
                
                Log.d(TAG, "Bulk update completed for " + deliveries.size() + " deliveries");
                return Resource.success(deliveries);
                
            } catch (Exception e) {
                Log.e(TAG, "Error bulk updating deliveries", e);
                return Resource.error("Failed to update deliveries: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    // =================== UTILITY METHODS ===================
    
    /**
     * Check if delivery data is stale and needs refresh
     */
    private boolean isDataStale(List<DeliveryEntity> deliveries) {
        if (deliveries == null || deliveries.isEmpty()) return true;
        
        long currentTime = System.currentTimeMillis();
        long staleThreshold = 10 * 60 * 1000; // 10 minutes (shorter than trips)
        
        // Check if any delivery was last synced more than threshold ago
        for (DeliveryEntity delivery : deliveries) {
            if (delivery.getUpdatedAt() != null &&
                    (currentTime - delivery.getUpdatedAt()) > staleThreshold) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get repository statistics
     */
    public CompletableFuture<DeliveryStats> getDeliveryStats(String tripId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int totalDeliveries = deliveryDao.getTotalDeliveriesForTrip(tripId);
                int pendingDeliveries = deliveryDao.getDeliveriesByTripAndStatus(tripId, "pending").size();
                int completedDeliveries = deliveryDao.getCompletedDeliveryCount(tripId);
                int failedDeliveries = deliveryDao.getDeliveriesByTripAndStatus(tripId, "failed").size();
                int unsyncedDeliveries = deliveryDao.getDeliveriesNeedingSync().size();
                
                return new DeliveryStats(totalDeliveries, pendingDeliveries, 
                                       completedDeliveries, failedDeliveries, unsyncedDeliveries);
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting delivery stats", e);
                return new DeliveryStats(0, 0, 0, 0, 0);
            }
        }, executor);
    }
    
    /**
     * Statistics class for delivery repository
     */
    public static class DeliveryStats {
        public final int totalDeliveries;
        public final int pendingDeliveries;
        public final int completedDeliveries;
        public final int failedDeliveries;
        public final int unsyncedDeliveries;
        
        public DeliveryStats(int totalDeliveries, int pendingDeliveries, int completedDeliveries, 
                           int failedDeliveries, int unsyncedDeliveries) {
            this.totalDeliveries = totalDeliveries;
            this.pendingDeliveries = pendingDeliveries;
            this.completedDeliveries = completedDeliveries;
            this.failedDeliveries = failedDeliveries;
            this.unsyncedDeliveries = unsyncedDeliveries;
        }
        
        public double getCompletionRate() {
            return totalDeliveries > 0 ? (double) completedDeliveries / totalDeliveries * 100 : 0;
        }
        
        public double getFailureRate() {
            return totalDeliveries > 0 ? (double) failedDeliveries / totalDeliveries * 100 : 0;
        }
        
        @Override
        public String toString() {
            return "DeliveryStats{" +
                    "totalDeliveries=" + totalDeliveries +
                    ", pendingDeliveries=" + pendingDeliveries +
                    ", completedDeliveries=" + completedDeliveries +
                    ", failedDeliveries=" + failedDeliveries +
                    ", unsyncedDeliveries=" + unsyncedDeliveries +
                    ", completionRate=" + String.format("%.1f", getCompletionRate()) + "%" +
                    ", failureRate=" + String.format("%.1f", getFailureRate()) + "%" +
                    '}';
        }
    }

    private DeliveryEntity convertDeliveryToEntity(Delivery delivery) {
        if (delivery == null) return null;

        DeliveryEntity entity = new DeliveryEntity();

        // Basic fields
        entity.setDeliveryId(delivery.getDeliveryId());
        entity.setOrderNumber(delivery.getOrderNumber());
        entity.setAddress(delivery.getAddress());
        entity.setQuantityExpected(delivery.getQuantityExpected());
        entity.setStatus(delivery.getStatus());

        // Customer data
        entity.setCustomerName(delivery.getCustomerName());
        entity.setCustomerContact(delivery.getCustomerContact());
        entity.setCustomerNumber(delivery.getCustomerNumber());
        entity.setCustomerEmail(delivery.getCustomerEmail());

        // GPS Location
        entity.setLatitude(delivery.getLatitude());
        entity.setLongitude(delivery.getLongitude());

        // Proof of Delivery data
        entity.setSignatureUrl(delivery.getSignatureUrl());
        entity.setPhotoUrl(delivery.getPhotoUrl());
        entity.setComment(delivery.getComment());
        entity.setRecipientName(delivery.getRecipientName());
        entity.setQuantityDelivered(delivery.getQuantityDelivered());

        // Set sync metadata
        entity.setUpdatedAt(System.currentTimeMillis());
        entity.setCreatedAt(System.currentTimeMillis());

        return entity;
    }
}