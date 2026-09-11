package com.example.onlinepull

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.PdfPreviewDialog
import com.example.data.RemoteAssetBinding
import com.example.data.StockItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun RemoteHistoryDialog(items: List<StockItem>, bindings: List<RemoteAssetBinding>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewPdf by remember { mutableStateOf<File?>(null) }
    var previewPhoto by remember { mutableStateOf<File?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<StockItem?>(null) }
    val history = bindings.filter { !it.active || it.syncState == "conflict" }
    val byUid = items.associateBy { it.uid }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val selected = pendingExport
        pendingExport = null
        if (uri != null && selected != null) {
            exporting = true
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = context.contentResolver.openOutputStream(uri) ?: error("文件无法打开")
                        ZipOutputStream(stream).use { zip ->
                            val photoDir = File(context.filesDir, "photos/${selected.uid}")
                            photoDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("jpg", "jpeg", "png") }?.sortedBy { it.name }?.forEach { photo ->
                                zip.putNextEntry(ZipEntry("photos/" + photo.name))
                                photo.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                            val pdf = File(context.filesDir, "pdfs/${selected.uid}/照片.pdf")
                            if (pdf.isFile) {
                                zip.putNextEntry(ZipEntry("照片.pdf"))
                                pdf.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                            val binding = history.firstOrNull { it.stockUid == selected.uid }
                            zip.putNextEntry(ZipEntry("历史记录.json"))
                            val record = org.json.JSONObject()
                                .put("assetName", selected.name).put("assetCode", selected.originalCode)
                                .put("companyName", binding?.companyName).put("subjectName", binding?.subjectName)
                                .put("status", binding?.syncState).put("remoteRowId", binding?.remoteRowId)
                                .put("lastSyncedAt", binding?.lastSyncedAt)
                                .put("snapshot", binding?.remoteSnapshotJson?.let { org.json.JSONObject(it) })
                            zip.write(record.toString(2).toByteArray(Charsets.UTF_8))
                            zip.closeEntry()
                        }
                    }.isSuccess
                }
                exporting = false
                Toast.makeText(context, if (success) "历史资料已导出" else "导出失败，请重试", Toast.LENGTH_LONG).show()
            }
        }
    }
    Dialog(onDismissRequest = { if (!exporting) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                Row {
                    Text("历史与核对", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, enabled = !exporting) { Text("关闭") }
                }
                Text("历史记录不进入当前盘点清单。需要核对的记录会保留原资料，不自动合并。", style = MaterialTheme.typography.bodySmall)
                if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                    if (history.isEmpty()) item { Text("暂无历史或待核对记录") }
                    items(history, key = { it.stableKey }) { binding ->
                        val item = byUid[binding.stockUid]
                        if (item != null) {
                            Column {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(binding.companyName, binding.subjectName, binding.assetCode).filter { it.isNotBlank() }.joinToString(" · "))
                                Text(if (binding.syncState == "conflict") binding.conflictReason ?: "需人工核对" else "已不在当前应盘范围",
                                    color = if (binding.syncState == "conflict") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("远端 ID：" + (binding.remoteRowId ?: "缺失"), style = MaterialTheme.typography.bodySmall)
                                val photos by produceState<List<File>>(emptyList(), item.uid) {
                                    value = withContext(Dispatchers.IO) {
                                        File(context.filesDir, "photos/${item.uid}").listFiles()
                                            ?.filter { it.isFile && it.extension.lowercase() in listOf("jpg", "jpeg", "png") }?.sortedBy { it.name }.orEmpty()
                                    }
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(photos, key = { it.absolutePath }) { photo ->
                                        AsyncImage(photo, contentDescription = "查看历史照片", contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(88.dp).clickable { previewPhoto = photo })
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val pdf = File(context.filesDir, "pdfs/${item.uid}/照片.pdf")
                                    if (pdf.isFile) OutlinedButton(onClick = { previewPdf = pdf }) { Text("查看 PDF") }
                                    OutlinedButton(onClick = {
                                        pendingExport = item
                                        export.launch("历史资料-${item.originalCode.ifBlank { item.uid }}.zip")
                                    }, enabled = !exporting) { Text("导出此条资料") }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
    previewPdf?.let { PdfPreviewDialog(it) { previewPdf = null } }
    previewPhoto?.let { photo ->
        Dialog(onDismissRequest = { previewPhoto = null }) {
            Surface {
                Column {
                    AsyncImage(photo, contentDescription = "历史照片", modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp), contentScale = ContentScale.Fit)
                    TextButton(onClick = { previewPhoto = null }) { Text("关闭照片") }
                }
            }
        }
    }
}
