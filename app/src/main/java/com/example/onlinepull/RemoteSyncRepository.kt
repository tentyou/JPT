package com.example.onlinepull

import android.content.Context
import androidx.room.withTransaction
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class SyncReport(
    val localProjectId: String, val companies: Int, val subjects: Int, val remoteRows: Int,
    val imported: Int, val updated: Int, val inactive: Int, val conflicts: Int, val syncedAt: Long
)

/** Read everything before a single transaction. Local evidence belongs to stable stock UIDs. */
class RemoteSyncRepository(
    private val context: Context,
    private val client: InventorySource,
    private val db: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val stockDao = db.stockItemDao()
    private val projectDao = db.projectDao()
    private val remoteDao = db.remoteSyncDao()
    private val stockRepository = StockRepository(stockDao, projectDao)

    suspend fun sync(project: RemoteProjectSummary, onProgress: (String) -> Unit = {}): SyncReport =
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
                try {
                    onProgress("读取公司列表…")
                    val companies = client.listCompanies(project.id)
                    onProgress("读取项目设置…")
                    val metadata = client.projectMetadata(project, companies)
                    val rows = mutableListOf<RemoteInventoryItem>()
                    var subjectCount = 0
                    companies.forEachIndexed { companyIndex, company ->
                        currentCoroutineContext().ensureActive()
                        onProgress("读取公司 ${companyIndex + 1}/${companies.size}：${company.name}")
                        val subjects = client.listAssetBasedSubjects(project.id, company.id)
                        subjectCount += subjects.size
                        subjects.forEachIndexed { index, subject ->
                            currentCoroutineContext().ensureActive()
                            onProgress("公司 ${companyIndex + 1}/${companies.size} · 科目 ${index + 1}/${subjects.size}：${subject.name}（已发现 ${rows.size} 条应盘）")
                            rows += client.listInventoryItems(project.id, company, subject)
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    onProgress("完整清单已读取，正在保存…")
                    commit(project, companies.size, subjectCount, rows, metadata)
                } catch (error: Exception) {
                    withContext(NonCancellable) {
                        // Failure markers do not change the last complete snapshot or local evidence.
                        remoteDao.findProjectByRemoteId(project.id)?.let { old ->
                            remoteDao.upsertProjectLink(old.copy(
                                syncState = if (error is McpAccessFailure) "unverified" else "failed",
                                lastSyncError = if (error is kotlinx.coroutines.CancellationException) "同步已取消，上次完整清单已保留" else safeError(error)
                            ))
                        }
                    }
                    throw error
                }
            }
        }

    private suspend fun commit(project: RemoteProjectSummary, companyCount: Int, subjectCount: Int, rows: List<RemoteInventoryItem>, metadata: RemoteProjectMetadata): SyncReport =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val oldLink = remoteDao.findProjectByRemoteId(project.id)
            val localProjectId = oldLink?.localProjectId ?: UUID.randomUUID().toString()
            val localProject = projectDao.getProjectById(localProjectId) ?: Project(id = localProjectId, name = project.name)
            val oldBindings = remoteDao.bindingsForProject(localProjectId).associateBy { it.stableKey }
            val keyCounts = rows.groupingBy { stableKey(project.id, it) }.eachCount()
            val bindings = mutableListOf<RemoteAssetBinding>()
            val pendingItems = mutableListOf<StockItem>()
            val retainedKeys = mutableSetOf<String>()
            var imported = 0
            var updated = 0
            var conflicts = 0
            rows.forEachIndexed { index, row ->
                val baseKey = stableKey(project.id, row)
                val duplicate = keyCounts.getValue(baseKey) > 1
                val noIdentity = row.rowId.isNullOrBlank() &&
                    (row.worksheetKey.isBlank() || (row.itemCode.isBlank() && row.itemName.isBlank()))
                val legacyCandidates = if (oldBindings[baseKey] == null) oldBindings.values.filter {
                    it.remoteRowId.isNullOrBlank() && it.companyId == row.companyId &&
                        SubjectFieldMap.normalize(it.subjectCode) == SubjectFieldMap.normalize(row.subjectCode) &&
                        it.assetCode == row.itemCode && it.assetName == row.itemName
                } else emptyList()
                // Legacy rows with no persistent ID require review, even when a name happens to match.
                val conflict = duplicate || noIdentity || legacyCandidates.isNotEmpty()
                val reason = when {
                    duplicate -> "远端资产 ID 重复，需人工核对"
                    noIdentity -> "缺少稳定资产身份，需人工核对"
                    legacyCandidates.isNotEmpty() -> "旧记录缺少远端 ID，无法可靠关联；旧照片和 PDF 已保留"
                    else -> null
                }
                legacyCandidates.forEach {
                    retainedKeys += it.stableKey
                    remoteDao.upsertBindings(listOf(it.copy(syncState = "conflict", conflictReason = reason)))
                }
                val key = if (conflict && oldBindings[baseKey] == null) "remote-conflict:" + sha256(baseKey + "|" + canonicalJson(row.raw)).take(40) else baseKey
                if (bindings.any { it.stableKey == key }) return@forEachIndexed
                val existing = oldBindings[key]
                val uid = existing?.stockUid ?: UUID.randomUUID().toString()
                val prior = stockDao.getItemByUid(uid)
                if (conflict && existing != null && prior != null) {
                    // An ambiguous remote ID cannot replace the last verified asset metadata.
                    pendingItems += prior
                    bindings += existing.copy(syncState = "conflict", conflictReason = reason)
                    conflicts++
                    return@forEachIndexed
                }
                val location = AssessmentSystemClient.cell(row.raw, "SZDDCK", "存放位置", "地点", "location", "WZ", "ZL")
                pendingItems += (prior ?: StockItem(uid = uid, name = row.itemName.ifBlank { "未命名资产" })).copy(
                    name = row.itemName.ifBlank { prior?.name ?: "未命名资产" },
                    category = row.subjectName,
                    location = location,
                    originalCode = row.itemCode,
                    originalRowJson = stockRepository.toJsonList(listOf(row.subjectName, row.itemCode, row.itemName, row.companyName, row.worksheetKey)),
                    projectId = localProjectId,
                    // A remote row enters the local checklist by default. Once present, keep the
                    // user's local inclusion choice across later read-only synchronizations.
                    shouldCheck = when {
                        conflict -> prior?.shouldCheck ?: false
                        existing?.active == true -> prior?.shouldCheck ?: true
                        else -> true
                    },
                    rowOrder = index + 1
                )
                if (prior == null) imported++ else updated++
                if (conflict) conflicts++
                bindings += RemoteAssetBinding(
                    stableKey = key, localProjectId = localProjectId, stockUid = uid,
                    remoteRowId = row.rowId, companyId = row.companyId, companyName = row.companyName,
                    subjectCode = row.subjectCode, subjectName = row.subjectName,
                    assetCode = row.itemCode, assetName = row.itemName, worksheetKey = row.worksheetKey,
                    inventoryIndexId = row.inventoryIndexId, remoteSnapshotJson = row.raw.toString(),
                    active = !conflict || existing?.active == true, syncState = if (conflict) "conflict" else "synced",
                    lastSyncedAt = now, conflictReason = reason
                )
            }
            val activeKeys = bindings.map { it.stableKey }.toSet() + retainedKeys
            val inactiveBindings = oldBindings.values.filter { it.stableKey !in activeKeys }
            // Update individually: projects can exceed SQLite's bind-parameter limit.
            inactiveBindings.forEach {
                remoteDao.upsertBindings(listOf(it.copy(active = false, syncState = "inactive", lastSyncedAt = now)))
                stockDao.updateShouldCheck(it.stockUid, false)
            }
            val updatedProject = if (localProject.metadataLocallyEdited) localProject else localProject.copy(
                baseDate = metadata.baseDate.ifBlank { localProject.baseDate },
                companyName = metadata.companyName.ifBlank { localProject.companyName },
                reportType = metadata.reportType.ifBlank { localProject.reportType }
            )
            projectDao.insertProject(updatedProject.copy(name = project.name))
            if (pendingItems.isNotEmpty()) stockDao.insertAll(pendingItems)
            if (bindings.isNotEmpty()) remoteDao.upsertBindings(bindings)
            remoteDao.upsertProjectLink(RemoteProjectLink(localProjectId, project.id, project.code, project.name, now, "synced", null))
            SyncReport(localProjectId, companyCount, subjectCount, rows.size, imported, updated,
                inactiveBindings.count { it.active }, conflicts, now)
        }

    suspend fun recordAccessibleProjects(projects: List<RemoteProjectSummary>) {
        val ids = projects.map { it.id }.toSet()
        db.withTransaction {
            remoteDao.allProjectLinks().filter { it.remoteProjectId !in ids }.forEach {
                remoteDao.upsertProjectLink(it.copy(syncState = "unverified", lastSyncError = "当前凭据无法访问此项目，保留上次清单，暂时无法核验"))
            }
        }
    }

    companion object {
        private val syncMutex = Mutex()
        internal fun stableKey(projectId: String, row: RemoteInventoryItem): String {
            val source = row.rowId?.takeIf { it.isNotBlank() }?.let { "row:$projectId:$it" }
                ?: listOf(projectId, row.companyId, row.subjectCode, row.worksheetKey, row.itemCode, row.itemName).joinToString("|")
            return "remote:" + sha256(source).take(40)
        }
        private fun canonicalJson(value: Any?): String = when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { JSONObject.quote(it) + ":" + canonicalJson(value.opt(it)) }
            is org.json.JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonicalJson(value.opt(it)) }
            is String -> JSONObject.quote(value)
            else -> value.toString()
        }
        private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        fun safeError(error: Exception): String = when (error) {
            is McpFailure -> error.message ?: "读取失败，上次清单已保留"
            is java.io.IOException -> "网络连接中断，请检查网络后重试；上次完整清单已保留"
            else -> "同步未完成，上次完整清单已保留，请重试"
        }
    }
}
