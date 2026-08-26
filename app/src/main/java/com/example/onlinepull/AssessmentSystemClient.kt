package com.example.onlinepull

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

data class RemoteProjectSummary(val id: String, val name: String, val code: String = "")
data class RemoteCompanySummary(val id: String, val name: String)
data class RemoteSubject(val code: String, val name: String, val hasInventoryColumn: Boolean)
data class RemoteInventoryItem(
    val rowId: String?,
    val companyId: String,
    val companyName: String,
    val subjectCode: String,
    val subjectName: String,
    val worksheetKey: String,
    val inventoryIndexId: String?,
    val itemCode: String,
    val itemName: String,
    val shouldCheck: Boolean,
    val raw: JSONObject
)
data class RemoteInventoryBinding(val inventoryIndexId: String, val fileId: String? = null, val fileName: String? = null)
data class UploadReceipt(val fileId: String?, val fileName: String, val sha256: String)

/** Stable facade used by ViewModels/repositories; UI never calls ZhrdcApiService directly. */
class AssessmentSystemClient(
    private val service: ZhrdcApiService = ZhrdcApiService()
) {
    suspend fun currentSession(): String? = service.currentUserName()

    suspend fun listProjects(): List<RemoteProjectSummary> = service.discoverProjects().map {
        RemoteProjectSummary(it.id, it.name, it.code)
    }.also {
        if (it.isEmpty()) throw ZhrdcApiException("未找到可访问的评估项目，请确认已登录并具有项目权限")
    }

    suspend fun listCompanies(projectId: String): List<RemoteCompanySummary> =
        service.listCompanies(projectId).map { RemoteCompanySummary(it.id, it.name) }

    suspend fun listAssetBasedSubjects(projectId: String, companyIds: List<String>): List<RemoteSubject> {
        val result = mutableListOf<RemoteSubject>()
        fun walk(nodes: List<SubjectNode>) {
            nodes.forEach { node ->
                val def = SubjectFieldMap.byCode(node.code)
                if (def != null && def.hasCheckColumn) {
                    result += RemoteSubject(SubjectFieldMap.normalize(node.code), def.name, true)
                }
                walk(node.children)
            }
        }
        walk(service.subjectTree(projectId, companyIds))
        return result.distinctBy { it.code }
    }

    suspend fun listInventoryItems(
        projectId: String,
        company: RemoteCompanySummary,
        subject: RemoteSubject
    ): List<RemoteInventoryItem> {
        val def = SubjectFieldMap.byCode(subject.code)
            ?: throw ZhrdcApiException("未识别的资产基础法科目：${subject.code}")
        val rows = service.fetchAllDraftData(projectId, subject.code, listOf(company.id), 1)
        return rows.mapIndexedNotNull { index, row ->
            val shouldCheck = rowValue(row, "SFPD", "是否盘点").let(SubjectFieldMap::isCheckTrue)
            if (!shouldCheck) return@mapIndexedNotNull null
            val itemCode = def.itemCode?.let { rowValue(row, it) }.orEmpty()
            val itemName = def.nameCode?.let { rowValue(row, it) }.orEmpty().ifBlank { itemCode }
            val rowId = rowValue(row, "id", "rowId", "_id", "bizId").ifBlank { null }
            val worksheet = rowValue(row, "worksheetKey", "sheetKey", "sheetName", "tableName")
            val indexId = rowValue(row, "inventoryIndexId", "pdIndexId", "盘点索引ID").ifBlank { null }
            RemoteInventoryItem(rowId, company.id, company.name, subject.code, subject.name, worksheet, indexId, itemCode, itemName, true, row)
        }
    }

    suspend fun getInventoryBinding(item: RemoteInventoryItem): RemoteInventoryBinding? {
        val indexId = item.inventoryIndexId ?: return null
        // The attachment endpoint is intentionally called only after the row has a stable index ID.
        val json = service.rawGet("/ty/api/attach/upload/or/update?inventoryIndexId=${enc(indexId)}")
        val data = json.optJSONObject("data") ?: json
        val fileId = first(data, "fileId", "attachId", "id")
        val fileName = first(data, "fileName", "name")
        return RemoteInventoryBinding(indexId, fileId.ifBlank { null }, fileName.ifBlank { null })
    }

    suspend fun getInventoryBinding(inventoryIndexId: String): RemoteInventoryBinding? {
        val json = service.rawGet("/ty/api/attach/upload/or/update?inventoryIndexId=${enc(inventoryIndexId)}")
        val data = json.optJSONObject("data") ?: json
        val fileId = first(data, "fileId", "attachId", "id")
        val fileName = first(data, "fileName", "name")
        return RemoteInventoryBinding(inventoryIndexId, fileId.ifBlank { null }, fileName.ifBlank { null })
    }
    suspend fun replaceInventoryPdf(
        binding: RemoteInventoryBinding,
        pdf: File,
        fileName: String,
        sha256: String
    ): UploadReceipt = withContext(Dispatchers.IO) {
        require(pdf.exists() && pdf.isFile) { "PDF 文件不存在" }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("inventoryIndexId", binding.inventoryIndexId)
            .addFormDataPart("file", fileName, pdf.asRequestBody("application/pdf".toMediaType()))
            .build()
        val url = "https://excel.zhrdc.net/ty/api/attach/upload/or/update"
        requireAllowed(url)
        val request = Request.Builder().url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("x-request-key", UUID.randomUUID().toString())
            .apply { CookieManager.getInstance().getCookie(url)?.let { header("Cookie", it) } }
            .post(body).build()
        val json = client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) { clearCookies(); throw ZhrdcApiException("登录态失效，请重新登录") }
            if (!response.isSuccessful) throw ZhrdcApiException("上传失败（HTTP ${response.code}）")
            try { JSONObject(text) } catch (_: Exception) { throw ZhrdcApiException("上传响应不是有效 JSON") }
        }
        val data = json.optJSONObject("data") ?: json
        UploadReceipt(first(data, "fileId", "attachId", "id").ifBlank { null }, fileName, sha256)
    }

    private fun clearCookies() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Throwable) {
        }
    }

    suspend fun logout() {
        clearCookies()
    }

    private fun rowValue(row: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = row.opt(key)
            if (value != null && value !is JSONObject && value.toString().trim().isNotEmpty()) return value.toString().trim()
        }
        return ""
    }

    private fun first(o: JSONObject, vararg keys: String): String = rowValue(o, *keys)
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun requireAllowed(url: String) {
        val uri = android.net.Uri.parse(url)
        require(uri.scheme == "https" && (uri.host == "zhrdc.net" || uri.host?.endsWith(".zhrdc.net") == true)) {
            "拒绝访问非评估系统域名"
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
    }
}
