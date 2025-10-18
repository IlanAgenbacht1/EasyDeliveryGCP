package com.clone.EasyDelivery.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.clone.EasyDelivery.data.entities.SessionEntity;

import java.util.List;

/**
 * DAO interface for SessionEntity operations
 */
@Dao
public interface SessionDao {
    
    // =================== INSERT OPERATIONS ===================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSession(SessionEntity session);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSessions(List<SessionEntity> sessions);
    
    // =================== UPDATE OPERATIONS ===================
    
    @Update
    void updateSession(SessionEntity session);
    
    @Query("UPDATE sessions SET is_active = :isActive, last_active_at = :lastActiveAt WHERE session_id = :sessionId")
    void updateSessionActivity(String sessionId, boolean isActive, long lastActiveAt);
    
    @Query("UPDATE sessions SET ended_at = :endedAt, is_active = 0, sync_status = 'pending' WHERE session_id = :sessionId")
    void endSession(String sessionId, long endedAt);
    
    // =================== QUERY OPERATIONS ===================
    
    @Query("SELECT * FROM sessions WHERE session_id = :sessionId")
    SessionEntity getSession(String sessionId);
    
    @Query("SELECT * FROM sessions WHERE session_id = :sessionId")
    LiveData<SessionEntity> getSessionLiveData(String sessionId);
    
    @Query("SELECT * FROM sessions WHERE driver_id = :driverId ORDER BY started_at DESC")
    List<SessionEntity> getSessionsByDriver(String driverId);
    
    @Query("SELECT * FROM sessions WHERE driver_id = :driverId ORDER BY started_at DESC")
    LiveData<List<SessionEntity>> getSessionsByDriverLiveData(String driverId);
    
    @Query("SELECT * FROM sessions WHERE is_active = 1")
    List<SessionEntity> getActiveSessions();
    
    @Query("SELECT * FROM sessions WHERE driver_id = :driverId AND is_active = 1")
    SessionEntity getActiveSessionForDriver(String driverId);
    
    @Query("SELECT * FROM sessions WHERE driver_id = :driverId AND is_active = 1")
    LiveData<SessionEntity> getActiveSessionForDriverLiveData(String driverId);
    
    // =================== SYNC OPERATIONS ===================
    
    @Query("SELECT * FROM sessions WHERE sync_status != 'synced'")
    List<SessionEntity> getSessionsNeedingSync();
    
    @Query("UPDATE sessions SET sync_status = 'synced', synced_at = :syncedAt WHERE session_id = :sessionId")
    void markAsSynced(String sessionId, long syncedAt);
    
    @Query("UPDATE sessions SET sync_status = 'pending' WHERE session_id = :sessionId")
    void markAsPending(String sessionId);
    
    // =================== DELETE OPERATIONS ===================
    
    @Query("DELETE FROM sessions WHERE session_id = :sessionId")
    void deleteSessionById(String sessionId);
    
    @Query("DELETE FROM sessions")
    void deleteAllSessions();
    
    // =================== UTILITY OPERATIONS ===================
    
    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE session_id = :sessionId)")
    boolean sessionExists(String sessionId);
    
    @Query("SELECT COUNT(*) FROM sessions")
    int getTotalCount();
    
    /**
     * Check if a session is active
     */
    @Query("SELECT is_active FROM sessions WHERE session_id = :sessionId")
    boolean isSessionActive(String sessionId);
    
    /**
     * End inactive sessions within a time range
     */
    @Query("UPDATE sessions SET ended_at = :endTime, is_active = 0, sync_status = 'pending' WHERE is_active = 1 AND last_active_at BETWEEN :startTime AND :endTime")
    void endInactiveSessions(long startTime, long endTime);
}