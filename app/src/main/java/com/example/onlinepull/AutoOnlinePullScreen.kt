package com.example.onlinepull

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.RemoteSyncViewModel
import com.example.ui.StockViewModel

@Composable
fun AutoOnlinePullScreen(
    stockViewModel: StockViewModel,
    onClose: () -> Unit,
    syncViewModel: RemoteSyncViewModel = viewModel()
) {
    val projects by syncViewModel.projects.collectAsStateWithLifecycle()
    val loaded by syncViewModel.projectsLoaded.collectAsStateWithLifecycle()
    val busy by syncViewModel.busy.collectAsStateWithLifecycle()
    val progress by syncViewModel.progress.collectAsStateWithLifecycle()
    val error by syncViewModel.error.collectAsStateWithLifecycle()
    val report by syncViewModel.report.collectAsStateWithLifecycle()
    val hasToken by syncViewModel.hasToken.collectAsStateWithLifecycle()
    val connection by syncViewModel.connectionStatus.collectAsStateWithLifecycle()
    val credentialError by syncViewModel.credentialError.collectAsStateWithLifecycle()
    val revision by syncViewModel.credentialRevision.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(false) }
    // Deliberately not rememberSaveable: plaintext never enters activity saved state.
    var credential by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var showResult by remember { mutableStateOf(false) }
    LaunchedEffect(revision) {
        credential = ""
        selectedId = null
        if (revision > 0 && hasToken) settings = false
    }
    LaunchedEffect(report) { showResult = report != null }
    BackHandler {
        credential = ""
        when {
            settings -> settings = false
            showResult -> showResult = false
            else -> onClose()
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (settings || !hasToken) "连接设置" else "线上同步", style = MaterialTheme.typography.titleLarge)
                    Text(connection, style = MaterialTheme.typography.bodySmall)
                }
                if (hasToken) IconButton(onClick = { settings = !settings; credential = "" }, enabled = !busy) {
                    Icon(Icons.Default.Settings, contentDescription = "连接设置")
                }
                TextButton(onClick = { credential = ""; onClose() }) { Text("关闭") }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            settings || !hasToken -> {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("连接公司的项目资料", style = MaterialTheme.typography.headlineSmall)
                        Text("粘贴 Token 或完整的 MCP 配置。验证成功后即可选择项目，同步需要盘点的资产。", style = MaterialTheme.typography.bodyMedium)
                    }
                    item {
                        OutlinedTextField(
                            value = credential, onValueChange = { credential = it },
                            label = { Text(if (hasToken) "新 Token 或 MCP 配置" else "Token 或 MCP 配置") },
                            modifier = Modifier.fillMaxWidth().testTag("mcp_token_input"),
                            enabled = !busy, minLines = 3, maxLines = 5,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                        )
                        Text("凭据仅加密保存在此设备，不会写入导出文件。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    credentialError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                    item {
                        Button(onClick = { syncViewModel.saveCredential(credential) }, enabled = !busy && credential.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("mcp_save_token")) {
                            Text(if (busy) "正在验证…" else "验证并保存")
                        }
                        if (hasToken) {
                            TextButton(onClick = { syncViewModel.removeCredential(); credential = "" }, enabled = !busy) { Text("移除凭据") }
                            OutlinedButton(onClick = { settings = false; credential = "" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("返回项目列表") }
                        }
                    }
                    item {
                        HorizontalDivider()
                        Text("现场可离线使用", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                        Text("Token 过期或移除后，已下载的清单、照片和 PDF 仍保留，可继续盘点与导出。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            showResult && report != null -> {
                val result = report!!
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Text("盘点清单已同步", style = MaterialTheme.typography.headlineSmall)
                        Text("已核验 ${result.companies} 家公司、${result.subjects} 个公司科目，共 ${result.remoteRows} 条应盘资产。")
                    }
                    item {
                        Text("新增 ${result.imported} · 更新 ${result.updated} · 转入历史 ${result.inactive}", style = MaterialTheme.typography.titleMedium)
                        if (result.conflicts > 0) Text("有 ${result.conflicts} 条记录需要核对，请在项目的历史与核对页查看。", color = MaterialTheme.colorScheme.error)
                        Text("已有照片和 PDF 已保留。", modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        Button(onClick = { stockViewModel.selectProject(result.localProjectId); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("进入项目盘点") }
                        OutlinedButton(onClick = { showResult = false }, modifier = Modifier.fillMaxWidth()) { Text("同步其他项目") }
                    }
                }
            }
            else -> {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("选择评估项目", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { syncViewModel.loadProjects(); selectedId = null }, enabled = !busy) {
                            Text(if (loaded) "刷新项目" else "加载项目")
                        }
                    }
                    Text("手动同步所选项目的全部公司和资产科目。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                        label = { Text("搜索项目名称或编号") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                    if (busy && progress.isNotBlank()) Text(progress, modifier = Modifier.padding(top = 12.dp))
                    if (!loaded && !busy && error == null) Text("点击“加载项目”验证当前凭据并读取项目列表。", modifier = Modifier.padding(top = 12.dp))
                    if (loaded && projects.isEmpty()) Text("当前凭据没有可访问项目，已有本地资料仍保留。", modifier = Modifier.padding(top = 12.dp))
                }
                val filtered = projects.filter { search.isBlank() || it.name.contains(search, true) || it.code.contains(search, true) }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { project ->
                        Surface(shape = RoundedCornerShape(12.dp),
                            color = if (selectedId == project.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !busy && loaded) { selectedId = project.id }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                                    Text(project.code.ifBlank { project.id }, style = MaterialTheme.typography.bodySmall)
                                }
                                if (selectedId == project.id) Icon(Icons.Default.CheckCircle, contentDescription = "已选中")
                            }
                        }
                    }
                }
                Button(onClick = { projects.firstOrNull { it.id == selectedId }?.let(syncViewModel::sync) },
                    enabled = !busy && loaded && projects.any { it.id == selectedId },
                    modifier = Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 50.dp).testTag("remote_sync_button")) {
                    Icon(Icons.Default.CloudDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "正在同步…" else "同步所选项目")
                }
            }
        }
    }
}
