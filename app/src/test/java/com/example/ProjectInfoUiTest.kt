package com.example

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.Project
import com.example.ui.WifiTransferLink
import com.example.ui.StockViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProjectInfoUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun consultingInfoUsesCorrectLabelsAndExplainsLocalEdits() {
        compose.setContent { MyApplicationTheme { Surface {
            ProjectInfoCard(Project(name = "咨询项目", baseDate = "2026-09-30", companyName = "测试产权单位", reportType = "咨询报告"), true, {})
        } } }
        compose.onNodeWithText("基准日").assertExists()
        compose.onNodeWithText("产权持有单位").assertExists()
        compose.onNodeWithText("评估基准日").assertDoesNotExist()
        compose.onNodeWithText("被评估单位").assertDoesNotExist()
        compose.onNodeWithText("修改仅保存在本机", substring = true).assertExists()
        compose.onRoot().captureRoboImage(filePath = "build/reports/mcp-ui/project-info.png")
    }
    @Test fun toolbarOffersCopyWithoutDisplayingAnIncompleteAddress() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val address = WifiTransferLink.create("192.168.31.225", 9090, "fake-pairing-token")!!
        var copied = false
        compose.setContent { MyApplicationTheme { WifiTransferToolbar(true, address, {}, { WifiTransferLink.copy(app, address); copied = true }) } }
        compose.onNodeWithText("192.168.31.225", substring = true).assertDoesNotExist()
        compose.onNodeWithText("复制传输地址").performClick()
        assertTrue(copied)
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("http://192.168.31.225:9090/?token=fake-pairing-token", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }
    @Test fun enablingWifiCopiesTheCurrentPairingLink() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        lateinit var model: StockViewModel
        compose.runOnUiThread { model = StockViewModel(app) }
        val port = java.net.ServerSocket(0).use { it.localPort }
        try {
            compose.runOnUiThread { model.toggleWifiTransfer(true, port) }
            val address = model.wifiTransferAddress.value
            assertNotNull(address)
            assertTrue(address!!.endsWith("?token=" + model.wifiPairingToken.value))
            val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(address, clipboard.primaryClip!!.getItemAt(0).text.toString())
        } finally { compose.runOnUiThread { model.toggleWifiTransfer(false) } }
    }
}
