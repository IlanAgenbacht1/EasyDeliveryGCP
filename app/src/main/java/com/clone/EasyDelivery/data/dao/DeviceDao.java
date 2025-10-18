package com.clone.EasyDelivery.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.clone.EasyDelivery.data.entities.DeviceEntity;

import java.util.List;

/**
 * DAO interface for DeviceEntity operations
 */
@Dao
public interface DeviceDao {
    
    // =================== INSERT OPERATIONS ===================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDevice(DeviceEntity device);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDevices(List<DeviceEntity> devices);
    
    // =================== UPDATE OPERATIONS ===================
    
    @Update
    void updateDevice(DeviceEntity device);
    
    @Query("UPDATE devices SET last_seen_at = :lastSeenAt, sync_status = 'pending' WHERE device_id = :deviceId")
    void updateLastSeen(String deviceId, long lastSeenAt);
    
    @Query("UPDATE devices SET is_approved = :isApproved, sync_status = 'pending' WHERE device_id = :deviceId")
    void updateApprovalStatus(String deviceId, boolean isApproved);
    
    @Query("UPDATE devices SET sync_status = :syncStatus, synced_at = :syncedAt WHERE device_id = :deviceId")
    void updateSyncStatus(String deviceId, String syncStatus, long syncedAt);
    
    // =================== QUERY OPERATIONS ===================
    
    @Query("SELECT * FROM devices WHERE device_id = :deviceId")
    DeviceEntity getDevice(String deviceId);
    
    @Query("SELECT * FROM devices WHERE device_id = :deviceId")
    LiveData<DeviceEntity> getDeviceLiveData(String deviceId);
    
    @Query("SELECT * FROM devices WHERE client_id = :clientId")
    List<DeviceEntity> getDevicesByClient(String clientId);
    
    @Query("SELECT * FROM devices WHERE client_id = :clientId")
    LiveData<List<DeviceEntity>> getDevicesByClientLiveData(String clientId);
    
    @Query("SELECT * FROM devices ORDER BY registered_at DESC")
    List<DeviceEntity> getAllDevices();
    
    @Query("SELECT * FROM devices ORDER BY registered_at DESC")
    LiveData<List<DeviceEntity>> getAllDevicesLiveData();
    
    // =================== SYNC OPERATIONS ===================
    
    @Query("SELECT * FROM devices WHERE sync_status != 'synced'")
    List<DeviceEntity> getDevicesNeedingSync();
    
    @Query("SELECT * FROM devices WHERE sync_status = 'pending'")
    List<DeviceEntity> getPendingDevices();
    
    @Query("SELECT * FROM devices WHERE sync_status = 'error'")
    List<DeviceEntity> getErrorDevices();
    
    @Query("SELECT COUNT(*) FROM devices WHERE sync_status != 'synced'")
    int getPendingSyncCount();
    
    @Query("SELECT COUNT(*) FROM devices WHERE sync_status != 'synced'")
    LiveData<Integer> getPendingSyncCountLiveData();
    
    // =================== APPROVAL OPERATIONS ===================
    
    @Query("SELECT * FROM devices WHERE is_approved = 1")
    List<DeviceEntity> getApprovedDevices();
    
    @Query("SELECT * FROM devices WHERE is_approved = 0")
    List<DeviceEntity> getUnapprovedDevices();
    
    @Query("SELECT COUNT(*) FROM devices WHERE client_id = :clientId AND is_approved = 1")
    int getApprovedDeviceCount(String clientId);
    
    // =================== DELETE OPERATIONS ===================
    
    @Delete
    void deleteDevice(DeviceEntity device);
    
    @Query("DELETE FROM devices WHERE device_id = :deviceId")
    void deleteDeviceById(String deviceId);
    
    @Query("DELETE FROM devices WHERE client_id = :clientId")
    void deleteDevicesByClient(String clientId);
    
    @Query("DELETE FROM devices")
    void deleteAllDevices();
    
    // =================== UTILITY OPERATIONS ===================
    
    @Query("SELECT EXISTS(SELECT 1 FROM devices WHERE device_id = :deviceId)")
    boolean deviceExists(String deviceId);
    
    @Query("SELECT device_type FROM devices WHERE device_id = :deviceId")
    String getDeviceType(String deviceId);
    
    @Query("SELECT client_id FROM devices WHERE device_id = :deviceId")
    String getClientIdForDevice(String deviceId);
    
    @Query("SELECT COUNT(*) FROM devices")
    int getTotalCount();
    
    /**
     * Mark device as synced with server version
     */
    @Query("UPDATE devices SET sync_status = 'synced', synced_at = :syncedAt, server_version = :serverVersion WHERE device_id = :deviceId")
    void markAsSynced(String deviceId, long syncedAt, String serverVersion);
    
    /**
     * Mark device as pending sync
     */
    @Query("UPDATE devices SET sync_status = 'pending' WHERE device_id = :deviceId")
    void markAsPending(String deviceId);
    
    /**
     * Mark device as error during sync
     */
    @Query("UPDATE devices SET sync_status = 'error' WHERE device_id = :deviceId")
    void markAsError(String deviceId);
    
    /**
     * Get devices that haven't been seen recently (for cleanup)
     */
    @Query("SELECT * FROM devices WHERE last_seen_at < :cutoffTime")
    List<DeviceEntity> getStaleDevices(long cutoffTime);
}