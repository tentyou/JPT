package com.example.onlinepull

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 线上作业系统（ty.zhrdc.net / excel.zhrdc.net）REST API 客户端。
 *
 * 认证方案：登录态 Cookie 由内嵌 WebView（同一 CookieManager）写入，
 * 本服务通过 OkHttp CookieJar 读取 CookieManager 自动携带（含 HttpOnly Cookie），
 * 因此不依赖具体 Cookie 名称。
 */
data class CompanyInfo(val id: String, val name: String, val raw: JSONObject)

data class ProjectInfo(val id: String, val name: String, val code: String = "")

data class SubjectNode(
    val code: String,
    val name: String,
    val children: List<SubjectNode> = emptyList(),
    val dataAvailable: Int = 1 // 0=无数据（默认1=未知/有）
) {
    val isLeaf: Boolean get() = children.isEmpty()
}

data class DraftPage(val rows: List<JSONObject>, val total: Int, val hasNext: Boolean)

class ZhrdcApiException(message: String) : Exception(message)

class ZhrdcApiService {

    val baseUrl: String = "https://excel.zhrdc.net"

    companion object {
        private const val TAG = "ZhrdcApi"

        private fun log(msg: String) {
            try {
                android.util.Log.d(TAG, msg)
            } catch (_: Throwable) {
            }
        }
    }

    // ---------------------------------------------------------------- Cookie

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            try {
                val cm = CookieManager.getInstance()
                for (c in cookies) {
                    cm.setCookie(url.toString(), c.toString())
                }
                cm.flush()
            } catch (_: Throwable) {
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return try {
                val header = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
                parseCookies(header, url)
            } catch (_: Throwable) {
                emptyList()
            }
        }

        private fun parseCookies(header: String, url: HttpUrl): List<Cookie> {
            val out = mutableListOf<Cookie>()
            for (part in header.split(";")) {
                val t = part.trim()
                if (t.isEmpty() || t.startsWith("$")) continue // 跳过 $Path/$Domain 等标记
                val idx = t.indexOf('=')
                if (idx <= 0) continue
                val name = t.substring(0, idx).trim()
                val value = t.substring(idx + 1).trim()
                val cookie = Cookie.parse(url, "$name=$value")
                if (cookie != null) out.add(cookie)
            }
            return out
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // ---------------------------------------------------------------- 基础工具

    private fun buildRequest(path: String, method: String, body: String?): Request {
        val url = if (path.startsWith("http")) path else baseUrl + path
        requireAllowedUrl(url)
        val builder = Request.Builder()
            .url(url)
            .header("x-request-key", UUID.randomUUID().toString())
            .header("Accept", "application/json, text/plain, */*")
        when (method) {
            "POST" -> {
                builder.header("Content-Type", "application/json")
                    .post((body ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()))
            }
            else -> builder.get()
        }
        return builder.build()
    }

    private fun clearCookies() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Throwable) {
        }
    }

    private fun requireAllowedUrl(url: String) {
        val uri = android.net.Uri.parse(url)
        require(uri.scheme == "https" && (uri.host == "zhrdc.net" || uri.host?.endsWith(".zhrdc.net") == true)) {
            "拒绝访问非评估系统域名"
        }
    }

    /** 执行请求并解析为 JSONObject；网络/协议异常向上抛出 */
    private suspend fun execute(request: Request): JSONObject = withContext(Dispatchers.IO) {
        log(">> " + request.method + " " + request.url.encodedPath)
        val startedAt = System.nanoTime()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            log("<< HTTP " + resp.code + " ms=" + ((System.nanoTime() - startedAt) / 1_000_000L) + " len=" + text.length)
            val json = try {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (e: Exception) {
                throw ZhrdcApiException("响应不是有效 JSON（HTTP " + resp.code + "）")
            }
            val code = json.optInt("code", 200)
            if (resp.code == 401 || resp.code == 403 || code == 401 || code == 403) clearCookies()
            if (code == 401 || code == 403) throw ZhrdcApiException("登录态失效，请重新登录")
            if (code != 200 && json.has("code")) {
                throw ZhrdcApiException("业务错误 code=$code msg=${json.optString("msg", "")}")
            }
            if (resp.code == 401 || resp.code == 403) {
                throw ZhrdcApiException("登录态失效（HTTP ${resp.code}），请重新登录")
            }
            json
        }
    }

    private fun pickString(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.opt(k)
            if (v != null && v !is JSONObject && v !is JSONArray) {
                val s = v.toString().trim()
                if (s.isNotEmpty() && s != "null") return s
            }
        }
        return ""
    }

    private fun findArray(j: JSONObject, vararg keys: String): JSONArray? {
        for (k in keys) {
            val a = j.optJSONArray(k)
            if (a != null) return a
        }
        return null
    }

    // ---------------------------------------------------------------- 业务接口

    /** 当前登录用户（用于校验登录态），未登录返回 null */
    suspend fun currentUserName(): String? = withContext(Dispatchers.IO) {
        try {
            val j = execute(buildRequest("/ty/api/users/currentUserInfo", "GET", null))
            val data = j.optJSONObject("data") ?: j
            val name = pickString(data, "userName", "realName", "nickName", "name", "userRealName")
            name.ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- 项目列表

    /** 获取当前用户可访问的项目列表。接口路径固定，分页参数兼容后端版本差异。 */
    suspend fun discoverProjects(): List<ProjectInfo> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        val attempts = listOf("POST" to "/ty/api/projects")
        for ((method, path) in attempts) {
            try {
                val list = fetchProjectPages(method, path)
                if (list.isNotEmpty()) {
                    log("discoverProjects: HIT ${method} ${path} -> ${list.size} projects")
                    return@withContext list
                }
                lastError = ZhrdcApiException("项目接口返回空列表或缺少项目 ID/名称")
            } catch (e: Exception) {
                lastError = e
                log("discoverProjects request failed")
            }
        }
        throw lastError ?: ZhrdcApiException("项目接口不可用")
    }

    /** 分页参数候选：依次尝试，取最先返回数据的组合并翻页 */
    private val pageParamFormats = listOf(
        "pageNum=%d&pageSize=%d",
        "current=%d&size=%d",
        "page=%d&limit=%d"
    )

    private suspend fun fetchProjectPages(method: String, path: String): List<ProjectInfo> {
        val all = LinkedHashMap<String, ProjectInfo>()
        val sep = if (path.contains("?")) "&" else "?"
        for (fmt in pageParamFormats) {
            var page = 1
            while (page <= 200) {
                val full = path + sep + String.format(fmt, page, 200)
                val j = execute(buildRequest(full, method, if (method == "POST") "{}" else null))
                val extracted = extractProjects(j)
                if (extracted.isEmpty()) break
                for (p in extracted) {
                    if (!all.containsKey(p.id)) all[p.id] = p
                }
                val total = totalCountFrom(j)
                if (total > 0 && all.size >= total) break
                if (extracted.size < 200) break // 单页未满，视为最后一页
                page++
            }
            if (all.isNotEmpty()) break // 本组分页参数已命中
        }
        return all.values.toList()
    }

    private fun totalCountFrom(j: JSONObject): Int {
        val pageInfo = j.optJSONObject("pageInfo")
        if (pageInfo != null) {
            val t = pageInfo.optInt("total", -1)
            if (t >= 0) return t
        }
        for (key in listOf("total", "totalCount", "totalRows", "count")) {
            val t = j.optInt(key, -1)
            if (t >= 0) return t
        }
        return -1
    }

    private fun isIdLike(v: Any?): Boolean {
        if (v == null || v is JSONObject || v is JSONArray) return false
        val s = v.toString().trim()
        return s.length in 6..20 && s.all { it.isDigit() }
    }

    /** 递归提取「项目ID + 项目名称」组合。
     *  真实项目对象结构（POST /ty/api/projects 返回）：
     *  { id, projectName, projectCode, partnerList:[{fullName:人名}], managerList:[...], ... }
     *  规则：只认同时含 id 与 projectName/projectCode 的对象；跳过人员子对象。 */
    private fun extractProjects(j: JSONObject): List<ProjectInfo> {
        val out = LinkedHashMap<String, ProjectInfo>()

        fun walk(v: Any?) {
            when (v) {
                is JSONObject -> {
                    val hasId = v.has("id") || v.has("projectId")
                    val hasProjectName = v.has("projectName")
                    val hasProjectCode = v.has("projectCode")
                    if (hasId && (hasProjectName || hasProjectCode)) {
                        val id = v.optString("id", "").ifBlank { v.optString("projectId", "") }
                        val name = v.optString("projectName", "").ifBlank {
                            v.optString("name", "").ifBlank { "项目$id" }
                        }
                        val code = v.optString("projectCode", "")
                        if (isIdLike(id)) {
                            out.putIfAbsent(id, ProjectInfo(id, name, code))
                        }
                    } else {
                        // 仅递归子对象（人员列表等不满足项目结构，不会被误收）
                        val keys = v.keys()
                        while (keys.hasNext()) walk(v.opt(keys.next()))
                    }
                }
                is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i))
            }
        }
        walk(j)
        return out.values.toList()
    }

    /**
     * 项目下的公司列表。POST /ty/api/company/list body {"projectId": "..."}
     * 由于公司列表响应字段名未完全确认，采用递归防御式抽取：
     * 收集所有同时具备「id 类字段」与「名称类字段」的对象。
     */
    suspend fun listCompanies(projectId: String): List<CompanyInfo> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("projectId", projectId).toString()
            val j = execute(buildRequest("/ty/api/company/list", "POST", body))
            val out = mutableListOf<CompanyInfo>()
            val seen = mutableSetOf<String>()

            fun walk(v: Any?) {
                when (v) {
                    is JSONObject -> {
                        val id = pickString(v, "companyId", "company_id", "id", "companyCode", "code")
                        val name = pickString(
                            v, "companyName", "companyShortName", "fullName", "name", "label", "company"
                        )
                        if (id.isNotEmpty() && name.isNotEmpty() && seen.add(id)) {
                            out.add(CompanyInfo(id, name, v))
                        }
                        val keys = v.keys()
                        while (keys.hasNext()) walk(v.opt(keys.next()))
                    }
                    is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i))
                }
            }
            walk(j)
            log("listCompanies -> ${out.size} companies")
            out
        } catch (e: Exception) {
            log("listCompanies FAILED")
            throw e
        }
    }

    /** 科目树。GET /ty/api/assignment_draft/subject/tree */
    suspend fun subjectTree(projectId: String, companyIds: List<String>): List<SubjectNode> =
        withContext(Dispatchers.IO) {
            val ids = companyIds.joinToString(",")
            val path = "/ty/api/assignment_draft/subject/tree?projectId=$projectId&companyIdList=$ids&skipMiddleLevel=0"
            val j = execute(buildRequest(path, "GET", null))
            var arr = findArray(j, "data", "tree", "list", "rows", "result", "dataList")

            // 兜底：若顶层没有数组，则递归寻找首个「元素含 subjectCode/code」的数组
            if (arr == null) {
                fun deepFind(v: Any?): JSONArray? {
                    when (v) {
                        is JSONArray -> {
                            if (v.length() > 0) {
                                val first = v.opt(0)
                                if (first is JSONObject &&
                                    (first.has("subjectCode") || first.has("code") ||
                                            first.has("children") || first.has("childNodeList"))
                                ) return v
                            }
                            for (i in 0 until v.length()) {
                                deepFind(v.opt(i))?.let { return it }
                            }
                        }
                        is JSONObject -> {
                            val keys = v.keys()
                            while (keys.hasNext()) {
                                deepFind(v.opt(keys.next()))?.let { return it }
                            }
                        }
                    }
                    return null
                }
                arr = deepFind(j)
            }
            if (arr == null) return@withContext emptyList()

            fun parseNode(v: Any?, filterEmpty: Boolean): SubjectNode? {
                val o = v as? JSONObject ?: return null
                val code = pickString(o, "subjectCode", "code", "id")
                val name = pickString(o, "subjectName", "name", "label", "title")
                val dataAvail = if (o.has("dataAvailable")) o.optInt("dataAvailable") else 1
                val children = mutableListOf<SubjectNode>()
                for (key in listOf("childNodeList", "children", "childList", "childrenList", "subList", "nodes")) {
                    val ca = o.optJSONArray(key) ?: continue
                    for (i in 0 until ca.length()) {
                        parseNode(ca.opt(i), filterEmpty)?.let { children.add(it) }
                    }
                    if (children.isNotEmpty()) break
                }
                if (filterEmpty && children.isEmpty() && dataAvail == 0) {
                    // 叶子科目且明确标记无数据 → 不显示
                    return null
                }
                if (code.isNotEmpty()) {
                    return SubjectNode(code, name.ifBlank { code }, children, dataAvail)
                }
                return if (children.isEmpty()) null else SubjectNode(code, name, children, dataAvail)
            }

            fun parseList(filterEmpty: Boolean): List<SubjectNode> {
                val list = mutableListOf<SubjectNode>()
                for (i in 0 until arr.length()) {
                    parseNode(arr.opt(i), filterEmpty)?.let { list.add(it) }
                }
                return list
            }

            fun hasLeaf(nodes: List<SubjectNode>): Boolean {
                for (n in nodes) {
                    if (n.children.isEmpty()) return true
                    if (hasLeaf(n.children)) return true
                }
                return false
            }

            val filtered = parseList(filterEmpty = true)
            if (hasLeaf(filtered)) {
                log("subjectTree -> ${filtered.size} roots (filtered empty-data subjects)")
                filtered
            } else {
                // 回退：dataAvailable 语义不明导致全空时，保留原始树
                val fallback = parseList(filterEmpty = false)
                log("subjectTree -> ${fallback.size} roots (fallback, no filter)")
                fallback
            }
        }

    /**
     * 科目明细数据（分页）。GET /ty/api/assignment_draft/draft/detail/data
     * 固定 valuationType=1（资产基础法）。
     */
    suspend fun draftData(
        projectId: String,
        subjectCode: String,
        companyIds: List<String>,
        draftDataType: Int = 1,
        pageNum: Int = 1,
        limit: Int = 5000
    ): DraftPage = withContext(Dispatchers.IO) {
        val ids = companyIds.joinToString(",")
        val sc = URLEncoder.encode(subjectCode, "UTF-8")
        val path = "/ty/api/assignment_draft/draft/detail/data" +
                "?projectId=$projectId&subjectCode=$sc&companyIdList=$ids" +
                "&valuationType=1&draftDataType=$draftDataType&limit=$limit&pageNum=$pageNum"
        val j = execute(buildRequest(path, "GET", null))
        val arr = findArray(j, "data", "list", "rows", "result") ?: JSONArray()
        val pageInfo = j.optJSONObject("pageInfo")
        val total = pageInfo?.optInt("total", arr.length()) ?: arr.length()
        val next = pageInfo?.optBoolean("next", false) ?: false
        val rows = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o != null) rows.add(o)
        }
        DraftPage(rows, total, next)
    }

    /** 拉取某科目全部明细（自动翻页）。 */
    suspend fun fetchAllDraftData(
        projectId: String,
        subjectCode: String,
        companyIds: List<String>,
        draftDataType: Int = 1,
        onProgress: ((loaded: Int, total: Int) -> Unit)? = null
    ): List<JSONObject> = withContext(Dispatchers.IO) {
        val all = mutableListOf<JSONObject>()
        var page = 1
        var knownTotal = -1
        while (true) {
            val p = draftData(projectId, subjectCode, companyIds, draftDataType, page, 5000)
            all.addAll(p.rows)
            if (p.total > knownTotal) knownTotal = p.total
            onProgress?.invoke(all.size, knownTotal)
            if (!p.hasNext || p.rows.isEmpty()) break
            page++
            if (page > 200) break // 安全上限 100 万行
        }
        log("fetchAllDraftData($subjectCode) -> ${all.size} rows")
        all
    }

    /**
     * 探测「公司在该评估方法下的底稿列表」接口（进入资产基础法页面时前端会调用）。
     * 解析出各科目的底稿记录（含 dataAvailableFlag 与底稿 id），
     * 并对第一个有数据的科目试拉明细，验证 draft/detail/data 参数是否正确。
     */
    suspend fun probeAssignmentDrafts(projectId: String, companyIds: List<String>) {
        for (cid in companyIds) {
            try {
                val path = "/ty/api/company/assignment/draft/list?projectId=$projectId&companyId=$cid&valuationType=1"
                val j = execute(buildRequest(path, "GET", null))
                val arr = findArray(j, "data", "list", "rows", "result") ?: JSONArray()
                val withData = mutableListOf<String>()
                var targetInfo = ""
                var firstCode: String? = null
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val code = o.optString("subjectCode", "")
                    val name = o.optString("name", "")
                    val flag = o.optInt("dataAvailableFlag", -1)
                    val draftId = o.optString("id", "")
                    if (flag == 1) {
                        withData.add("$code/$name(draft=$draftId)")
                        if (firstCode == null) firstCode = code
                    }
                    if (code == "C4-8-4" || code == "4-8-4") {
                        targetInfo = "机器设备 draft=$draftId flag=$flag name=$name showFlag=${o.optInt("showFlag", -1)}"
                    }
                }
                log("probeDrafts: ${arr.length()} subjects; withDataCount=${withData.size}; target=${targetInfo.isNotBlank()}")
                // 验证：对第一个有数据的科目，用现有参数试拉明细
                if (firstCode != null) {
                    try {
                        val page = draftData(projectId, firstCode, listOf(cid), 1, 1, 5000)
                        log("probeDrafts: verified ${page.total} rows")
                    } catch (e: Exception) {
                        log("probeDrafts: verify failed")
                    }
                }
            } catch (e: Exception) {
                log("probeDrafts failed")
            }
        }
    }
    /** Read-only JSON endpoint for attachment metadata. */
    suspend fun rawGet(path: String): JSONObject = execute(buildRequest(path, "GET", null))
}
