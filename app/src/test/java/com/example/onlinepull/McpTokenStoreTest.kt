package com.example.onlinepull

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class McpTokenStoreTest {
    @Test fun persistedCredentialIsEncryptedSurvivesReopenAndCanBeRemoved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = McpTokenStore(context) { key }
        val token = "fake-test-token-01234567890123456789"
        store.save(token)
        val file = File(context.noBackupFilesDir, "valuation-mcp-token")
        assertTrue(file.isFile)
        assertFalse(file.readText().contains(token))
        assertEquals(token, McpTokenStore(context) { key }.read())
        store.save("replacement-test-token-01234567890")
        assertEquals("replacement-test-token-01234567890", store.read())
        store.clear()
        assertNull(store.read())
        assertFalse(file.exists())
    }

    @Test fun corruptedCredentialDoesNotSilentlyBecomeAValidConnection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.noBackupFilesDir, "valuation-mcp-token").writeText("corrupted")
        val store = McpTokenStore(context)
        assertThrows(McpFailure::class.java) { store.read() }
        store.clear()
    }
}
