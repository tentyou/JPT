package com.example.onlinepull

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class McpClientTest {
    @Test fun performsHandshakeReadsSseAndKeepsSession() = runBlocking {
        val methods = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            val method = body.getString("method")
            methods += method
            assertEquals("Bearer test-token-for-unit-tests", request.header("Authorization"))
            if (method != "initialize") {
                assertEquals("session-one", request.header("Mcp-Session-Id"))
                assertEquals("2025-06-18", request.header("MCP-Protocol-Version"))
            }
            val result = when (method) {
                "initialize" -> JSONObject().put("protocolVersion", "2025-06-18").put("capabilities", JSONObject().put("tools", JSONObject()))
                "notifications/initialized" -> null
                else -> JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "[{\"id\":101725686071298,\"projectName\":\"测试项目\"}]")))
            }
            val rpc = JSONObject().put("jsonrpc", "2.0").put("id", body.opt("id")).put("result", result)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (result == null) 202 else 200).message("OK")
                .header("Mcp-Session-Id", "session-one")
                .body((if (result == null) "" else ": heartbeat\n\nevent: message\ndata: $rpc\n\n").toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        val client = AssessmentSystemClient(McpClient("test-token-for-unit-tests", http))
        assertEquals("101725686071298", client.listProjects().single().id)
        client.listProjects()
        assertEquals(listOf("initialize", "notifications/initialized", "tools/call", "tools/call"), methods)
    }

    @Test fun skipsUnrelatedSseEventsAndReadsMultilineData() {
        val source = Buffer().writeUtf8("data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}\n\ndata: {\"id\":\"7\",\ndata: \"result\":{}}\n\n")
        assertEquals("7", McpClient.readEvent(source, "7").getString("id"))
    }

    @Test fun distinguishesExpiredTokenFromAccessDeniedWithoutEchoingSecrets() {
        for (code in listOf(401, 403)) {
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("failure")
                    .body("secret-that-must-not-be-shown".toResponseBody()).build()
            }.build()
            val failure = try {
                runBlocking { McpClient("test-token-for-unit-tests", http).callTool("get_my_projects", JSONObject()) }
                fail("should fail"); error("unreachable")
            } catch (error: McpFailure) { error }
            assertEquals(code == 401, failure is McpAuthFailure)
            assertEquals(code == 403, failure is McpAccessFailure)
            assertFalse(failure.message.orEmpty().contains("secret-that"))
        }
    }

    @Test fun rejectsWriteToolsBeforeOpeningConnection() {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { calls++; error("must not connect") }.build()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { McpClient("test-token-for-unit-tests", http).callTool("execute_asset_based_approach_draft_import", JSONObject()) }
        }
        assertEquals(0, calls)
    }

    @Test fun acceptsRawBearerAndFullConfigButPinsEndpoint() {
        val token = "zhmcp_test_token_0123456789"
        assertEquals(token, McpCredentialInput.parse(token))
        assertEquals(token, McpCredentialInput.parse("Bearer $token"))
        val server = JSONObject().put("url", McpCredentialInput.ENDPOINT)
            .put("headers", JSONObject().put("Authorization", "Bearer $token"))
        val config = JSONObject().put("mcpServers", JSONObject().put("valuation-mcp", server))
        assertEquals(token, McpCredentialInput.parse(config.toString()))
        server.put("url", "https://example.com/steal")
        assertThrows(McpFailure::class.java) { McpCredentialInput.parse(config.toString()) }
        assertThrows(McpFailure::class.java) { McpCredentialInput.parse("bad\r\nToken") }
    }

    @Test fun readsAllPagesAndFiltersNestedInventoryFlag() = runBlocking {
        val calls = mutableListOf<Int>()
        val source = AssessmentSystemClient(McpTools { name, args ->
            assertEquals("get_asset_based_approach_draft_data", name)
            val page = args.getInt("pageNum")
            calls += page
            page(page, 2, page == 1, JSONArray().put(JSONObject()
                .put("_id", "asset-$page").put("MC", "设备$page").put("SFPD", JSONObject().put("value", if (page == 2) "是" else "否"))))
        })
        val items = source.listInventoryItems("1", RemoteCompanySummary("2", "测试公司"), RemoteSubject("C4-99", "4-99", "新增科目"))
        assertEquals(listOf(1, 2), calls)
        assertEquals("asset-2", items.single().rowId)
        assertEquals("设备2", items.single().itemName)
    }

    @Test fun changingTotalsAndRepeatedPagesFailInsteadOfReturningPartialList() {
        for (changeTotal in listOf(true, false)) {
            val source = AssessmentSystemClient(McpTools { _, args ->
                val number = args.getInt("pageNum")
                page(number, if (number == 2 && changeTotal) 3 else 2, number == 1,
                    JSONArray().put(JSONObject().put("_id", "same-id").put("SFPD", "是")))
            })
            assertThrows(RemoteParseFailure::class.java) {
                runBlocking { source.listInventoryItems("1", RemoteCompanySummary("2", "公司"), RemoteSubject("C4-99", "4-99", "新增科目")) }
            }
        }
    }

    @Test fun subjectNamesComeFromEachCompanyAndAreNotLimitedToOldMappings() = runBlocking {
        val source = AssessmentSystemClient(McpTools { _, args ->
            JSONObject().put("success", true).put("projectId", 1).put("companyId", args.getLong("companyId"))
                .put("total", 1).put("subjects", JSONArray().put(JSONObject().put("subjectCode", "C4-99")
                    .put("standardSubjectName", "新增科目").put("companySubjectName", "客户自定义名称")))
        })
        assertEquals("新增科目", source.listAssetBasedSubjects("1", "2").single().name)
    }

    private fun page(number: Int, total: Int, next: Boolean, rows: JSONArray) = JSONObject()
        .put("success", true).put("projectId", 1).put("companyId", 2).put("subjectCode", "C4-99")
        .put("pageInfo", JSONObject().put("pageNum", number).put("total", total).put("next", next)).put("data", rows)
}
