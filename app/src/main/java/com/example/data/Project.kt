package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseDate: String = "",
    val companyName: String = "",
    val reportType: String = InventoryConstants.REPORT_TYPE_EVALUATION,
    val columnHeadersJson: String = "",
    val watermarkEnabled: Boolean = false,
    val watermarkBlEnabled: Boolean = true,
    val watermarkBlShowDate: Boolean = true,
    val watermarkBlShowTime: Boolean = true,
    val watermarkBlShowGps: Boolean = true,
    val watermarkBlShowAddress: Boolean = true,
    val watermarkTrEnabled: Boolean = true,
    @androidx.room.ColumnInfo(defaultValue = "0") val metadataLocallyEdited: Boolean = false
)

val Project.baseDateLabel: String get() = if (reportType == InventoryConstants.REPORT_TYPE_EVALUATION) "评估基准日" else "基准日"
val Project.companyLabel: String get() = if (reportType == InventoryConstants.REPORT_TYPE_EVALUATION) "被评估单位" else "产权持有单位"
