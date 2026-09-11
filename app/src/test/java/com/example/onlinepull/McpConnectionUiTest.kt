package com.example.onlinepull

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.RemoteSyncViewModel
import com.example.ui.StockViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class McpConnectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedReplacementKeepsOldTokenThenValidConnectionCanSyncWithoutWebLogin() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val credentials = MemoryCredentials()
        lateinit var model: RemoteSyncViewModel
        lateinit var stock: StockViewModel
        compose.runOnUiThread {
            model = RemoteSyncViewModel(app, credentials) { token -> FixtureSource(token.startsWith("invalid")) }
            stock = StockViewModel(app)
        }
        compose.setContent { MyApplicationTheme { Surface { AutoOnlinePullScreen(stock, {}, model) } } }
        awaitState { !model.busy.value }
        compose.onNodeWithContentDescription("连接设置").performClick()
        compose.onRoot().captureRoboImage(filePath = "build/reports/mcp-ui/connection-settings.png")
        compose.onNodeWithTag("mcp_token_input").performTextInput("invalid-token-01234567890")
        compose.onNodeWithTag("mcp_save_token").assertIsEnabled().performClick()
        awaitState { !model.busy.value }
        assertNotNull(model.credentialError.value)
        assertEquals("old-token-012345678901234", credentials.token)
        compose.onNodeWithTag("mcp_token_input").performTextClearance()
        compose.onNodeWithTag("mcp_token_input").performTextInput("valid-token-0123456789012")
        compose.onNodeWithTag("mcp_save_token").assertIsEnabled().performClick()
        awaitState { !model.busy.value && model.projectsLoaded.value }
        assertEquals("valid-token-0123456789012", credentials.token)
        compose.waitForIdle()
        compose.onNodeWithText("测试项目").assertExists()
        compose.onRoot().captureRoboImage(filePath = "build/reports/mcp-ui/project-list.png")
        compose.onNodeWithText("测试项目").performClick()
        compose.onNodeWithTag("remote_sync_button").performClick()
        awaitState { !model.busy.value && model.report.value != null }
        compose.waitForIdle()
        compose.onNodeWithText("盘点清单已同步").assertExists()
        compose.onRoot().captureRoboImage(filePath = "build/reports/mcp-ui/sync-result.png")
        assertEquals(1, model.report.value!!.remoteRows)
    }

    private fun awaitState(condition: () -> Boolean) {
        compose.waitUntil(15000) {
            // IO completion resumes ViewModel work on the Android main looper.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            condition()
        }
    }

    private class MemoryCredentials : McpCredentials {
        @Volatile var token: String? = "old-token-012345678901234"
        override fun read() = token
        override fun save(token: String) { this.token = token }
        override fun clear() { token = null }
    }

    private class FixtureSource(private val expired: Boolean) : InventorySource {
        override suspend fun listProjects(): List<RemoteProjectSummary> {
            if (expired) throw McpAuthFailure()
            return listOf(RemoteProjectSummary("101725686071298", "测试项目", "20250715002"))
        }
        override suspend fun listCompanies(projectId: String) = listOf(RemoteCompanySummary("101725686071320", "XXX委托公司"))
        override suspend fun listAssetBasedSubjects(projectId: String, companyId: String) = listOf(RemoteSubject("C4-8-4", "4-8-4", "机器设备"))
        override suspend fun listInventoryItems(projectId: String, company: RemoteCompanySummary, subject: RemoteSubject) =
            listOf(RemoteInventoryItem("174221412401152", company.id, company.name, subject.code, subject.name, "", null, "1", "1", true,
                JSONObject().put("_id", "174221412401152").put("SFPD", "是")))
    }
}
