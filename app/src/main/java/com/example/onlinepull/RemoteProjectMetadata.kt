package com.example.onlinepull

import org.json.JSONArray
import java.time.Instant
import java.time.ZoneOffset

/** Business type is independent of the report template (report/notes/investment notes). */
data class RemoteProjectMetadata(val baseDate: String = "", val companyName: String = "", val reportType: String = "") {
    companion object {
        fun parse(values: JSONArray, fallbackDateMillis: String, companyNames: List<String>): RemoteProjectMetadata {
            val fields = (0 until values.length()).map { index ->
                values.optJSONObject(index) ?: throw RemoteParseFailure("项目设置字段格式不正确")
            }.associate { it.optString("namedKey") to it.optString("value") }
            val timestamp = fields["evaluationBaseDate"].orEmpty().ifBlank { fallbackDateMillis }
            val date = if (timestamp.isBlank()) "" else try {
                Instant.ofEpochMilli(timestamp.toLong()).atOffset(ZoneOffset.ofHours(8)).toLocalDate().toString()
            } catch (_: Exception) { throw RemoteParseFailure("基准日格式不正确，项目设置未更新") }
            val type = when (val business = fields["businessType"].orEmpty().trim()) {
                "资产评估业务" -> "评估报告"
                "评估咨询业务" -> "咨询报告"
                "" -> "待确认"
                else -> business
            }
            return RemoteProjectMetadata(date, companyNames.filter { it.isNotBlank() }.distinct().joinToString("、"), type)
        }
    }
}
