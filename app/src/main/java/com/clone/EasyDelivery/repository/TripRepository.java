package com.clone.EasyDelivery.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.clone.EasyDelivery.Api.services.TripService;
import com.clone.EasyDelivery.models.Trip;
import com.clone.EasyDelivery.Api.utils.ApiResponseHandler;
import com.clone.EasyDelivery.data.dao.TripDao;
import com.clone.EasyDelivery.data.entities.TripEntity;
import com.clone.EasyDelivery.repository.utils.NetworkBoundResource;
import com.clone.EasyDelivery.repository.utils.Resource;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Repository for Trip data with offline-first architecture and unified models
 * Provides unified data access combining local Room database with remote API
 * Uses Hilt dependency injection for modern Android architecture
 */
@Singleton
public class TripRepository {
    
    private static final String TAG = "TripRepository";
    
    // Dependencies injected by Hilt
    private final TripDao tripDao;
    private final TripService tripService;
    private final ExecutorService executor;
    
    @Inject
    public TripRepository(TripDao tripDao, TripService tripService, ExecutorService executorService) {
        this.tripDao = tripDao;
        this.tripService = tripService;
        this.executor = executorService;

        Log.i(TAG, "TripRepository initialized with Hilt DI and unified Trip models");
    }
    
    // =================== TRIP CRUD OPERATIONS ===================
    
    /**
     * Get all trips for a driver - with unified model support
     */
    public LiveData<Resource<List<TripEntity>>> getTripsForDriver(String driverId, boolean forceRefresh) {
        MediatorLiveData<Resource<List<TripEntity>>> result = new MediatorLiveData<>();
        
        // Start with local data
        LiveData<List<TripEntity>> localTrips = tripDao.getTripsByDriverLiveData(driverId);
        result.addSource(localTrips, trips -> {
            result.setValue(Resource.success(trips != null ? trips : new ArrayList<>()));
        });

        // TODO: Implement API fetch using TripService with unified Trip model
        if (forceRefresh) {
            Log.d(TAG, "Force refresh requested for driver trips: " + driverId);
            // With unified models, we can use TripService directly without conversion
        }
        
        return result;
    }
    
    /**
     * Convert unified Trip model to TripEntity for database storage
     * Handles date conversion from ISO 8601 strings to Long timestamps
     */
    private TripEntity convertTripToEntity(Trip trip) {
        TripEntity entity = new TripEntity();
        entity.setTripId(trip.getTripId());
        entity.setClientId(trip.getClientId());
        entity.setStatus(trip.getStatus());
        entity.setDriverId(trip.getDriverId());
        entity.setSessionId(trip.getSessionId());

        // Convert date strings to Long timestamps for database storage
        entity.setCreatedAt(trip.getCreatedAtAsTimestamp() != null ?
                trip.getCreatedAtAsTimestamp() : System.currentTimeMillis());
        entity.setClaimedAt(trip.getClaimedAtAsTimestamp());
        entity.setCompletedAt(trip.getCompletedAtAsTimestamp());

        entity.setTotalDeliveries(trip.getTotalDeliveries());
        entity.setCompletedDeliveries(trip.getCompletedDeliveries());
        
        entity.setSyncedAt(System.currentTimeMillis());
        entity.setSyncStatus("synced");
        return entity;
    }
    
    /**
     * Get a specific trip by ID
     */
    public LiveData<Resource<TripEntity>> getTripById(String tripId, boolean forceRefresh) {
        return new NetworkBoundResource<TripEntity, TripEntity>(executor) {
            @Override
            protected void saveCallResult(TripEntity item) {
                Log.d(TAG, "Saving trip " + tripId + " to local database");
                item.setSyncStatus("synced");
                item.setSyncedAt(System.currentTimeMillis());
                tripDao.insertTrip(item);
            }
            
            @Override
            protected boolean shouldFetch(TripEntity data) {
                if (forceRefresh) return true;
                return data == null || !"synced".equals(data.getSyncStatus());
            }
            
            @Override
            protected LiveData<TripEntity> loadFromDb() {
                Log.d(TAG, "Loading trip " + tripId + " from database");
                return tripDao.getTripLiveData(tripId);
            }
            
            @Override
            protected CompletableFuture<ApiResponseHandler.ApiResult<TripEntity>> createCall() {
                Log.d(TAG, "Fetching trip " + tripId + " from API");
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        // This would be replaced with actual API call
                        // Use available method - getTrip instead of getTripById
                        TripEntity trip = tripDao.getTrip(tripId);
                        return trip != null ? 
                            ApiResponseHandler.ApiResult.success(trip) : 
                            ApiResponseHandler.ApiResult.error("Trip not found", 404);
                    } catch (Exception e) {
                        Log.e(TAG, "Error fetching trip from API", e);
                        return ApiResponseHandler.ApiResult.error("API error: " + e.getMessage(), -1);
                    }
                }, executor);
            }
        }.asLiveData();
    }
    
    /**
     * Create a new trip (offline-first)
     */
    public CompletableFuture<Resource<TripEntity>> createTrip(TripEntity trip) {
        Log.i(TAG, "Creating new trip: " + trip.getTripId());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Save locally first
                trip.setSyncStatus("pending");
                trip.setSyncedAt(System.currentTimeMillis());
                tripDao.insertTrip(trip);
                
                Log.d(TAG, "Trip created locally: " + trip.getTripId());
                return Resource.success(trip);
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating trip locally", e);
                return Resource.error("Failed to create trip: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    /**
     * Update an existing trip (offline-first)
     */
    public CompletableFuture<Resource<TripEntity>> updateTrip(TripEntity trip) {
        Log.i(TAG, "Updating trip: " + trip.getTripId());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Update locally first
                trip.setSyncStatus("pending");
                trip.setSyncedAt(System.currentTimeMillis());
                tripDao.updateTrip(trip);
                
                Log.d(TAG, "Trip updated locally: " + trip.getTripId());
                return Resource.success(trip);
                
            } catch (Exception e) {
                Log.e(TAG, "Error updating trip locally", e);
                return Resource.error("Failed to update trip: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    /**
     * Delete a trip (offline-first)
     */
    public CompletableFuture<Resource<Void>> deleteTrip(String tripId) {
        Log.i(TAG, "Deleting trip: " + tripId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Delete directly for now - could implement soft delete later
                tripDao.deleteTripById(tripId);
                
                Log.d(TAG, "Trip deleted locally: " + tripId);
                return Resource.success(null);
                
            } catch (Exception e) {
                Log.e(TAG, "Error deleting trip", e);
                return Resource.error("Failed to delete trip: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    // =================== TRIP STATUS OPERATIONS ===================
    
    /**
     * Start a trip
     */
    public CompletableFuture<Resource<TripEntity>> startTrip(String tripId) {
        Log.i(TAG, "Starting trip: " + tripId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Use DAO method for starting trip
                tripDao.startTrip(tripId, System.currentTimeMillis());
                
                // Get updated trip
                TripEntity trip = tripDao.getTrip(tripId);
                if (trip == null) {
                    return Resource.error("Trip not found after starting", null);
                }
                
                Log.d(TAG, "Trip started: " + tripId);
                return Resource.success(trip);
                
            } catch (Exception e) {
                Log.e(TAG, "Error starting trip", e);
                return Resource.error("Failed to start trip: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    /**
     * Complete a trip
     */
    public CompletableFuture<Resource<TripEntity>> completeTrip(String tripId) {
        Log.i(TAG, "Completing trip: " + tripId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Use DAO method for completing trip
                tripDao.completeTrip(tripId, System.currentTimeMillis());
                
                // Get updated trip
                TripEntity trip = tripDao.getTrip(tripId);
                if (trip == null) {
                    return Resource.error("Trip not found after completing", null);
                }
                
                Log.d(TAG, "Trip completed: " + tripId);
                return Resource.success(trip);
                
            } catch (Exception e) {
                Log.e(TAG, "Error completing trip", e);
                return Resource.error("Failed to complete trip: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    // =================== QUERY OPERATIONS ===================
    
    /**
     * Get active trips for driver
     */
    public LiveData<List<TripEntity>> getActiveTrips(String driverId) {
        Log.d(TAG, "Getting active trips for driver: " + driverId);
        return tripDao.getActiveTripsForDriverLiveData(driverId);
    }
    
    /**
     * Get trips by status
     */
    public LiveData<List<TripEntity>> getTripsByStatus(String status) {
        Log.d(TAG, "Getting trips by status: " + status);
        return tripDao.getTripsByStatusLiveData(status);
    }
    
    /**
     * Get trips needing sync
     */
    public LiveData<List<TripEntity>> getTripsNeedingSync() {
        Log.d(TAG, "Getting trips needing sync");
        return tripDao.getTripsNeedingSyncLiveData();
    }
    
    /**
     * Get pending sync count
     */
    public LiveData<Integer> getPendingSyncCount() {
        return tripDao.getPendingSyncCountLiveData();
    }
    
    // =================== SYNC OPERATIONS ===================
    
    /**
     * Get trips that need to be synced (non-LiveData for sync worker)
     */
    public CompletableFuture<List<TripEntity>> getTripsForSync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return tripDao.getTripsNeedingSync();
            } catch (Exception e) {
                Log.e(TAG, "Error getting trips for sync", e);
                return new ArrayList<>();
            }
        }, executor);
    }
    
    // =================== UTILITY METHODS ===================
    
    /**
     * Check if trip data is stale and needs refresh
     */
    private boolean isDataStale(List<TripEntity> trips) {
        if (trips == null || trips.isEmpty()) return true;
        
        long currentTime = System.currentTimeMillis();
        long staleThreshold = 15 * 60 * 1000; // 15 minutes
        
        // Check if any trip was last synced more than threshold ago
        for (TripEntity trip : trips) {
            if (trip.getSyncedAt() > 0 && 
                (currentTime - trip.getSyncedAt()) > staleThreshold) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Mark trip as synced
     */
    public CompletableFuture<Void> markTripAsSynced(String tripId, String serverVersion) {
        return CompletableFuture.runAsync(() -> {
            try {
                tripDao.markAsSynced(tripId, System.currentTimeMillis(), serverVersion);
                Log.d(TAG, "Trip marked as synced: " + tripId);
            } catch (Exception e) {
                Log.e(TAG, "Error marking trip as synced", e);
            }
        }, executor);
    }

    // =================== ADDITIONAL METHODS FOR TRIPVIEWMODEL COMPATIBILITY ===================

    /**
     * Get active trip for driver (single trip)
     */
    public LiveData<TripEntity> getActiveTrip(String driverId) {
        Log.d(TAG, "Getting active trip for driver: " + driverId);
        // Create a fallback using available methods
        androidx.lifecycle.MutableLiveData<TripEntity> result = new androidx.lifecycle.MutableLiveData<>();

        executor.execute(() -> {
            try {
                // Get all trips for driver and find first active one
                List<TripEntity> driverTrips = tripDao.getTripsByDriver(driverId);
                TripEntity activeTrip = null;

                for (TripEntity trip : driverTrips) {
                    if ("claimed".equals(trip.getStatus()) || "in_progress".equals(trip.getStatus())) {
                        activeTrip = trip;
                        break;
                    }
                }

                result.postValue(activeTrip);
            } catch (Exception e) {
                Log.e(TAG, "Error getting active trip for driver", e);
                result.postValue(null);
            }
        });

        return result;
    }

    /**
     * Force sync trips for driver
     */
    public void forceSyncTrips(String driverId) {
        Log.i(TAG, "Force syncing trips for driver: " + driverId);
        executor.execute(() -> {
            try {
                // Mark all driver trips as needing sync
                List<TripEntity> driverTrips = tripDao.getTripsByDriver(driverId);
                for (TripEntity trip : driverTrips) {
                    trip.setSyncStatus("pending");
                    tripDao.updateTrip(trip);
                }
                Log.d(TAG, "Force sync queued for " + driverTrips.size() + " trips");
            } catch (Exception e) {
                Log.e(TAG, "Error force syncing trips", e);
            }
        });
    }

    /**
     * Get sync status for UI display
     */
    public LiveData<com.clone.EasyDelivery.sync.models.SyncStatus> getSyncStatus() {
        androidx.lifecycle.MutableLiveData<com.clone.EasyDelivery.sync.models.SyncStatus> syncStatus =
                new androidx.lifecycle.MutableLiveData<>();

        // Simple implementation - could be enhanced with actual sync monitoring
        executor.execute(() -> {
            try {
                List<TripEntity> pendingTrips = tripDao.getTripsNeedingSync();
                if (pendingTrips.isEmpty()) {
                    syncStatus.postValue(com.clone.EasyDelivery.sync.models.SyncStatus.SUCCESS);
                } else {
                    syncStatus.postValue(com.clone.EasyDelivery.sync.models.SyncStatus.SYNCING);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting sync status", e);
                syncStatus.postValue(com.clone.EasyDelivery.sync.models.SyncStatus.ERROR);
            }
        });

        return syncStatus;
    }

    /**
     * Get sync progress message for UI display
     */
    public LiveData<String> getSyncProgress() {
        androidx.lifecycle.MutableLiveData<String> syncProgress =
                new androidx.lifecycle.MutableLiveData<>();

        executor.execute(() -> {
            try {
                List<TripEntity> pendingTrips = tripDao.getTripsNeedingSync();
                String progressMessage = pendingTrips.isEmpty() ?
                        "All trips synced" :
                        pendingTrips.size() + " trips pending sync";
                syncProgress.postValue(progressMessage);
            } catch (Exception e) {
                Log.e(TAG, "Error getting sync progress", e);
                syncProgress.postValue("Sync status unknown");
            }
        });

        return syncProgress;
    }

    /**
     * Get trip statistics for debugging/monitoring
     */
    public CompletableFuture<TripStats> getTripStats(String driverId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<TripEntity> allTrips = tripDao.getTripsByDriver(driverId);
                int totalTrips = allTrips.size();

                int pendingTrips = 0;
                int activeTrips = 0;
                int completedTrips = 0;
                int unsyncedTrips = 0;

                for (TripEntity trip : allTrips) {
                    switch (trip.getStatus()) {
                        case "available":
                        case "pending":
                            pendingTrips++;
                            break;
                        case "claimed":
                        case "in_progress":
                            activeTrips++;
                            break;
                        case "completed":
                            completedTrips++;
                            break;
                    }

                    if (!"synced".equals(trip.getSyncStatus())) {
                        unsyncedTrips++;
                    }
                }

                return new TripStats(totalTrips, pendingTrips, activeTrips, completedTrips, unsyncedTrips);

            } catch (Exception e) {
                Log.e(TAG, "Error getting trip stats", e);
                return new TripStats(0, 0, 0, 0, 0);
            }
        }, executor);
    }

    /**
     * Get unsynced trips
     */
    public LiveData<List<TripEntity>> getUnsyncedTrips() {
        Log.d(TAG, "Getting unsynced trips");
        return tripDao.getTripsNeedingSyncLiveData();
    }

    /**
     * Statistics class for trip repository
     */
    public static class TripStats {
        public final int totalTrips;
        public final int pendingTrips;
        public final int activeTrips;
        public final int completedTrips;
        public final int unsyncedTrips;

        public TripStats(int totalTrips, int pendingTrips, int activeTrips,
                         int completedTrips, int unsyncedTrips) {
            this.totalTrips = totalTrips;
            this.pendingTrips = pendingTrips;
            this.activeTrips = activeTrips;
            this.completedTrips = completedTrips;
            this.unsyncedTrips = unsyncedTrips;
        }

        @Override
        public String toString() {
            return "TripStats{" +
                    "totalTrips=" + totalTrips +
                    ", pendingTrips=" + pendingTrips +
                    ", activeTrips=" + activeTrips +
                    ", completedTrips=" + completedTrips +
                    ", unsyncedTrips=" + unsyncedTrips +
                    '}';
        }
    }
}
