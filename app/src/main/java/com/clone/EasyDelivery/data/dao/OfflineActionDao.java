package com.clone.EasyDelivery.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.clone.EasyDelivery.data.entities.OfflineActionEntity;

import java.util.List;

/**
 * DAO interface for OfflineActionEntity operations
 * Handles queued offline operations that need to be synced when online
 */
@Dao
public interface OfflineActionDao {
    
    // =================== INSERT OPERATIONS ===================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertAction(OfflineActionEntity action);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertActions(List<OfflineActionEntity> actions);
    
    // =================== UPDATE OPERATIONS ===================
    
    @Update
    void updateAction(OfflineActionEntity action);
    
    @Query("UPDATE offline_actions SET status = :status, last_attempt_at = :lastAttemptAt WHERE id = :id")
    void updateActionStatus(long id, String status, long lastAttemptAt);
    
    @Query("UPDATE offline_actions SET retry_count = retry_count + 1, last_attempt_at = :lastAttemptAt, " +
           "next_retry_at = :nextRetryAt, error_message = :errorMessage WHERE id = :id")
    void incrementRetryCount(long id, long lastAttemptAt, long nextRetryAt, String errorMessage);
    
    @Query("UPDATE offline_actions SET status = 'failed', error_message = :errorMessage WHERE id = :id")
    void markAsFailed(long id, String errorMessage);
    
    @Query("UPDATE offline_actions SET status = 'completed' WHERE id = :id")
    void markAsCompleted(long id);
    
    // =================== QUERY OPERATIONS ===================
    
    @Query("SELECT * FROM offline_actions WHERE id = :id")
    OfflineActionEntity getAction(long id);
    
    @Query("SELECT * FROM offline_actions WHERE id = :id")
    LiveData<OfflineActionEntity> getActionLiveData(long id);
    
    @Query("SELECT * FROM offline_actions ORDER BY priority DESC, created_at ASC")
    List<OfflineActionEntity> getAllActions();
    
    @Query("SELECT * FROM offline_actions ORDER BY priority DESC, created_at ASC")
    LiveData<List<OfflineActionEntity>> getAllActionsLiveData();
    
    @Query("SELECT * FROM offline_actions WHERE status = :status ORDER BY priority DESC, created_at ASC")
    List<OfflineActionEntity> getActionsByStatus(String status);
    
    @Query("SELECT * FROM offline_actions WHERE status = :status ORDER BY priority DESC, created_at ASC")
    LiveData<List<OfflineActionEntity>> getActionsByStatusLiveData(String status);
    
    @Query("SELECT * FROM offline_actions WHERE action_type = :actionType ORDER BY created_at ASC")
    List<OfflineActionEntity> getActionsByType(String actionType);
    
    @Query("SELECT * FROM offline_actions WHERE resource_id = :resourceId ORDER BY created_at ASC")
    List<OfflineActionEntity> getActionsByResourceId(String resourceId);
    
    // =================== PENDING OPERATIONS ===================
    
    @Query("SELECT * FROM offline_actions WHERE status = 'pending' ORDER BY priority DESC, created_at ASC")
    List<OfflineActionEntity> getPendingActions();
    
    @Query("SELECT * FROM offline_actions WHERE status = 'pending' ORDER BY priority DESC, created_at ASC")
    LiveData<List<OfflineActionEntity>> getPendingActionsLiveData();
    
    @Query("SELECT * FROM offline_actions WHERE status = 'pending' AND next_retry_at <= :currentTime ORDER BY priority DESC, created_at ASC")
    List<OfflineActionEntity> getRetryableActions(long currentTime);
    
    @Query("SELECT COUNT(*) FROM offline_actions WHERE status = 'pending'")
    int getPendingActionCount();
    
    @Query("SELECT COUNT(*) FROM offline_actions WHERE status = 'pending'")
    LiveData<Integer> getPendingActionCountLiveData();
    
    // =================== RETRY OPERATIONS ===================
    
    @Query("SELECT * FROM offline_actions WHERE status = 'retrying' ORDER BY priority DESC, next_retry_at ASC")
    List<OfflineActionEntity> getRetryingActions();
    
    @Query("SELECT * FROM offline_actions WHERE retry_count < max_retries AND status IN ('failed', 'retrying') " +
           "ORDER BY priority DESC, next_retry_at ASC")
    List<OfflineActionEntity> getActionsEligibleForRetry();
    
    @Query("SELECT * FROM offline_actions WHERE retry_count >= max_retries AND status = 'failed' " +
           "ORDER BY created_at DESC")
    List<OfflineActionEntity> getFailedActions();
    
    // =================== SESSION OPERATIONS ===================
    
    @Query("SELECT * FROM offline_actions WHERE session_id = :sessionId ORDER BY created_at ASC")
    List<OfflineActionEntity> getActionsBySession(String sessionId);
    
    @Query("SELECT COUNT(*) FROM offline_actions WHERE session_id = :sessionId AND status = 'pending'")
    int getPendingActionCountForSession(String sessionId);
    
    // =================== CLEANUP OPERATIONS ===================
    
    @Query("SELECT * FROM offline_actions WHERE status = 'completed' AND created_at < :cutoffTime")
    List<OfflineActionEntity> getCompletedActionsOlderThan(long cutoffTime);
    
    @Query("DELETE FROM offline_actions WHERE status = 'completed' AND created_at < :cutoffTime")
    int deleteCompletedActionsOlderThan(long cutoffTime);
    
    @Query("DELETE FROM offline_actions WHERE status = 'failed' AND retry_count >= max_retries AND created_at < :cutoffTime")
    int deleteFailedActionsOlderThan(long cutoffTime);
    
    // =================== DELETE OPERATIONS ===================
    
    @Delete
    void deleteAction(OfflineActionEntity action);
    
    @Query("DELETE FROM offline_actions WHERE id = :id")
    void deleteActionById(long id);
    
    @Query("DELETE FROM offline_actions WHERE resource_id = :resourceId")
    void deleteActionsByResourceId(String resourceId);
    
    @Query("DELETE FROM offline_actions WHERE session_id = :sessionId")
    void deleteActionsBySession(String sessionId);
    
    @Query("DELETE FROM offline_actions")
    void deleteAllActions();
    
    // =================== STATISTICS ===================
    
    @Query("SELECT COUNT(*) FROM offline_actions WHERE status = 'completed'")
    int getCompletedActionCount();
    
    @Query("SELECT COUNT(*) FROM offline_actions WHERE status = 'failed'")
    int getFailedActionCount();
    
    @Query("SELECT action_type, COUNT(*) as count FROM offline_actions GROUP BY action_type")
    List<ActionTypeCount> getActionCountsByType();
    
    @Query("SELECT AVG(last_attempt_at - created_at) FROM offline_actions WHERE status = 'completed' AND last_attempt_at IS NOT NULL")
    Long getAverageCompletionTime();
    
    // =================== UTILITY OPERATIONS ===================
    
    @Query("SELECT EXISTS(SELECT 1 FROM offline_actions WHERE resource_id = :resourceId AND action_type = :actionType)")
    boolean actionExists(String resourceId, String actionType);
    
    @Query("SELECT COUNT(*) FROM offline_actions")
    int getTotalCount();
    
    // =================== CONVENIENCE ALIASES ===================
    
    /**
     * Marks an action as completed with timestamp
     */
    @Query("UPDATE offline_actions SET status = 'completed', last_attempt_at = :completedAt WHERE id = :id")
    void markActionCompleted(long id, long completedAt);
    
    /**
     * Marks an action as failed with error message
     */
    @Query("UPDATE offline_actions SET status = 'failed', error_message = :errorMessage WHERE id = :id")
    void markActionFailed(long id, String errorMessage);
    
    /**
     * Deletes old completed actions (alias for cleanup)
     */
    @Query("DELETE FROM offline_actions WHERE status = 'completed' AND created_at < :cutoffTime")
    void deleteOldCompletedActions(long cutoffTime);
    
    /**
     * Reset retry count for failed actions (for manual retry)
     */
    @Query("UPDATE offline_actions SET status = 'pending', retry_count = 0, next_retry_at = :nextRetryAt, error_message = NULL WHERE id = :id")
    void resetActionForRetry(long id, long nextRetryAt);
    
    /**
     * Get high priority actions first
     */
    @Query("SELECT * FROM offline_actions WHERE status = 'pending' AND priority >= :minPriority ORDER BY priority DESC, created_at ASC")
    List<OfflineActionEntity> getHighPriorityActions(int minPriority);
    
    // Inner class for action type statistics
    public static class ActionTypeCount {
        public String action_type;
        public int count;
        
        public ActionTypeCount(String action_type, int count) {
            this.action_type = action_type;
            this.count = count;
        }
    }
}