package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StockItem::class, Project::class, RemoteProjectLink::class, RemoteAssetBinding::class, UploadTask::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stockItemDao(): StockItemDao
    abstract fun projectDao(): ProjectDao
    abstract fun remoteSyncDao(): RemoteSyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS remote_project_links (localProjectId TEXT NOT NULL PRIMARY KEY, remoteProjectId TEXT NOT NULL, remoteProjectCode TEXT NOT NULL, remoteProjectName TEXT NOT NULL, lastSyncAt INTEGER, syncState TEXT NOT NULL, lastSyncError TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_remote_project_links_remoteProjectId ON remote_project_links(remoteProjectId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS remote_asset_bindings (stableKey TEXT NOT NULL PRIMARY KEY, localProjectId TEXT NOT NULL, stockUid TEXT NOT NULL, remoteRowId TEXT, companyId TEXT NOT NULL, companyName TEXT NOT NULL, subjectCode TEXT NOT NULL, subjectName TEXT NOT NULL, worksheetKey TEXT NOT NULL, inventoryIndexId TEXT, remoteSnapshotJson TEXT NOT NULL, active INTEGER NOT NULL, syncState TEXT NOT NULL, lastSyncedAt INTEGER, conflictReason TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_remote_asset_bindings_stockUid ON remote_asset_bindings(stockUid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_remote_asset_bindings_localProjectId_active ON remote_asset_bindings(localProjectId, active)")
                db.execSQL("CREATE TABLE IF NOT EXISTS upload_tasks (stableKey TEXT NOT NULL PRIMARY KEY, localProjectId TEXT NOT NULL, stockUid TEXT NOT NULL, filePath TEXT NOT NULL, fileName TEXT NOT NULL, sha256 TEXT, remoteFileId TEXT, status TEXT NOT NULL, attempts INTEGER NOT NULL, lastError TEXT, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_upload_tasks_status ON upload_tasks(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_upload_tasks_localProjectId_status ON upload_tasks(localProjectId, status)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE remote_asset_bindings ADD COLUMN assetCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE remote_asset_bindings ADD COLUMN assetName TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stocktake_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
