package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "remote_project_links", indices = [Index(value = ["remoteProjectId"], unique = true)])
data class RemoteProjectLink(
    @PrimaryKey val localProjectId: String,
    val remoteProjectId: String,
    val remoteProjectCode: String = "",
    val remoteProjectName: String = "",
    val lastSyncAt: Long? = null,
    val syncState: String = "never",
    val lastSyncError: String? = null
)

@Entity(tableName = "remote_asset_bindings", indices = [Index(value = ["stockUid"], unique = true), Index(value = ["localProjectId", "active"])])
data class RemoteAssetBinding(
    @PrimaryKey val stableKey: String,
    val localProjectId: String,
    val stockUid: String,
    val remoteRowId: String? = null,
    val companyId: String,
    val companyName: String,
    val subjectCode: String,
    val subjectName: String,
    val worksheetKey: String = "",
    val inventoryIndexId: String? = null,
    val remoteSnapshotJson: String = "",
    val active: Boolean = true,
    val syncState: String = "synced",
    val lastSyncedAt: Long? = null,
    val conflictReason: String? = null
)

@Entity(tableName = "upload_tasks", indices = [Index(value = ["status"]), Index(value = ["localProjectId", "status"])])
data class UploadTask(
    @PrimaryKey val stableKey: String,
    val localProjectId: String,
    val stockUid: String,
    val filePath: String,
    val fileName: String,
    val sha256: String? = null,
    val remoteFileId: String? = null,
    val status: String = "waiting",
    val attempts: Int = 0,
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
