package com.example.onlinepull

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.example.data.SyncStatus
import com.example.data.UploadStatus
import org.junit.Test

class RemoteParsingTest {
    @Test
    fun remoteCodeKeepsPrefixForLookupAlias() {
        assertTrue(SubjectFieldMap.normalize("C4-8-4") == "4-8-4")
        assertNotNull(SubjectFieldMap.byCode("C4-8-4"))
        assertNotNull(SubjectFieldMap.byCode("4-8-4"))
    }

    @Test
    fun persistedStatusesUseStableStorageValues() {
        assertTrue(SyncStatus.fromStorage("synced") == SyncStatus.SYNCED)
        assertTrue(SyncStatus.fromStorage(" CONFLICT ") == SyncStatus.CONFLICT)
        assertTrue(SyncStatus.fromStorage("conflict") == SyncStatus.CONFLICT)
        assertTrue(UploadStatus.fromStorage("session_expired") == UploadStatus.SESSION_EXPIRED)
        assertTrue(UploadStatus.fromStorage("future_status") == null)
    }

    @Test
    fun checkFlagRequiresExplicitTrueValue() {
        listOf("是", "true", "1", "1.0", "yes", "√", "需要盘点").forEach {
            assertTrue("expected true for $it", SubjectFieldMap.isCheckTrue(it))
        }
        listOf(null, "", "否", "0", "unknown", "待配置").forEach {
            assertFalse("expected false for $it", SubjectFieldMap.isCheckTrue(it))
        }
    }
}
