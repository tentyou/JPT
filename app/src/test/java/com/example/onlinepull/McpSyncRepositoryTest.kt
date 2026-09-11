package com.example.onlinepull

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class McpSyncRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var source: FixtureSource
    private lateinit var repository: RemoteSyncRepository
    private val project = RemoteProjectSummary("101725686071298", "测试项目", "20250715002")

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        source = FixtureSource()
        repository = RemoteSyncRepository(context, source, db)
    }
    @After fun close() { db.close() }

    @Test fun repeatedSyncPreservesUidPhotosPdfAndLocalProjectSettings() = runBlocking(Dispatchers.IO) {
        val first = repository.sync(project)
        val original = db.stockItemDao().getItemsByProjectSync(first.localProjectId).single()
        db.stockItemDao().updatePhotoState(original.uid, 2, "已生成")
        val photo = File(context.filesDir, "photos/${original.uid}/one.jpg").apply { parentFile!!.mkdirs(); writeText("photo-evidence") }
        val pdf = File(context.filesDir, "pdfs/${original.uid}/照片.pdf").apply { parentFile!!.mkdirs(); writeText("pdf-evidence") }
        db.projectDao().insertProject(db.projectDao().getProjectById(first.localProjectId)!!.copy(baseDate = "2026-09-11", watermarkEnabled = true))
        source.rows = listOf(row("a", "更新名称"))
        val second = repository.sync(project)
        val updated = db.stockItemDao().getItemsByProjectSync(first.localProjectId).single()
        assertEquals(first.localProjectId, second.localProjectId)
        assertEquals(original.uid, updated.uid)
        assertEquals(2, updated.photoCount)
        assertEquals("已生成", updated.pdfStatus)
        assertEquals("更新名称", updated.name)
        assertEquals("photo-evidence", photo.readText())
        assertEquals("pdf-evidence", pdf.readText())
        assertEquals("2026-09-11", db.projectDao().getProjectById(first.localProjectId)!!.baseDate)
        assertTrue(db.projectDao().getProjectById(first.localProjectId)!!.watermarkEnabled)
    }

    @Test fun failureOnLaterSubjectDoesNotCommitEarlierChangesOrInactivateRows() = runBlocking(Dispatchers.IO) {
        val first = repository.sync(project)
        val original = db.stockItemDao().getItemsByProjectSync(first.localProjectId).single()
        source.rows = listOf(row("b", "不应保存"))
        source.failSecondSubject = true
        try { repository.sync(project); fail("expected error") } catch (_: McpAuthFailure) {}
        assertEquals(listOf(original), db.stockItemDao().getItemsByProjectSync(first.localProjectId))
        val link = db.remoteSyncDao().findProjectByLocalId(first.localProjectId)!!
        assertEquals(first.syncedAt, link.lastSyncAt)
        assertEquals("failed", link.syncState)
        assertTrue(db.remoteSyncDao().bindingsForProject(first.localProjectId).single().active)
    }

    @Test fun fullSnapshotInactivatesDisappearedAssetAndLaterReusesSameUid() = runBlocking(Dispatchers.IO) {
        val first = repository.sync(project)
        val original = db.stockItemDao().getItemsByProjectSync(first.localProjectId).single()
        source.rows = emptyList()
        val removed = repository.sync(project)
        assertEquals(1, removed.inactive)
        assertFalse(db.stockItemDao().getItemByUid(original.uid)!!.shouldCheck)
        assertEquals("inactive", db.remoteSyncDao().bindingsForProject(first.localProjectId).single().syncState)
        source.rows = listOf(row("a"))
        repository.sync(project)
        assertEquals(original.uid, db.stockItemDao().getItemsByProjectSync(first.localProjectId).single().uid)
        assertTrue(db.stockItemDao().getItemByUid(original.uid)!!.shouldCheck)
    }

    @Test fun permissionLossRetainsLastSnapshotAndFlagsUnverified() = runBlocking(Dispatchers.IO) {
        val first = repository.sync(project)
        val original = db.stockItemDao().getItemsByProjectSync(first.localProjectId)
        repository.recordAccessibleProjects(emptyList())
        assertEquals(original, db.stockItemDao().getItemsByProjectSync(first.localProjectId))
        assertEquals("unverified", db.remoteSyncDao().findProjectByLocalId(first.localProjectId)!!.syncState)
    }

    @Test fun duplicateRemoteIdsRequireReviewAndNeverOverwriteExistingEvidence() = runBlocking(Dispatchers.IO) {
        val first = repository.sync(project)
        val uid = db.stockItemDao().getItemsByProjectSync(first.localProjectId).single().uid
        db.stockItemDao().updatePhotoState(uid, 3, "已生成")
        source.rows = listOf(row("a", "甲"), row("a", "乙"))
        val result = repository.sync(project)
        assertEquals(1, result.conflicts)
        assertEquals(3, db.stockItemDao().getItemByUid(uid)!!.photoCount)
        assertEquals("机器", db.stockItemDao().getItemByUid(uid)!!.name)
        assertEquals("机器", db.remoteSyncDao().bindingsForProject(first.localProjectId).single().assetName)
        assertEquals("conflict", db.remoteSyncDao().bindingsForProject(first.localProjectId).single().syncState)
    }

    @Test fun migrationUsesTheSameStableKeyAsWebLoginVersion() {
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest("row:101725686071298:a".toByteArray()).joinToString("") { "%02x".format(it) }.take(40)
        assertEquals("remote:$expected", RemoteSyncRepository.stableKey(project.id, row("a")))
    }

    private class FixtureSource : InventorySource {
        var rows = listOf(row("a"))
        var failSecondSubject = false
        override suspend fun listProjects() = listOf(RemoteProjectSummary("101725686071298", "测试项目"))
        override suspend fun listCompanies(projectId: String) = listOf(RemoteCompanySummary("2", "公司"))
        override suspend fun listAssetBasedSubjects(projectId: String, companyId: String) =
            listOf(RemoteSubject("C4-8-4", "4-8-4", "机器设备"), RemoteSubject("C4-8-5", "4-8-5", "车辆"))
        override suspend fun listInventoryItems(projectId: String, company: RemoteCompanySummary, subject: RemoteSubject): List<RemoteInventoryItem> {
            if (subject.code == "C4-8-5") {
                if (failSecondSubject) throw McpAuthFailure()
                return emptyList()
            }
            return rows
        }
    }
    companion object {
        private fun row(id: String, name: String = "机器") = RemoteInventoryItem(id, "2", "公司", "C4-8-4", "机器设备", "", null, "001", name, true,
            JSONObject().put("_id", id).put("SFPD", "是").put("SBMC", name))
    }
}
