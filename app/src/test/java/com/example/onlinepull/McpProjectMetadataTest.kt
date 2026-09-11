package com.example.onlinepull

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class McpProjectMetadataTest {
    private fun fields(vararg values: Pair<String, String>) = JSONArray().apply {
        values.forEach { (key, value) -> put(JSONObject().put("namedKey", key).put("value", value)) }
    }
    @Test fun contextUsesEastEightDateAndBusinessTypeInsteadOfReportTemplate() {
        val data = fields("evaluationBaseDate" to "1790697600000", "businessType" to "评估咨询业务", "reportTypeName" to "评估报告", "evaluationBusinessNature" to "资产评估业务")
        val parsed = RemoteProjectMetadata.parse(data, "", listOf("根公司", "子公司"))
        assertEquals("2026-09-30", parsed.baseDate)
        assertEquals("咨询报告", parsed.reportType)
        assertEquals("根公司、子公司", parsed.companyName)
    }
    @Test fun missingBusinessTypeStaysUnknownAndDateCanFallBackToProjectList() {
        val parsed = RemoteProjectMetadata.parse(fields(), "1753804800000", listOf("公司"))
        assertEquals("2025-07-30", parsed.baseDate)
        assertEquals("待确认", parsed.reportType)
    }
    @Test fun blankFieldsAndNewBusinessTypesRemainSupported() {
        assertEquals("", RemoteProjectMetadata.parse(fields("evaluationBaseDate" to ""), "", emptyList()).baseDate)
        assertEquals("集团内部复核", RemoteProjectMetadata.parse(fields("businessType" to "集团内部复核"), "", emptyList()).reportType)
        assertEquals("评估报告", RemoteProjectMetadata.parse(fields("businessType" to "资产评估业务"), "", emptyList()).reportType)
    }
    @Test fun invalidDateFailsBeforeCommittingMetadata() {
        assertThrows(RemoteParseFailure::class.java) { RemoteProjectMetadata.parse(fields("evaluationBaseDate" to "not-a-date"), "", emptyList()) }
    }
}
