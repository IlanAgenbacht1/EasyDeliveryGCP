package com.clone.EasyDelivery.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.clone.EasyDelivery.data.entities.DeliveryEntity;

import java.util.List;

/**
 * DAO interface for DeliveryEntity operations
 */
@Dao
public interface DeliveryDao {
    
    // =================== INSERT OPERATIONS ===================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDelivery(DeliveryEntity delivery);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDeliveries(List<DeliveryEntity> deliveries);
    
    // =================== UPDATE OPERATIONS ===================
    
    @Update
    void updateDelivery(DeliveryEntity delivery);
    
    @Query("UPDATE deliveries SET status = :status, sync_status = 'pending' WHERE delivery_id = :deliveryId")
    void updateDeliveryStatus(String deliveryId, String status);
    
    @Query("UPDATE deliveries SET quantity_delivered = :quantityDelivered, recipient_name = :recipientName, " +
           "comment = :comment, captured_latitude = :capturedLat, captured_longitude = :capturedLon, " +
           "delivery_time = :deliveryTime, status = 'delivered', sync_status = 'pending' WHERE delivery_id = :deliveryId")
    void completeDelivery(String deliveryId, int quantityDelivered, String recipientName, String comment,
                         Double capturedLat, Double capturedLon, long deliveryTime);
    
    @Query("UPDATE deliveries SET signature_path = :signaturePath WHERE delivery_id = :deliveryId")
    void updateSignaturePath(String deliveryId, String signaturePath);
    
    @Query("UPDATE deliveries SET photo_path = :photoPath WHERE delivery_id = :deliveryId")
    void updatePhotoPath(String deliveryId, String photoPath);
    
    @Query("UPDATE deliveries SET signature_url = :signatureUrl, sync_status = 'pending' WHERE delivery_id = :deliveryId")
    void updateSignatureUrl(String deliveryId, String signatureUrl);
    
    @Query("UPDATE deliveries SET photo_url = :photoUrl, sync_status = 'pending' WHERE delivery_id = :deliveryId")
    void updatePhotoUrl(String deliveryId, String photoUrl);
    
    @Query("UPDATE deliveries SET files_uploaded = :filesUploaded, sync_status = 'pending' WHERE delivery_id = :deliveryId")
    void updateFilesUploaded(String deliveryId, boolean filesUploaded);
    
    // =================== QUERY OPERATIONS ===================
    
    @Query("SELECT * FROM deliveries WHERE delivery_id = :deliveryId")
    DeliveryEntity getDelivery(String deliveryId);
    
    @Query("SELECT * FROM deliveries WHERE delivery_id = :deliveryId")
    LiveData<DeliveryEntity> getDeliveryLiveData(String deliveryId);
    
    @Query("SELECT * FROM deliveries WHERE trip_id = :tripId ORDER BY order_number ASC")
    List<DeliveryEntity> getDeliveriesByTrip(String tripId);
    
    @Query("SELECT * FROM deliveries WHERE trip_id = :tripId ORDER BY order_number ASC")
    LiveData<List<DeliveryEntity>> getDeliveriesByTripLiveData(String tripId);
    
    @Query("SELECT * FROM deliveries WHERE trip_id = :tripId AND status = :status ORDER BY order_number ASC")
    List<DeliveryEntity> getDeliveriesByTripAndStatus(String tripId, String status);
    
    @Query("SELECT * FROM deliveries WHERE trip_id = :tripId AND status = :status ORDER BY order_number ASC")
    LiveData<List<DeliveryEntity>> getDeliveriesByTripAndStatusLiveData(String tripId, String status);
    
    @Query("SELECT * FROM deliveries WHERE status = :status ORDER BY delivery_time DESC")
    List<DeliveryEntity> getDeliveriesByStatus(String status);
    
    @Query("SELECT * FROM deliveries WHERE status = :status ORDER BY delivery_time DESC")
    LiveData<List<DeliveryEntity>> getDeliveriesByStatusLiveData(String status);
    
    @Query("SELECT * FROM deliveries ORDER BY delivery_time DESC")
    List<DeliveryEntity> getAllDeliveries();
    
    @Query("SELECT * FROM deliveries ORDER BY delivery_time DESC")
    LiveData<List<DeliveryEntity>> getAllDeliveriesLiveData();
    
    // =================== COMPLETION OPERATIONS ===================
    
    @Query("SELECT * FROM deliveries WHERE status = 'delivered' ORDER BY delivery_time DESC")
    List<DeliveryEntity> getCompletedDeliveries();
    
    @Query("SELECT * FROM deliveries WHERE status = 'delivered' ORDER BY delivery_time DESC")
    LiveData<List<DeliveryEntity>> getCompletedDeliveriesLiveData();
    
    @Query("SELECT * FROM deliveries WHERE trip_id = :tripId AND status = 'delivered'")
    List<DeliveryEntity> getCompletedDeliveriesByTrip(String tripId);
    
    @Query("SELECT * FROM deliveries WHERE trip_id = :tripId AND status = 'delivered'")
    LiveData<List<DeliveryEntity>> getCompletedDeliveriesByTripLiveData(String tripId);
    
    @Query("SELECT COUNT(*) FROM deliveries WHERE trip_id = :tripId AND status = 'delivered'")
    int getCompletedDeliveryCount(String tripId);
    
    @Query("SELECT COUNT(*) FROM deliveries WHERE trip_id = :tripId AND status = 'delivered'")
    LiveData<Integer> getCompletedDeliveryCountLiveData(String tripId);
    
    // =================== SYNC OPERATIONS ===================
    
    @Query("SELECT * FROM deliveries WHERE sync_status != 'synced'")
    List<DeliveryEntity> getDeliveriesNeedingSync();
    
    @Query("SELECT * FROM deliveries WHERE sync_status = 'pending'")
    List<DeliveryEntity> getPendingDeliveries();
    
    @Query("SELECT * FROM deliveries WHERE sync_status = 'pending' AND status = 'delivered'")
    List<DeliveryEntity> getPendingCompletedDeliveries();
    
    @Query("SELECT COUNT(*) FROM deliveries WHERE sync_status != 'synced'")
    int getPendingSyncCount();
    
    @Query("SELECT COUNT(*) FROM deliveries WHERE sync_status != 'synced'")
    LiveData<Integer> getPendingSyncCountLiveData();
    
    @Query("UPDATE deliveries SET sync_status = 'synced', synced_at = :syncedAt, server_version = :serverVersion WHERE delivery_id = :deliveryId")
    void markAsSynced(String deliveryId, long syncedAt, String serverVersion);
    
    @Query("UPDATE deliveries SET sync_status = 'pending' WHERE delivery_id = :deliveryId")
    void markAsPending(String deliveryId);
    
    @Query("UPDATE deliveries SET sync_status = 'error' WHERE delivery_id = :deliveryId")
    void markAsError(String deliveryId);
    
    // =================== FILE OPERATIONS ===================
    
    @Query("SELECT * FROM deliveries WHERE (signature_path IS NOT NULL AND signature_path != '') OR (photo_path IS NOT NULL AND photo_path != '')")
    List<DeliveryEntity> getDeliveriesWithFiles();
    
    @Query("SELECT * FROM deliveries WHERE files_uploaded = 0 AND ((signature_path IS NOT NULL AND signature_path != '') OR (photo_path IS NOT NULL AND photo_path != ''))")
    List<DeliveryEntity> getDeliveriesNeedingFileUpload();
    
    @Query("SELECT * FROM deliveries WHERE files_uploaded = 1 AND ((signature_url IS NOT NULL AND signature_url != '') OR (photo_url IS NOT NULL AND photo_url != ''))")
    List<DeliveryEntity> getDeliveriesWithUploadedFiles();
    
    @Query("SELECT COUNT(*) FROM deliveries WHERE files_uploaded = 0 AND ((signature_path IS NOT NULL AND signature_path != '') OR (photo_path IS NOT NULL AND photo_path != ''))")
    int getPendingFileUploadCount();
    
    @Query("SELECT COUNT(*) FROM deliveries WHERE files_uploaded = 0 AND ((signature_path IS NOT NULL AND signature_path != '') OR (photo_path IS NOT NULL AND photo_path != ''))")
    LiveData<Integer> getPendingFileUploadCountLiveData();
    
    // =================== SEARCH OPERATIONS ===================
    
    @Query("SELECT * FROM deliveries WHERE order_number LIKE '%' || :searchTerm || '%' OR customer_name LIKE '%' || :searchTerm || '%' OR address LIKE '%' || :searchTerm || '%'")
    List<DeliveryEntity> searchDeliveries(String searchTerm);
    
    @Query("SELECT * FROM deliveries WHERE order_number = :orderNumber")
    DeliveryEntity getDeliveryByOrderNumber(String orderNumber);
    
    @Query("SELECT * FROM deliveries WHERE order_number = :orderNumber")
    LiveData<DeliveryEntity> getDeliveryByOrderNumberLiveData(String orderNumber);
    
    // =================== STATISTICS ===================
    
    @Query("SELECT COUNT(*) FROM deliveries WHERE trip_id = :tripId")
    int getTotalDeliveriesForTrip(String tripId);
    
    @Query("SELECT AVG(delivery_time - (SELECT created_at FROM trips WHERE trip_id = deliveries.trip_id)) " +
           "FROM deliveries WHERE status = 'delivered' AND delivery_time IS NOT NULL")
    Long getAverageDeliveryTime();
    
    @Query("SELECT COUNT(*) FROM deliveries WHERE status = 'delivered' AND delivery_time >= :fromTime AND delivery_time <= :toTime")
    int getDeliveriesCompletedInTimeRange(long fromTime, long toTime);
    
    @Query("SELECT SUM(quantity_delivered) FROM deliveries WHERE status = 'delivered' AND quantity_delivered IS NOT NULL")
    Integer getTotalQuantityDelivered();
    
    // =================== DELETE OPERATIONS ===================
    
    @Delete
    void deleteDelivery(DeliveryEntity delivery);
    
    @Query("DELETE FROM deliveries WHERE delivery_id = :deliveryId")
    void deleteDeliveryById(String deliveryId);
    
    @Query("DELETE FROM deliveries WHERE trip_id = :tripId")
    void deleteDeliveriesByTrip(String tripId);
    
    @Query("DELETE FROM deliveries WHERE status = :status")
    void deleteDeliveriesByStatus(String status);
    
    @Query("DELETE FROM deliveries WHERE delivery_time < :cutoffTime")
    void deleteOldDeliveries(long cutoffTime);
    
    @Query("DELETE FROM deliveries")
    void deleteAllDeliveries();
    
    // =================== UTILITY OPERATIONS ===================
    
    @Query("SELECT EXISTS(SELECT 1 FROM deliveries WHERE delivery_id = :deliveryId)")
    boolean deliveryExists(String deliveryId);
    
    @Query("SELECT EXISTS(SELECT 1 FROM deliveries WHERE order_number = :orderNumber)")
    boolean orderNumberExists(String orderNumber);
    
    @Query("SELECT status FROM deliveries WHERE delivery_id = :deliveryId")
    String getDeliveryStatus(String deliveryId);
    
    @Query("SELECT trip_id FROM deliveries WHERE delivery_id = :deliveryId")
    String getTripIdForDelivery(String deliveryId);
    
    @Query("SELECT COUNT(*) FROM deliveries")
    int getTotalCount();
    
    /**
     * Get next delivery in sequence for a trip
     */
    @Query("SELECT * FROM deliveries WHERE trip_id = :tripId AND status = 'pending' ORDER BY order_number ASC LIMIT 1")
    DeliveryEntity getNextDeliveryForTrip(String tripId);
    
    /**
     * Get delivery progress for trip
     */
    @Query("SELECT COUNT(*) as total, SUM(CASE WHEN status = 'delivered' THEN 1 ELSE 0 END) as completed FROM deliveries WHERE trip_id = :tripId")
    DeliveryProgress getDeliveryProgress(String tripId);
    
    /**
     * Alias for getDeliveriesNeedingSync() for backward compatibility
     */
    @Query("SELECT * FROM deliveries WHERE sync_status != 'synced'")
    List<DeliveryEntity> getUnsyncedDeliveries();
    
    // Inner class for delivery progress
    public static class DeliveryProgress {
        public int total;
        public int completed;
        
        public DeliveryProgress(int total, int completed) {
            this.total = total;
            this.completed = completed;
        }
        
        public float getProgressPercentage() {
            if (total == 0) return 0f;
            return (float) completed / total * 100f;
        }
        
        public boolean isCompleted() {
            return completed >= total && total > 0;
        }
    }
}