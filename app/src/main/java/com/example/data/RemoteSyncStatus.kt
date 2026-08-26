package com.example.data

/** Lifecycle states persisted by the remote assessment synchronisation flow. */
enum class SyncStatus {
    NEVER,
    SYNCING,
    SYNCED,
    FAILED
}

/** Lifecycle states persisted by the PDF upload queue. */
enum class UploadStatus {
    WAITING,
    UPLOADING,
    SUCCESS,
    FAILED,
    SESSION_EXPIRED,
    REMOTE_UNCONFIGURED
}
