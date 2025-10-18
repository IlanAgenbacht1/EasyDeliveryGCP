package com.clone.EasyDelivery.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.clone.EasyDelivery.data.entities.TripEntity;

import java.util.List;

/**
 * DAO interface for TripEntity operations
 */
@Dao
public interface TripDao {
    
    // =================== INSERT OPERATIONS ===================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTrip(TripEntity trip);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTrips(List<TripEntity> trips);
    
    // =================== UPDATE OPERATIONS ===================
    
    @Update
    void updateTrip(TripEntity trip);
    
    @Query("UPDATE trips SET status = :status, sync_status = 'pending' WHERE trip_id = :tripId")
    void updateTripStatus(String tripId, String status);
    
    @Query("UPDATE trips SET status = 'claimed', session_id = :sessionId, driver_id = :driverId, " +
           "claimed_at = :claimedAt, sync_status = 'pending' WHERE trip_id = :tripId")
    void claimTrip(String tripId, String sessionId, String driverId, long claimedAt);
    
    @Query("UPDATE trips SET status = 'in_progress', started_at = :startedAt, sync_status = 'pending' WHERE trip_id = :tripId")
    void startTrip(String tripId, long startedAt);
    
    @Query("UPDATE trips SET status = 'completed', completed_at = :completedAt, sync_status = 'pending' WHERE trip_id = :tripId")
    void completeTrip(String tripId, long completedAt);
    
    @Query("UPDATE trips SET completed_deliveries = completed_deliveries + 1, sync_status = 'pending' WHERE trip_id = :tripId")
    void incrementCompletedDeliveries(String tripId);
    
    @Query("UPDATE trips SET total_deliveries = :totalDeliveries WHERE trip_id = :tripId")
    void updateTotalDeliveries(String tripId, int totalDeliveries);
    
    // =================== QUERY OPERATIONS ===================
    
    @Query("SELECT * FROM trips WHERE trip_id = :tripId")
    TripEntity getTrip(String tripId);
    
    @Query("SELECT * FROM trips WHERE trip_id = :tripId")
    LiveData<TripEntity> getTripLiveData(String tripId);
    
    @Query("SELECT * FROM trips WHERE client_id = :clientId ORDER BY created_at DESC")
    List<TripEntity> getTripsByClient(String clientId);
    
    @Query("SELECT * FROM trips WHERE client_id = :clientId ORDER BY created_at DESC")
    LiveData<List<TripEntity>> getTripsByClientLiveData(String clientId);
    
    @Query("SELECT * FROM trips WHERE status = :status ORDER BY created_at DESC")
    List<TripEntity> getTripsByStatus(String status);
    
    @Query("SELECT * FROM trips WHERE status = :status ORDER BY created_at DESC")
    LiveData<List<TripEntity>> getTripsByStatusLiveData(String status);
    
    @Query("SELECT * FROM trips WHERE client_id = :clientId AND status = :status ORDER BY created_at DESC")
    List<TripEntity> getTripsByClientAndStatus(String clientId, String status);
    
    @Query("SELECT * FROM trips WHERE client_id = :clientId AND status = :status ORDER BY created_at DESC")
    LiveData<List<TripEntity>> getTripsByClientAndStatusLiveData(String clientId, String status);
    
    @Query("SELECT * FROM trips WHERE driver_id = :driverId ORDER BY created_at DESC")
    List<TripEntity> getTripsByDriver(String driverId);
    
    @Query("SELECT * FROM trips WHERE driver_id = :driverId ORDER BY created_at DESC")
    LiveData<List<TripEntity>> getTripsByDriverLiveData(String driverId);
    
    @Query("SELECT * FROM trips WHERE session_id = :sessionId ORDER BY created_at DESC")
    List<TripEntity> getTripsBySession(String sessionId);
    
    @Query("SELECT * FROM trips WHERE session_id = :sessionId ORDER BY created_at DESC")
    LiveData<List<TripEntity>> getTripsBySessionLiveData(String sessionId);
    
    // =================== ACTIVE TRIP OPERATIONS ===================
    
    @Query("SELECT * FROM trips WHERE driver_id = :driverId AND status IN ('claimed', 'in_progress') ORDER BY claimed_at ASC")
    List<TripEntity> getActiveTripsForDriver(String driverId);
    
    @Query("SELECT * FROM trips WHERE driver_id = :driverId AND status IN ('claimed', 'in_progress') ORDER BY claimed_at ASC")
    LiveData<List<TripEntity>> getActiveTripsForDriverLiveData(String driverId);
    
    @Query("SELECT * FROM trips WHERE session_id = :sessionId AND status IN ('claimed', 'in_progress') ORDER BY claimed_at ASC")
    List<TripEntity> getActiveTripsForSession(String sessionId);
    
    @Query("SELECT COUNT(*) FROM trips WHERE driver_id = :driverId AND status IN ('claimed', 'in_progress')")
    int getActiveTripsCount(String driverId);
    
    @Query("SELECT COUNT(*) FROM trips WHERE driver_id = :driverId AND status IN ('claimed', 'in_progress')")
    LiveData<Integer> getActiveTripsCountLiveData(String driverId);
    
    // =================== SYNC OPERATIONS ===================
    
    @Query("SELECT * FROM trips WHERE sync_status != 'synced'")
    List<TripEntity> getTripsNeedingSync();
    
    @Query("SELECT * FROM trips WHERE sync_status != 'synced'")
    LiveData<List<TripEntity>> getTripsNeedingSyncLiveData();
    
    @Query("SELECT * FROM trips WHERE sync_status = 'pending'")
    List<TripEntity> getPendingTrips();
    
    @Query("SELECT COUNT(*) FROM trips WHERE sync_status != 'synced'")
    int getPendingSyncCount();
    
    @Query("SELECT COUNT(*) FROM trips WHERE sync_status != 'synced'")
    LiveData<Integer> getPendingSyncCountLiveData();
    
    @Query("UPDATE trips SET sync_status = 'synced', synced_at = :syncedAt, server_version = :serverVersion WHERE trip_id = :tripId")
    void markAsSynced(String tripId, long syncedAt, String serverVersion);
    
    @Query("UPDATE trips SET sync_status = 'pending' WHERE trip_id = :tripId")
    void markAsPending(String tripId);
    
    @Query("UPDATE trips SET sync_status = 'error' WHERE trip_id = :tripId")
    void markAsError(String tripId);
    
    // =================== STATISTICS ===================
    
    @Query("SELECT COUNT(*) FROM trips WHERE client_id = :clientId AND status = 'completed'")
    int getCompletedTripsCount(String clientId);
    
    @Query("SELECT COUNT(*) FROM trips WHERE driver_id = :driverId AND status = 'completed'")
    int getCompletedTripsCountByDriver(String driverId);
    
    @Query("SELECT AVG(completed_at - started_at) FROM trips WHERE status = 'completed' AND started_at IS NOT NULL AND completed_at IS NOT NULL")
    Long getAverageTripDuration();
    
    @Query("SELECT SUM(completed_deliveries) FROM trips WHERE driver_id = :driverId AND status = 'completed'")
    int getTotalDeliveriesCompleted(String driverId);
    
    // =================== DELETE OPERATIONS ===================
    
    @Delete
    void deleteTrip(TripEntity trip);
    
    @Query("DELETE FROM trips WHERE trip_id = :tripId")
    void deleteTripById(String tripId);
    
    @Query("DELETE FROM trips WHERE client_id = :clientId")
    void deleteTripsByClient(String clientId);
    
    @Query("DELETE FROM trips WHERE status = :status")
    void deleteTripsByStatus(String status);
    
    @Query("DELETE FROM trips")
    void deleteAllTrips();
    
    // =================== UTILITY OPERATIONS ===================
    
    @Query("SELECT EXISTS(SELECT 1 FROM trips WHERE trip_id = :tripId)")
    boolean tripExists(String tripId);
    
    @Query("SELECT status FROM trips WHERE trip_id = :tripId")
    String getTripStatus(String tripId);
    
    @Query("SELECT driver_id FROM trips WHERE trip_id = :tripId")
    String getTripDriverId(String tripId);
    
    @Query("SELECT session_id FROM trips WHERE trip_id = :tripId")
    String getTripSessionId(String tripId);
    
    @Query("SELECT completed_deliveries, total_deliveries FROM trips WHERE trip_id = :tripId")
    TripProgressInfo getTripProgress(String tripId);
    
    @Query("SELECT COUNT(*) FROM trips")
    int getTotalCount();
    
    /**
     * Get trips that are stale (claimed/started but not updated recently)
     */
    @Query("SELECT * FROM trips WHERE status IN ('claimed', 'in_progress') AND " +
           "(claimed_at < :staleTime OR started_at < :staleTime)")
    List<TripEntity> getStaleTrips(long staleTime);
    
    /**
     * Release trips that have been claimed but not started within timeout
     */
    @Query("UPDATE trips SET status = 'available', session_id = NULL, driver_id = NULL, " +
           "claimed_at = NULL, sync_status = 'pending' WHERE status = 'claimed' AND claimed_at < :timeoutTime")
    void releaseExpiredClaims(long timeoutTime);
    
    /**
     * Alias for getTripsNeedingSync() for backward compatibility
     */
    @Query("SELECT * FROM trips WHERE sync_status != 'synced'")
    List<TripEntity> getUnsyncedTrips();
    
    // Inner class for trip progress info
    public static class TripProgressInfo {
        @ColumnInfo(name = "completed_deliveries")
        public int completedDeliveries;
        
        @ColumnInfo(name = "total_deliveries")
        public int totalDeliveries;
        
        public TripProgressInfo(int completedDeliveries, int totalDeliveries) {
            this.completedDeliveries = completedDeliveries;
            this.totalDeliveries = totalDeliveries;
        }
        
        public float getProgressPercentage() {
            if (totalDeliveries == 0) return 0f;
            return (float) completedDeliveries / totalDeliveries * 100f;
        }
        
        public boolean isCompleted() {
            return completedDeliveries >= totalDeliveries && totalDeliveries > 0;
        }
    }
}