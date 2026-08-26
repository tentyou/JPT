package com.example.onlinepull

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.UploadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class UploadResult(val status: String, val message: String, val receipt: UploadReceipt? = null)

/** Durable PDF queue. A successful upload is recorded only after the server responds. */
class RemoteUploadRepository(
    private val context: Context,
    private val client: AssessmentSystemClient = AssessmentSystemClient()
) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.remoteSyncDao()

    fun tasks(localProjectId: String): Flow<List<UploadTask>> = dao.uploadTasks(localProjectId)

    suspend fun tasksOnce(localProjectId: String): List<UploadTask> = withContext(Dispatchers.IO) {
        dao.bindingsForProject(localProjectId).mapNotNull { binding -> dao.uploadTask(binding.stableKey) }
    }

    suspend fun enqueuePdf(localProjectId: String, stockUid: String): UploadTask? = withContext(Dispatchers.IO) {
        val binding = dao.bindingsForProject(localProjectId).firstOrNull { it.stockUid == stockUid } ?: return@withContext null
        val pdf = File(context.filesDir, "pdfs/$stockUid/照片.pdf")
        if (!pdf.exists()) return@withContext null
        val link = dao.findProjectByLocalId(localProjectId)
        val safe = listOf(link?.remoteProjectCode, binding.companyName, binding.subjectName, stockUid.take(12))
            .filterNot { it.isNullOrBlank() }.joinToString("_") { sanitize(it!!) }
        val task = UploadTask(
            stableKey = binding.stableKey,
            localProjectId = localProjectId,
            stockUid = stockUid,
            filePath = pdf.absolutePath,
            fileName = "Tenken_${safe.ifBlank { stockUid }}.pdf",
            status = if (binding.inventoryIndexId.isNullOrBlank()) "remote_unconfigured" else "waiting",
            lastError = if (binding.inventoryIndexId.isNullOrBlank()) "远端缺少盘点索引" else null
        )
        dao.upsertUploadTask(task)
        task
    }

    suspend fun uploadAll(localProjectId: String): List<UploadResult> = withContext(Dispatchers.IO) {
        tasksOnce(localProjectId)
            .filter { it.status == "waiting" || it.status == "failed" || it.status == "session_expired" }
            .map { upload(it.stableKey) }
    }

    suspend fun upload(stableKey: String): UploadResult = withContext(Dispatchers.IO) {
        val task = dao.uploadTask(stableKey) ?: return@withContext UploadResult("missing", "上传任务不存在")
        val binding = dao.bindingsForProject(task.localProjectId).firstOrNull { it.stableKey == stableKey }
            ?: return@withContext UploadResult("missing", "远端绑定不存在")
        val indexId = binding.inventoryIndexId
            ?: return@withContext fail(task, "remote_unconfigured", "远端缺少盘点索引，已阻止上传")
        if (!binding.active) return@withContext fail(task, "failed", "远端条目已失效，已阻止上传")
        if (binding.syncState == "conflict") return@withContext fail(task, "failed", "远端稳定键冲突，已阻止上传")
        val file = File(task.filePath)
        if (!file.exists()) return@withContext fail(task, "failed", "本地 PDF 不存在")
        val hash = sha256(file)
        val current = task.copy(status = "uploading", attempts = task.attempts + 1, sha256 = hash, lastError = null, updatedAt = System.currentTimeMillis())
        dao.upsertUploadTask(current)
        try {
            val remote = client.getInventoryBinding(indexId)
            if (remote?.fileId != null && task.sha256 != hash) {
                val remoteName = remote.fileName
                if (remoteName.isNullOrBlank() || remoteName != task.fileName) {
                    return@withContext fail(current, "failed", "远端已有非本 App 同名附件，无法安全替换")
                }
            }
            if (remote?.fileId != null && task.sha256 == hash) {
                val done = current.copy(status = "success", remoteFileId = remote.fileId, updatedAt = System.currentTimeMillis())
                dao.upsertUploadTask(done)
                return@withContext UploadResult("success", "PDF 内容未变化，已跳过重复上传", UploadReceipt(remote.fileId, task.fileName, hash))
            }
            val receipt = client.replaceInventoryPdf(remote ?: RemoteInventoryBinding(indexId), file, task.fileName, hash)
            dao.upsertUploadTask(current.copy(status = "success", remoteFileId = receipt.fileId, sha256 = hash, updatedAt = System.currentTimeMillis()))
            UploadResult("success", "上传成功", receipt)
        } catch (error: Exception) {
            fail(current, if (error.message?.contains("登录态") == true) "session_expired" else "failed", error.message ?: "上传失败")
        }
    }

    private suspend fun fail(task: UploadTask, status: String, message: String): UploadResult {
        dao.upsertUploadTask(task.copy(status = status, lastError = message, updatedAt = System.currentTimeMillis()))
        return UploadResult(status, message)
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").take(40)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
