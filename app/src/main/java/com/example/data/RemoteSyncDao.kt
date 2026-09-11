package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteSyncDao {
    @Query("SELECT * FROM remote_project_links")
    suspend fun allProjectLinks(): List<RemoteProjectLink>

    @Query("SELECT * FROM remote_project_links WHERE localProjectId = :localId LIMIT 1")
    fun observeProjectLink(localId: String): Flow<RemoteProjectLink?>

    @Query("SELECT * FROM remote_asset_bindings WHERE localProjectId = :projectId")
    fun observeBindings(projectId: String): Flow<List<RemoteAssetBinding>>

    @Query("SELECT * FROM remote_project_links WHERE remoteProjectId = :remoteId LIMIT 1")
    suspend fun findProjectByRemoteId(remoteId: String): RemoteProjectLink?

    @Query("SELECT * FROM remote_project_links WHERE localProjectId = :localId LIMIT 1")
    suspend fun findProjectByLocalId(localId: String): RemoteProjectLink?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjectLink(link: RemoteProjectLink)

    @Query("SELECT * FROM remote_asset_bindings WHERE localProjectId = :projectId")
    suspend fun bindingsForProject(projectId: String): List<RemoteAssetBinding>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBindings(bindings: List<RemoteAssetBinding>)

    @Query("UPDATE remote_asset_bindings SET active = 0, syncState = 'inactive', lastSyncedAt = :at WHERE localProjectId = :projectId AND stableKey NOT IN (:activeKeys)")
    suspend fun markMissingInactive(projectId: String, activeKeys: List<String>, at: Long)

    @Query("UPDATE remote_asset_bindings SET active = 0, syncState = 'inactive', lastSyncedAt = :at WHERE localProjectId = :projectId")
    suspend fun markAllInactive(projectId: String, at: Long)

    @Query("SELECT * FROM upload_tasks WHERE localProjectId = :projectId ORDER BY updatedAt ASC")
    fun uploadTasks(projectId: String): Flow<List<UploadTask>>

    @Query("SELECT * FROM upload_tasks WHERE stableKey = :stableKey LIMIT 1")
    suspend fun uploadTask(stableKey: String): UploadTask?

    @Query("SELECT * FROM remote_asset_bindings WHERE stableKey = :stableKey LIMIT 1")
    suspend fun binding(stableKey: String): RemoteAssetBinding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUploadTask(task: UploadTask)

    @Query("DELETE FROM upload_tasks WHERE stableKey = :stableKey")
    suspend fun deleteUploadTask(stableKey: String)

    @Query("DELETE FROM remote_asset_bindings WHERE localProjectId = :projectId")
    suspend fun deleteBindingsForProject(projectId: String)

    @Query("DELETE FROM upload_tasks WHERE localProjectId = :projectId")
    suspend fun deleteUploadTasksForProject(projectId: String)

    @Query("DELETE FROM remote_project_links WHERE localProjectId = :projectId")
    suspend fun deleteProjectLink(projectId: String)

    @Query("DELETE FROM remote_asset_bindings")
    suspend fun deleteAllBindings()

    @Query("DELETE FROM upload_tasks")
    suspend fun deleteAllUploadTasks()

    @Query("DELETE FROM remote_project_links")
    suspend fun deleteAllProjectLinks()

    @Query("DELETE FROM upload_tasks WHERE stockUid = :stockUid")
    suspend fun deleteUploadTaskForStockUid(stockUid: String)
}
