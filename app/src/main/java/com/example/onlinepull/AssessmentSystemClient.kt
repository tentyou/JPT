package com.example.onlinepull

import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

data class RemoteProjectSummary(val id: String, val name: String, val code: String = "", val evaluationBaseDateMillis: String = "")
data class RemoteCompanySummary(val id: String, val name: String)
data class RemoteSubject(val remoteCode: String, val normalizedCode: String, val name: String, val hasInventoryColumn: Boolean = true) {
    val code: String get() = remoteCode
}
data class RemoteInventoryItem(
    val rowId: String?, val companyId: String, val companyName: String,
    val subjectCode: String, val subjectName: String, val worksheetKey: String,
    val inventoryIndexId: String?, val itemCode: String, val itemName: String,
    val shouldCheck: Boolean, val raw: JSONObject
)
class RemoteParseFailure(message: String) : McpFailure(message)
class MissingRequiredField(val field: String, message: String = "远端字段缺失：$field") : McpFailure(message)

/** One complete project snapshot is the only input the synchronizer commits. */
interface InventorySource {
    suspend fun listProjects(): List<RemoteProjectSummary>
    suspend fun projectMetadata(project: RemoteProjectSummary, companies: List<RemoteCompanySummary>): RemoteProjectMetadata = RemoteProjectMetadata()
    suspend fun listCompanies(projectId: String): List<RemoteCompanySummary>
    suspend fun listAssetBasedSubjects(projectId: String, companyId: String): List<RemoteSubject>
    suspend fun listInventoryItems(projectId: String, company: RemoteCompanySummary, subject: RemoteSubject): List<RemoteInventoryItem>
}

class AssessmentSystemClient(private val tools: McpTools) : InventorySource {
    override suspend fun listProjects(): List<RemoteProjectSummary> {
        val array = tools.callTool("get_my_projects", JSONObject()) as? JSONArray
            ?: throw RemoteParseFailure("项目列表格式不正确")
        val result = objects(array).map {
            RemoteProjectSummary(required(it, "id"), required(it, "projectName"), cell(it, "projectCode"), cell(it, "evaluationBaseDate"))
        }
        if (result.map { it.id }.distinct().size != result.size) throw RemoteParseFailure("项目列表存在重复 ID")
        return result
    }

    override suspend fun projectMetadata(project: RemoteProjectSummary, companies: List<RemoteCompanySummary>): RemoteProjectMetadata {
        val values = tools.callTool("get_project_context", args(project.id)) as? JSONArray
            ?: throw RemoteParseFailure("项目设置格式不正确")
        return RemoteProjectMetadata.parse(values, project.evaluationBaseDateMillis, companies.map { it.name })
    }
    override suspend fun listCompanies(projectId: String): List<RemoteCompanySummary> {
        val array = tools.callTool("get_project_companies", args(projectId)) as? JSONArray
            ?: throw RemoteParseFailure("公司列表格式不正确")
        val result = objects(array).map { RemoteCompanySummary(required(it, "id"), required(it, "name")) }
        if (result.isEmpty()) throw RemoteParseFailure("公司列表为空，无法核验原盘点清单")
        if (result.map { it.id }.distinct().size != result.size) throw RemoteParseFailure("公司列表存在重复 ID")
        return result
    }

    override suspend fun listAssetBasedSubjects(projectId: String, companyId: String): List<RemoteSubject> {
        val result = tools.callTool("get_company_asset_based_approach_subjects", args(projectId, companyId)) as? JSONObject
            ?: throw RemoteParseFailure("科目列表格式不正确")
        checkScope(result, projectId, companyId)
        val all = objects(result.optJSONArray("subjects") ?: throw MissingRequiredField("subjects"))
        if (!result.has("total") || result.optInt("total", -1) != all.size || all.isEmpty()) {
            throw RemoteParseFailure("科目列表不完整，原盘点清单未改动")
        }
        val subjects = all.map {
            val code = required(it, "subjectCode")
            RemoteSubject(code, SubjectFieldMap.normalize(code), required(it, "standardSubjectName"))
        }.filter { it.normalizedCode.matches(Regex("[34]-.*")) }
        if (subjects.map { it.normalizedCode }.distinct().size != subjects.size) throw RemoteParseFailure("科目代码重复，无法完整核验")
        return subjects
    }

    override suspend fun listInventoryItems(projectId: String, company: RemoteCompanySummary, subject: RemoteSubject): List<RemoteInventoryItem> {
        val all = mutableListOf<JSONObject>()
        val previousPages = mutableSetOf<String>()
        var expectedTotal: Int? = null
        var pageNumber = 1
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val data = tools.callTool("get_asset_based_approach_draft_data", args(projectId, company.id)
                .put("subjectName", subject.name).put("pageNum", pageNumber).put("limit", 500)) as? JSONObject
                ?: throw RemoteParseFailure("底稿格式不正确")
            checkScope(data, projectId, company.id)
            if (data.optString("subjectCode") != subject.remoteCode) throw RemoteParseFailure("底稿科目与请求不一致")
            val page = data.optJSONObject("pageInfo") ?: throw MissingRequiredField("pageInfo")
            val rows = objects(data.optJSONArray("data") ?: throw MissingRequiredField("data"))
            val total = page.optInt("total", -1)
            if (total < 0 || page.optInt("pageNum", -1) != pageNumber || !page.has("next")) throw RemoteParseFailure("底稿分页信息不完整")
            if (expectedTotal != null && total != expectedTotal) throw RemoteParseFailure("同步期间底稿发生变化，请重新同步")
            expectedTotal = total
            val fingerprint = rows.joinToString("|") { cell(it, "id", "rowId", "_id", "bizId").ifBlank { it.toString() } }
            if (rows.isNotEmpty() && !previousPages.add(fingerprint)) throw RemoteParseFailure("远端重复返回同一页，原盘点清单未改动")
            all.addAll(rows)
            if (all.size > total) throw RemoteParseFailure("底稿条数超过分页总数")
            if (!page.optBoolean("next") && all.size == total) break
            if (rows.isEmpty() || all.size == total || pageNumber >= 2000) throw RemoteParseFailure("底稿未完整读取，请重试")
            pageNumber++
        }
        return all.mapNotNull { row ->
            val rowProject = cell(row, "projectId")
            val rowCompany = cell(row, "companyId")
            if ((rowProject.isNotBlank() && rowProject != projectId) || (rowCompany.isNotBlank() && rowCompany != company.id)) {
                throw RemoteParseFailure("底稿行归属与请求不一致")
            }
            if (!SubjectFieldMap.isCheckTrue(cell(row, "SFPD", "是否盘点"))) return@mapNotNull null
            val def = SubjectFieldMap.byCode(subject.normalizedCode)
            val code = cell(row, *listOfNotNull(def?.itemCode, "SBBH", "WLBH", "CLPH", "QZBH", "资产编号", "编号").distinct().toTypedArray())
            val name = cell(row, *listOfNotNull(def?.nameCode, "SBMC", "CLMC", "JZWMC", "DCLZCMC", "XMMC", "MC", "资产名称", "名称").distinct().toTypedArray()).ifBlank { code }
            RemoteInventoryItem(
                cell(row, "id", "rowId", "_id", "bizId").ifBlank { null },
                company.id, company.name, subject.remoteCode, def?.name ?: subject.name,
                cell(row, "worksheetKey", "sheetKey", "sheetName", "tableName"),
                cell(row, "inventoryIndexId", "pdIndexId", "盘点索引ID").ifBlank { null },
                code, name, true, row
            )
        }
    }

    private fun args(projectId: String, companyId: String? = null) = JSONObject().put("projectId", numericId(projectId)).apply {
        companyId?.let { put("companyId", numericId(it)) }
    }

    private fun numericId(value: String): Long {
        val number = value.toLongOrNull() ?: throw RemoteParseFailure("项目或公司 ID 格式不正确")
        if (number !in 1..9007199254740991L) throw RemoteParseFailure("当前 MCP 不支持此范围的项目或公司 ID")
        return number
    }

    private fun checkScope(result: JSONObject, projectId: String, companyId: String) {
        if (result.optString("projectId") != projectId || result.optString("companyId") != companyId || !result.optBoolean("success")) {
            throw RemoteParseFailure("远端返回的项目或公司与请求不一致")
        }
    }

    private fun objects(array: JSONArray) = (0 until array.length()).map {
        array.optJSONObject(it) ?: throw RemoteParseFailure("远端列表中出现非对象记录")
    }
    private fun required(row: JSONObject, key: String) = cell(row, key).ifBlank { throw MissingRequiredField(key) }

    companion object {
        internal fun cell(row: JSONObject, vararg keys: String): String =
            keys.firstNotNullOfOrNull { decode(row.opt(it)).takeIf(String::isNotBlank) }.orEmpty()

        private fun decode(value: Any?): String = when (value) {
            null, JSONObject.NULL -> ""
            is JSONObject -> cell(value, "value", "text", "label", "displayValue", "name")
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { decode(value.opt(it)).takeIf(String::isNotBlank) }.orEmpty()
            else -> value.toString().trim().takeUnless { it == "null" }.orEmpty()
        }
    }
}
