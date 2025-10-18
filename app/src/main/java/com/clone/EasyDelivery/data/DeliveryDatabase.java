package com.clone.EasyDelivery.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabase.Callback;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.clone.EasyDelivery.data.dao.DeliveryDao;
import com.clone.EasyDelivery.data.dao.DeviceDao;
import com.clone.EasyDelivery.data.dao.OfflineActionDao;
import com.clone.EasyDelivery.data.dao.SessionDao;
import com.clone.EasyDelivery.data.dao.TripDao;
import com.clone.EasyDelivery.data.entities.DeliveryEntity;
import com.clone.EasyDelivery.data.entities.DeviceEntity;
import com.clone.EasyDelivery.data.entities.OfflineActionEntity;
import com.clone.EasyDelivery.data.entities.SessionEntity;
import com.clone.EasyDelivery.data.entities.TripEntity;

/**
 * Main Room database class for EasyDelivery
 * Manages all entities and provides access to DAOs
 */
@Database(
    entities = {
        DeviceEntity.class,
        SessionEntity.class,
        TripEntity.class,
        DeliveryEntity.class,
        OfflineActionEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class DeliveryDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "delivery_database";
    
    private static volatile DeliveryDatabase INSTANCE;
    
    // Abstract DAO getters
    public abstract DeviceDao deviceDao();
    public abstract SessionDao sessionDao();
    public abstract TripDao tripDao();
    public abstract DeliveryDao deliveryDao();
    public abstract OfflineActionDao offlineActionDao();
    
    /**
     * Get database instance with singleton pattern
     */
    public static DeliveryDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DeliveryDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        DeliveryDatabase.class,
                        DATABASE_NAME
                    )
                    .addCallback(DATABASE_CALLBACK)
                    .addMigrations(MIGRATION_LEGACY_TO_1)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Database callback for initialization
     */
    private static final Callback DATABASE_CALLBACK = new Callback() {
        @Override
        public void onCreate(SupportSQLiteDatabase db) {
            super.onCreate(db);
            // Database created for first time
        }
        
        @Override
        public void onOpen(SupportSQLiteDatabase db) {
            super.onOpen(db);
            // Database opened
        }
    };
    
    /**
     * Migration from legacy SQLite database to Room
     * This will help preserve existing data during the transition
     */
    private static final Migration MIGRATION_LEGACY_TO_1 = new Migration(0, 1) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create new Room tables
            createDevicesTable(database);
            createSessionsTable(database);
            createTripsTable(database);
            createDeliveriesTable(database);
            createOfflineActionsTable(database);
            
            // Migrate data from legacy DeliveryDb if it exists
            migrateLegacyData(database);
        }
        
        private void createDevicesTable(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS devices (" +
                "device_id TEXT PRIMARY KEY NOT NULL, " +
                "client_id TEXT, " +
                "device_type TEXT, " +
                "is_approved INTEGER NOT NULL, " +
                "registered_at INTEGER NOT NULL, " +
                "last_seen_at INTEGER NOT NULL, " +
                "synced_at INTEGER NOT NULL, " +
                "sync_status TEXT NOT NULL, " +
                "server_version TEXT)");
        }
        
        private void createSessionsTable(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS sessions (" +
                "session_id TEXT PRIMARY KEY NOT NULL, " +
                "driver_id TEXT, " +
                "device_id TEXT, " +
                "client_id TEXT, " +
                "vehicle_info TEXT, " +
                "is_active INTEGER NOT NULL, " +
                "started_at INTEGER NOT NULL, " +
                "ended_at INTEGER, " +
                "last_active_at INTEGER NOT NULL, " +
                "synced_at INTEGER NOT NULL, " +
                "sync_status TEXT, " +
                "FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE)");
            
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_device_id ON sessions(device_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_driver_id ON sessions(driver_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_is_active ON sessions(is_active)");
        }
        
        private void createTripsTable(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS trips (" +
                "trip_id TEXT PRIMARY KEY NOT NULL, " +
                "client_id TEXT, " +
                "status TEXT, " +
                "created_at INTEGER NOT NULL, " +
                "claimed_at INTEGER, " +
                "started_at INTEGER, " +
                "completed_at INTEGER, " +
                "session_id TEXT, " +
                "driver_id TEXT, " +
                "vehicle_info TEXT, " +
                "total_deliveries INTEGER NOT NULL, " +
                "completed_deliveries INTEGER NOT NULL, " +
                "synced_at INTEGER NOT NULL, " +
                "sync_status TEXT, " +
                "server_version TEXT, " +
                "FOREIGN KEY(session_id) REFERENCES sessions(session_id) ON DELETE SET NULL)");
            
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trips_client_id ON trips(client_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trips_session_id ON trips(session_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trips_status ON trips(status)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trips_driver_id ON trips(driver_id)");
        }
        
        private void createDeliveriesTable(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS deliveries (" +
                "delivery_id TEXT PRIMARY KEY NOT NULL, " +
                "trip_id TEXT, " +
                "order_number TEXT, " +
                "customer_name TEXT, " +
                "customer_contact TEXT, " +
                "customer_number TEXT, " +
                "customer_email TEXT, " +
                "address TEXT, " +
                "quantity_expected INTEGER NOT NULL, " +
                "quantity_delivered INTEGER, " +
                "status TEXT, " +
                "latitude REAL, " +
                "longitude REAL, " +
                "captured_latitude REAL, " +
                "captured_longitude REAL, " +
                "signature_path TEXT, " +
                "signature_url TEXT, " +
                "photo_path TEXT, " +
                "photo_url TEXT, " +
                "comment TEXT, " +
                "recipient_name TEXT, " +
                "delivery_time INTEGER, " +
                "synced_at INTEGER NOT NULL, " +
                "sync_status TEXT, " +
                "server_version TEXT, " +
                "files_uploaded INTEGER NOT NULL, " +
                "FOREIGN KEY(trip_id) REFERENCES trips(trip_id) ON DELETE CASCADE)");
            
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deliveries_trip_id ON deliveries(trip_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deliveries_status ON deliveries(status)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deliveries_order_number ON deliveries(order_number)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deliveries_sync_status ON deliveries(sync_status)");
        }
        
        private void createOfflineActionsTable(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS offline_actions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "action_type TEXT, " +
                "resource_id TEXT, " +
                "session_id TEXT, " +
                "payload TEXT, " +
                "status TEXT, " +
                "retry_count INTEGER NOT NULL, " +
                "max_retries INTEGER NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "last_attempt_at INTEGER, " +
                "next_retry_at INTEGER, " +
                "error_message TEXT, " +
                "priority INTEGER NOT NULL)");
            
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_actions_action_type ON offline_actions(action_type)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_actions_resource_id ON offline_actions(resource_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_actions_status ON offline_actions(status)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_actions_retry_count ON offline_actions(retry_count)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_actions_created_at ON offline_actions(created_at)");
        }
        
        private void migrateLegacyData(SupportSQLiteDatabase database) {
            // Check if legacy tables exist and migrate data
            try {
                // Migrate from DeliveryTable to deliveries
                database.execSQL("INSERT OR IGNORE INTO deliveries " +
                    "(delivery_id, trip_id, order_number, customer_name, address, quantity_expected, " +
                    "latitude, longitude, captured_latitude, captured_longitude, signature_path, " +
                    "photo_path, comment, delivery_time, status, synced_at, sync_status, files_uploaded) " +
                    "SELECT _docu, _tripId, _orderNumber, _customer, _address, _parcelQty, " +
                    "_latitude, _longitude, _capturedLatitude, _capturedLongitude, _sign, " +
                    "_pic, _comment, _time, " +
                    "CASE WHEN _completed = 1 THEN 'delivered' ELSE 'pending' END, " +
                    "strftime('%s', 'now') * 1000, " +
                    "CASE WHEN _uploaded = 1 THEN 'synced' ELSE 'pending' END, " +
                    "_uploaded " +
                    "FROM DeliveryTable WHERE EXISTS (SELECT name FROM sqlite_master WHERE type='table' AND name='DeliveryTable')");
                
                // Migrate sync metadata if SyncMetadataTable exists
                database.execSQL("UPDATE deliveries SET synced_at = s._lastCloudSync, sync_status = s._syncStatus " +
                    "FROM SyncMetadataTable s WHERE deliveries.trip_id = s._syncTripId " +
                    "AND EXISTS (SELECT name FROM sqlite_master WHERE type='table' AND name='SyncMetadataTable')");
                
            } catch (Exception e) {
                // Legacy migration failed, but continue - new installs won't have legacy data
            }
        }
    };
    
    /**
     * Clear all data (for testing or reset purposes)
     */
    public void clearAllTables() {
        runInTransaction(() -> {
            offlineActionDao().deleteAllActions();
            deliveryDao().deleteAllDeliveries();
            tripDao().deleteAllTrips();
            sessionDao().deleteAllSessions();
            deviceDao().deleteAllDevices();
        });
    }
    
    /**
     * Get database size information
     */
    public DatabaseStats getDatabaseStats() {
        return new DatabaseStats(
            deviceDao().getTotalCount(),
            sessionDao().getTotalCount(),
            tripDao().getTotalCount(),
            deliveryDao().getTotalCount(),
            offlineActionDao().getTotalCount()
        );
    }
    
    public static class DatabaseStats {
        public final int deviceCount;
        public final int sessionCount;
        public final int tripCount;
        public final int deliveryCount;
        public final int offlineActionCount;
        
        public DatabaseStats(int deviceCount, int sessionCount, int tripCount, 
                           int deliveryCount, int offlineActionCount) {
            this.deviceCount = deviceCount;
            this.sessionCount = sessionCount;
            this.tripCount = tripCount;
            this.deliveryCount = deliveryCount;
            this.offlineActionCount = offlineActionCount;
        }
        
        public int getTotalRecords() {
            return deviceCount + sessionCount + tripCount + deliveryCount + offlineActionCount;
        }
    }
}