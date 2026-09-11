package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Project
import com.example.data.baseDateLabel
import com.example.data.companyLabel

@Composable
fun ProjectInfoCard(project: Project?, fromRemote: Boolean, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("project_info_card"),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("项目信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (fromRemote) {
                        if (project?.metadataLocallyEdited == true) "线上项目 · 使用本地修改" else "来自线上项目"
                    } else "本地项目", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onEdit, modifier = Modifier.testTag("edit_project_info")) { Text("编辑") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            ProjectInfoField(project?.baseDateLabel ?: "基准日", project?.baseDate.orEmpty())
            ProjectInfoField(project?.companyLabel ?: "产权持有单位", project?.companyName.orEmpty())
            ProjectInfoField("报告类型", project?.reportType.orEmpty())
            if (fromRemote) Text(
                "信息来自线上系统，建议保持一致。修改仅保存在本机，不影响线上；后续同步保留本地修改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProjectInfoField(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, modifier = Modifier.width(88.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "未设置" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun WifiTransferToolbar(enabled: Boolean, address: String?, onToggle: (Boolean) -> Unit, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Wi-Fi", style = MaterialTheme.typography.labelSmall)
        Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.height(32.dp).testTag("wifi_toolbar_switch"))
        if (enabled) TextButton(onClick = onCopy, enabled = address != null, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.height(32.dp).testTag("copy_transfer_address")) {
            Text("复制传输地址", style = MaterialTheme.typography.labelMedium)
        }
    }
}
