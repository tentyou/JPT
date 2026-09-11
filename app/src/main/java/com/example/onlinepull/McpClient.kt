package com.example.onlinepull

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

open class McpFailure(message: String) : Exception(message)
class McpAuthFailure : McpFailure("Token 已过期或失效，请更新连接凭据；本地资料仍可离线使用")
class McpAccessFailure : McpFailure("当前凭据无权读取此项目或科目，上次完整清单已保留，暂时无法核验")
private class McpSessionExpired : Exception()

fun interface McpTools {
    suspend fun callTool(name: String, arguments: JSONObject): Any
}

/** Read-only Streamable HTTP client. The credential and session never enter logs or saved UI state. */
class McpClient(
    private val token: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
) : McpTools {
    private val mutex = Mutex()
    private var session: String? = null
    private var initialized = false
    private var nextId = 0L

    override suspend fun callTool(name: String, arguments: JSONObject): Any = withContext(Dispatchers.IO) {
        require(name in READ_TOOLS) { "仅允许读取项目和资产" }
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            if (!initialized) initialize()
            val result = try {
                request("tools/call", JSONObject().put("name", name).put("arguments", arguments))
            } catch (_: McpSessionExpired) {
                initialized = false
                session = null
                initialize()
                request("tools/call", JSONObject().put("name", name).put("arguments", arguments))
            } ?: throw RemoteParseFailure("MCP 未返回工具结果")
            currentCoroutineContext().ensureActive()
            if (result.optBoolean("isError")) throw toolError(result)
            val structured = result.opt("structuredContent")
            val data = if (structured is JSONObject || structured is JSONArray) structured else {
                val blocks = result.optJSONArray("content") ?: throw RemoteParseFailure("MCP 未返回数据")
                val texts = (0 until blocks.length()).mapNotNull { index ->
                    blocks.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.optString("text")
                }
                try { JSONTokener(texts.joinToString("\n")).nextValue() }
                catch (_: Exception) { throw RemoteParseFailure("MCP 返回的数据不是有效 JSON") }
            }
            if (data is JSONObject && data.has("success") && !data.optBoolean("success")) throw toolError(data)
            if (data !is JSONObject && data !is JSONArray) throw RemoteParseFailure("MCP 数据结构不正确")
            data
        }
    }

    private fun initialize() {
        val result = request("initialize", JSONObject()
            .put("protocolVersion", VERSION)
            .put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "tenken-inventory").put("version", "1.0")))
            ?: throw RemoteParseFailure("MCP 初始化没有返回结果")
        if (result.optString("protocolVersion") != VERSION || result.optJSONObject("capabilities")?.has("tools") != true) {
            throw McpFailure("MCP 协议或工具能力与此版本不兼容")
        }
        request("notifications/initialized", null, notification = true)
        initialized = true
    }

    private fun request(method: String, params: JSONObject?, notification: Boolean = false): JSONObject? {
        val id = if (notification) null else (++nextId).toString()
        val payload = JSONObject().put("jsonrpc", "2.0").put("method", method)
        id?.let { payload.put("id", it) }
        params?.let { payload.put("params", it) }
        val request = Request.Builder().url(McpCredentialInput.ENDPOINT)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json, text/event-stream")
            .apply {
                if (method != "initialize") header("MCP-Protocol-Version", VERSION)
                session?.let { header("Mcp-Session-Id", it) }
            }
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> throw McpAuthFailure()
                403 -> throw McpAccessFailure()
                404 -> if (initialized) throw McpSessionExpired()
            }
            if (!response.isSuccessful) throw McpFailure("连接失败（HTTP ${response.code}），请稍后重试")
            if (method == "initialize") session = response.header("Mcp-Session-Id")
            if (notification) return@use null
            val body = response.body
            val rpc = if (body.contentType()?.subtype == "event-stream") {
                readEvent(body.source(), id!!)
            } else {
                val source = body.source()
                if (source.request(MAX_RESPONSE_BYTES + 1)) throw RemoteParseFailure("MCP 响应过大，请联系管理员")
                try { JSONObject(source.readUtf8()) } catch (_: Exception) { throw RemoteParseFailure("MCP 响应格式错误") }
            }
            if (rpc.optString("id") != id) throw RemoteParseFailure("MCP 响应编号不匹配")
            if (rpc.has("error")) throw toolError(rpc.getJSONObject("error"))
            rpc.optJSONObject("result") ?: throw RemoteParseFailure("MCP 响应缺少 result")
        }
    }

    private fun toolError(value: JSONObject): McpFailure {
        // Inspect server errors for classification, but never surface raw responses or credentials.
        val raw = value.toString().lowercase()
        return when {
            listOf("token expired", "invalid token", "token已过期", "token 已过期", "登录已失效", "登录态失效", "unauthorized").any { it in raw } -> McpAuthFailure()
            listOf("forbidden", "无权", "权限不足", "没有权限").any { it in raw } -> McpAccessFailure()
            else -> McpFailure("远端读取失败，上次完整清单未改动；请检查项目权限后重试")
        }
    }

    companion object {
        private const val VERSION = "2025-06-18"
        private const val MAX_RESPONSE_BYTES = 16L * 1024 * 1024
        private val READ_TOOLS = setOf("get_my_projects", "get_project_companies", "get_company_asset_based_approach_subjects", "get_asset_based_approach_draft_data")

        /** Finish at the matching event; an SSE response need not close its connection. */
        internal fun readEvent(source: BufferedSource, id: String): JSONObject {
            val data = StringBuilder()
            var consumed = 0L
            fun event(): JSONObject? {
                if (data.isEmpty()) return null
                val parsed = try { JSONObject(data.toString()) } catch (_: Exception) { throw RemoteParseFailure("MCP 事件格式错误") }
                data.setLength(0)
                return parsed.takeIf { it.optString("id") == id }
            }
            while (!source.exhausted()) {
                val line = source.readUtf8LineStrict(MAX_RESPONSE_BYTES)
                consumed += line.length
                if (consumed > MAX_RESPONSE_BYTES) throw RemoteParseFailure("MCP 响应过大")
                if (line.isEmpty()) event()?.let { return it }
                else if (line.startsWith("data:")) {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").removePrefix(" "))
                }
            }
            return event() ?: throw RemoteParseFailure("MCP 事件未返回请求结果")
        }
    }
}
