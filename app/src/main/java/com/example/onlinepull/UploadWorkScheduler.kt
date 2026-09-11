package com.example.onlinepull

import android.content.Context
import androidx.work.WorkManager

/** Retained only to retire persisted jobs created by older versions. No upload is scheduled. */
object UploadWorkScheduler {
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag("tenken-upload-retry")
    }
    fun cancelRetry(context: Context, localProjectId: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork("tenken-upload-retry-" + localProjectId)
    }
}

/** Keep this class name so a previously persisted Worker safely completes after an upgrade. */
class RemoteUploadWorker(appContext: Context, workerParams: androidx.work.WorkerParameters) :
    androidx.work.CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = Result.success()
}
