package com.example.onlinepull

import android.content.Context
import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.Project
import com.example.data.RemoteAssetBinding
import com.example.data.RemoteProjectLink
import com.example.data.StockItem
import com.example.data.StockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class SyncReport(
    val localProjectId: String,
    val companies: Int,
    val subjects: Int,
    val remoteRows: Int,
    val imported: Int,
    val updated: Int,
    val inactive: Int,
    val conflicts: Int,
    val remoteUnconfigured: Int,
    val syncedAt: Long
)

/** Coordinates automatic all-company asset-based synchronisation and preserves local media. */
class RemoteSyncRepository(
    private val context: Context,
    private val client: AssessmentSystemClient = AssessmentSystemClient()
) {
    private val db = AppDatabase.getDatabase(context)
    private val stockDao = db.stockItemDao()
    private val projectDao = db.projectDao()
    private val remoteDao = db.remoteSyncDao()
    private val stockRepository = StockRepository(stockDao, projectDao)

    suspend fun sync(project: RemoteProjectSummary, onProgress: (String) -> Unit = {}): SyncReport =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val oldLink = remoteDao.findProjectByRemoteId(project.id)
            val localProjectId = oldLink?.localProjectId ?: UUID.randomUUID().toString()
            val oldProject = projectDao.getProjectById(localProjectId)
            val localProject = oldProject ?: Project(id = localProjectId, name = project.name)
            projectDao.insertProject(localProject.copy(name = project.name))
            remoteDao.upsertProjectLink(
                RemoteProjectLink(localProjectId, project.id, project.code, project.name, now, "syncing", null)
            )

            try {
                onProgress("读取公司列表…")
                val companies = client.listCompanies(project.id)
                require(companies.isNotEmpty()) { "远端项目没有可访问的公司" }
                onProgress("读取资产基础法科目…")
                val subjects = client.listAssetBasedSubjects(project.id, companies.map { it.id })
                val rows = mutableListOf<RemoteInventoryItem>()
                subjects.forEachIndexed { index, subject ->
                    companies.forEach { company ->
                        onProgress("读取 ${index + 1}/${subjects.size}：${company.name} · ${subject.name}")
                        rows += client.listInventoryItems(project.id, company, subject)
                    }
                }
                val oldBindings = remoteDao.bindingsForProject(localProjectId).associateBy { it.stableKey }
                val seen = mutableMapOf<String, Int>()
                val bindings = mutableListOf<RemoteAssetBinding>()
                var imported = 0
                var updated = 0
                var conflicts = 0
                var unconfigured = 0

                rows.forEachIndexed { rowIndex, row ->
                    val baseKey = stableKey(project.id, row)
                    val duplicateIndex = seen.merge(baseKey, 1, Int::plus) ?: 1
                    val key = if (duplicateIndex == 1) baseKey else "$baseKey#duplicate$duplicateIndex"
                    val existing = oldBindings[baseKey] ?: oldBindings[key]
                    val uid = existing?.stockUid ?: UUID.randomUUID().toString()
                    val prior = stockDao.getItemByUid(uid)
                    val values = listOf(row.subjectName, row.itemCode, row.itemName, row.companyName, row.worksheetKey)
                    val item = (prior ?: StockItem(uid = uid, name = row.itemName.ifBlank { "未命名${rowIndex + 1}" }))
                        .copy(
                            name = row.itemName.ifBlank { prior?.name ?: "未命名${rowIndex + 1}" },
                            category = row.subjectName,
                            location = rowValue(row.raw, "存放位置", "地点", "location", "WZ"),
                            originalCode = row.itemCode,
                            originalRowJson = stockRepository.toJsonList(values),
                            projectId = localProjectId,
                            shouldCheck = true,
                            rowOrder = rowIndex + 1
                        )
                    stockDao.insertItem(item)
                    if (prior == null) imported++ else updated++
                    val conflict = duplicateIndex > 1
                    if (conflict) conflicts++
                    val missingIndex = row.inventoryIndexId.isNullOrBlank()
                    if (missingIndex) unconfigured++
                    bindings += RemoteAssetBinding(
                        stableKey = key,
                        localProjectId = localProjectId,
                        stockUid = uid,
                        remoteRowId = row.rowId,
                        companyId = row.companyId,
                        companyName = row.companyName,
                        subjectCode = row.subjectCode,
                        subjectName = row.subjectName,
                        worksheetKey = row.worksheetKey,
                        inventoryIndexId = row.inventoryIndexId,
                        remoteSnapshotJson = row.raw.toString(),
                        active = true,
                        syncState = when {
                            conflict -> "conflict"
                            missingIndex -> "remote_unconfigured"
                            else -> "synced"
                        },
                        lastSyncedAt = now,
                        conflictReason = when {
                            conflict -> "同一远端稳定键出现多行，需人工核对"
                            missingIndex -> "远端缺少盘点索引"
                            else -> null
                        }
                    )
                }

                val activeKeys = bindings.map { it.stableKey }
                if (activeKeys.isEmpty()) remoteDao.markAllInactive(localProjectId, now)
                else remoteDao.markMissingInactive(localProjectId, activeKeys, now)
                if (bindings.isNotEmpty()) remoteDao.upsertBindings(bindings)
                remoteDao.upsertProjectLink(
                    RemoteProjectLink(localProjectId, project.id, project.code, project.name, now, "synced", null)
                )
                SyncReport(localProjectId, companies.size, subjects.size, rows.size, imported, updated,
                    oldBindings.keys.count { it !in activeKeys }, conflicts, unconfigured, now)
            } catch (error: Exception) {
                remoteDao.upsertProjectLink(
                    RemoteProjectLink(localProjectId, project.id, project.code, project.name, now, "failed", error.message)
                )
                throw error
            }
        }

    suspend fun remoteLink(localProjectId: String): RemoteProjectLink? = remoteDao.findProjectByLocalId(localProjectId)

    private fun stableKey(projectId: String, row: RemoteInventoryItem): String {
        val source = row.rowId?.takeIf { it.isNotBlank() }?.let { "row:$projectId:$it" }
            ?: listOf(projectId, row.companyId, row.subjectCode, row.worksheetKey, row.itemCode, row.itemName)
                .joinToString("|")
        return "remote:" + sha256(source).take(40)
    }

    private fun rowValue(row: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = row.opt(key)
            if (value != null && value !is JSONObject && value.toString().isNotBlank()) return value.toString().trim()
        }
        return ""
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
