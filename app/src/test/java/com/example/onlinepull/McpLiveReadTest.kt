package com.example.onlinepull

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opt-in smoke test: credentials are supplied only via the local test process environment. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class McpLiveReadTest {
    @Test fun readsAuthorizedTestProjectThroughTheProductionClient() = runBlocking {
        val token = System.getenv("VALUATION_MCP_TEST_TOKEN").orEmpty()
        assumeTrue("No live credential supplied", token.isNotBlank())
        val client = AssessmentSystemClient(McpClient(token))
        val project = client.listProjects().single { it.code == "20250715002" && it.name == "测试项目" }
        assertEquals("101725686071298", project.id)
        val companies = client.listCompanies(project.id)
        var subjectCount = 0
        val rows = mutableListOf<RemoteInventoryItem>()
        for (company in companies) {
            val subjects = client.listAssetBasedSubjects(project.id, company.id)
            subjectCount += subjects.size
            for (subject in subjects) rows += client.listInventoryItems(project.id, company, subject)
        }
        assertTrue(companies.isNotEmpty())
        assertTrue(subjectCount > 0)
        assertTrue(rows.all { it.shouldCheck && !it.rowId.isNullOrBlank() })
        println("MCP read-only smoke test: companies=${companies.size}, companySubjects=$subjectCount, inventoryRows=${rows.size}")
    }
}
