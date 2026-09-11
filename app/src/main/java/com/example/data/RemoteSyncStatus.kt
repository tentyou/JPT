package com.example.data

/** Lifecycle states persisted by the remote assessment synchronisation flow. */
enum class SyncStatus(val storageValue: String) {
    NEVER("never"),
    SYNCING("syncing"),
    SYNCED("synced"),
    FAILED("failed"),
    UNVERIFIED("unverified"),
    INACTIVE("inactive"),
    CONFLICT("conflict"),
    REMOTE_UNCONFIGURED("remote_unconfigured");

    companion object {
        fun fromStorage(value: String?): SyncStatus? {
            val normalized = value?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.storageValue == normalized }
        }
    }
}

/** Lifecycle states persisted by the PDF upload queue. */
enum class UploadStatus(val storageValue: String) {
    WAITING("waiting"),
    UPLOADING("uploading"),
    SUCCESS("success"),
    FAILED("failed"),
    SESSION_EXPIRED("session_expired"),
    REMOTE_UNCONFIGURED("remote_unconfigured");

    companion object {
        fun fromStorage(value: String?): UploadStatus? {
            val normalized = value?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.storageValue == normalized }
        }
    }
}
