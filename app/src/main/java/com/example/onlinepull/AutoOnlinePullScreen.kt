package com.example.onlinepull

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.UploadTask
import com.example.ui.RemoteSyncViewModel
import com.example.ui.StockViewModel

/** Automatic remote sync surface; existing OnlinePullScreen remains available for diagnostics. */
@Composable
fun AutoOnlinePullScreen(
    stockViewModel: StockViewModel,
    onClose: () -> Unit,
    syncViewModel: RemoteSyncViewModel = viewModel()
) {
    val context = LocalContext.current
    val projects by syncViewModel.projects.collectAsStateWithLifecycle()
    val busy by syncViewModel.busy.collectAsStateWithLifecycle()
    val progress by syncViewModel.progress.collectAsStateWithLifecycle()
    val error by syncViewModel.error.collectAsStateWithLifecycle()
    val report by syncViewModel.report.collectAsStateWithLifecycle()
    val uploadMessage by syncViewModel.uploadMessage.collectAsStateWithLifecycle()
    val uploadTasks by syncViewModel.uploadTasks.collectAsStateWithLifecycle()
    var stage by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<RemoteProjectSummary?>(null) }
    var pendingUpload by remember { mutableStateOf<UploadTask?>(null) }
    var batchConfirm by remember { mutableStateOf(false) }
    val webView = remember { loginWebView(context) }

    LaunchedEffect(Unit) { webView.loadUrl("https://ty.zhrdc.net/") }
    LaunchedEffect(report) { if (report != null) stage = 2 }
    BackHandler {
        when (stage) {
            0 -> onClose()
            1 -> stage = 0
            else -> stage = 1
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("线上同步", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Text("关闭") }
        }
        when (stage) {
            0 -> {
                Text("请在评估系统完成登录，登录后加载项目列表。", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush(); webView.loadUrl("https://ty.zhrdc.net/") }) { Text("重新登录") }
                    Button(onClick = { syncViewModel.loadProjects(); stage = 1 }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        if (busy) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.padding(3.dp)); Text("加载项目列表")
                    }
                }
            }
            1 -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("选择评估项目（将自动扫描全部公司和资产基础法科目）", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { syncViewModel.loadProjects() }, enabled = !busy) { Icon(Icons.Default.Refresh, null) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(projects, key = { it.id }) { project ->
                        Card(Modifier.fillMaxWidth().clickable { selected = project }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (selected?.id == project.id) Icons.Default.CheckCircle else Icons.Default.CloudDownload, null)
                                Spacer(Modifier.padding(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                                    if (project.code.isNotBlank()) Text(project.code, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Button(onClick = { selected?.let { syncViewModel.sync(it) } }, enabled = selected != null && !busy, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(50.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.CloudDownload, null)
                    Spacer(Modifier.padding(4.dp)); Text(if (busy) progress.ifBlank { "同步中…" } else "自动同步应盘资产")
                }
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        report?.let {
                            Text("同步完成", style = MaterialTheme.typography.headlineSmall)
                            Text("公司 ${it.companies} · 科目 ${it.subjects} · 远端应盘 ${it.remoteRows}")
                            Text("新增 ${it.imported} · 更新 ${it.updated} · 失效 ${it.inactive} · 冲突 ${it.conflicts} · 待配置 ${it.remoteUnconfigured}")
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        uploadMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    }
                    report?.let {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("PDF 上传队列", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = { batchConfirm = true }, enabled = !busy && uploadTasks.any { it.status == "waiting" || it.status == "failed" || it.status == "session_expired" }) { Text("批量上传") }
                            }
                        }
                        items(uploadTasks, key = { it.stableKey }) { task -> UploadTaskRow(task) { pendingUpload = task } }
                    }
                    item {
                        Button(onClick = { report?.localProjectId?.let { stockViewModel.selectProject(it); onClose() } }, enabled = report != null, modifier = Modifier.fillMaxWidth()) {
                            Text("进入本地盘点项目")
                        }
                    }
                }
            }
        }
    pendingUpload?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingUpload = null },
            title = { Text("确认上传盘点索引") },
            text = { Text("将上传 " + task.fileName + "。仅替换本 App 管理的同名附件；确认继续？") },
            confirmButton = {
                Button(onClick = { pendingUpload = null; syncViewModel.upload(task.stableKey) }) { Text("确认上传") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingUpload = null }) { Text("取消") }
            }
        )
    }

    if (batchConfirm) {
        AlertDialog(
            onDismissRequest = { batchConfirm = false },
            title = { Text("确认批量上传") },
            text = { Text("将按队列上传所有可重试 PDF。仅替换本 App 管理的同名附件；确认继续？") },
            confirmButton = {
                Button(onClick = { batchConfirm = false; syncViewModel.uploadAll() }) { Text("确认上传") }
            },
            dismissButton = {
                OutlinedButton(onClick = { batchConfirm = false }) { Text("取消") }
            }
        )
    }

    }
}

@Composable
private fun UploadTaskRow(task: UploadTask, onUpload: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.fileName, style = MaterialTheme.typography.bodyMedium)
                Text(task.lastError ?: task.status, style = MaterialTheme.typography.bodySmall)
            }
            if (task.status != "success" && task.status != "remote_unconfigured") {
                IconButton(onClick = onUpload) { Icon(Icons.Default.CloudUpload, "上传") }
            }
        }
    }
}

private fun loginWebView(context: Context): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.setSupportMultipleWindows(false)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/151.0 Safari/537.36"
    CookieManager.getInstance().setAcceptCookie(true)
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            val host = uri.host.orEmpty()
            if (host == "zhrdc.net" || host.endsWith(".zhrdc.net")) {
                if (uri.scheme == "https") return false
                if (uri.scheme == "http") {
                    view?.loadUrl(uri.buildUpon().scheme("https").build().toString())
                }
            }
            return true
        }
    }
}
