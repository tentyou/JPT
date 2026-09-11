package com.example

import android.Manifest
import android.net.Uri
import androidx.compose.ui.draw.scale
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.StockItem
import com.example.onlinepull.AutoOnlinePullScreen
import com.example.ui.StockViewModel
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: StockViewModel, onOpenOnlinePull: () -> Unit = {}) {
    val context = LocalContext.current
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val activeProjectId by viewModel.activeProjectId.collectAsStateWithLifecycle()
    val stockItems by viewModel.stockItems.collectAsStateWithLifecycle()
    val remoteLink by viewModel.remoteProjectLink.collectAsStateWithLifecycle()
    val remoteBindings by viewModel.remoteBindings.collectAsStateWithLifecycle()
    var showRemoteHistory by remember { mutableStateOf(false) }
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val isWatermarking by viewModel.isWatermarking.collectAsStateWithLifecycle()

    val currentProject = allProjects.find { it.id == activeProjectId }
    val currentProjectName = currentProject?.name ?: "默认项目"

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showAddProjectDialog by remember { mutableStateOf(false) }
    var showDeleteProjectDialog by remember { mutableStateOf<com.example.data.Project?>(null) }
    var showRenameProjectDialog by remember { mutableStateOf<com.example.data.Project?>(null) }
    var showWatermarkConfirmDialog by remember { mutableStateOf<Boolean?>(null) }
    var showEditMetaDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showWatermarkSettingsPage by remember { mutableStateOf(false) }

    // Drawer state configuration
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Tab Index: 0 = 待盘点设备 (Only shouldCheck == true), 1 = 台账管理 (All items with shouldCheck toggles)
    var activeTab by remember { mutableStateOf(0) }

    // Document Import Launcher supporting CSV & XLSX
    val documentImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    // CSV Template Export Launcher
    val csvTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val csvContent = "\uFEFF序号,设备编号,设备名称,资产分类,规格型号,生产厂家,计量单位,数量,存放位置,购置日期,启用日期,账面原值,账面净值,是否盘点,备注,UUID\n"
                    outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "盘点表模板保存成功！", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "保存模板失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ZIP Export Launcher
    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToZip(uri) { success ->
                if (success) {
                    Toast.makeText(context, "归档 ZIP 压缩包已成功保存！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "导出 ZIP 失败，请确保至少有一项数据且已被拍摄！", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Display background PDF compilation notification
    val backgroundMessage by viewModel.backgroundPdfMessage.collectAsStateWithLifecycle()
    if (backgroundMessage != null) {
        androidx.compose.runtime.LaunchedEffect(backgroundMessage) {
            Toast.makeText(context, backgroundMessage ?: "PDF 合并生成成功！", Toast.LENGTH_LONG).show()
            viewModel.dismissBackgroundPdfMessage()
        }
    }

    // Safe direct file import options dialog
    if (pendingImportUri != null) {
        var selectedImportProjId by remember(allProjects, activeProjectId) { mutableStateOf(activeProjectId) }
        var replaceMode by remember { mutableStateOf(false) } // false = 追加, true = 替换

        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                    Text("底账资产数据导入配置 Mapping", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("1. 请选择导入关联的目标分类项目:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        allProjects.forEach { proj ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedImportProjId == proj.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedImportProjId = proj.id }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    RadioButton(
                                        selected = (selectedImportProjId == proj.id),
                                        onClick = { selectedImportProjId = proj.id }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = proj.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selectedImportProjId == proj.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Text("2. 请选择数据注入并解析的模式:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (!replaceMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { replaceMode = false }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                RadioButton(selected = !replaceMode, onClick = { replaceMode = false })
                                Text("追加单据", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (replaceMode) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { replaceMode = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                RadioButton(selected = replaceMode, onClick = { replaceMode = true })
                                Text("覆写替换", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (replaceMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "⚠️ 覆写将替换该特定项目的当前清单！在覆盖替换后，不再新资产列表中的旧照片与 PDF 生成清册将被自动离线物理删除。请建议必要时做好备份，虽然非强制要求。",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingImportUri!!
                        viewModel.importFile(uri, selectedImportProjId, replaceMode) { success ->
                            if (success) {
                                Toast.makeText(context, "数据导入映射匹配成功！", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "直接导入失败，请核实文件列名与是否拥有布尔点检列", Toast.LENGTH_LONG).show()
                            }
                        }
                        pendingImportUri = null
                    }
                ) {
                    Text("执行映射导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Modal Drawer wrapper
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .padding(16.dp)
                ) {
                    // Drawer Brand Head
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "项目列表",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Wi-Fi Local File Transfer Portal embedded inside Drawer
                    val wifiEnabled by viewModel.wifiTransferEnabled.collectAsStateWithLifecycle()
                    val ipAddress by viewModel.deviceIpAddress.collectAsStateWithLifecycle()
                    val wifiPort by viewModel.wifiPort.collectAsStateWithLifecycle()
                    val wifiPairingToken by viewModel.wifiPairingToken.collectAsStateWithLifecycle()

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (wifiEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = if (wifiEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "无线传单 HTTP 传送",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (wifiEnabled) "服务器开启中..." else "电脑输入网址传送导入",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Switch(
                                    checked = wifiEnabled,
                                    onCheckedChange = { viewModel.toggleWifiTransfer(it, wifiPort) },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }

                            if (wifiEnabled && ipAddress != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "IP地址:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            text = "http://$ipAddress:$wifiPort/?token=$wifiPairingToken",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                // Port input in Sidebar
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("传送端口:", fontSize = 11.sp, color = Color.Gray)
                                    var portInput by remember { mutableStateOf(wifiPort.toString()) }
                                    OutlinedTextField(
                                        value = portInput,
                                        onValueChange = { input ->
                                            val filtered = input.filter { it.isDigit() }
                                            portInput = filtered
                                            filtered.toIntOrNull()?.let { viewModel.updateWifiPort(it) }
                                        },
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier
                                            .width(90.dp),
                                        singleLine = true,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(
                                        onClick = { showWatermarkSettingsPage = true },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("类别前缀", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Project selection row headers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "分类项目组列表",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        IconButton(
                            onClick = { showAddProjectDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "新建项目分类",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Side list of all projects
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allProjects.forEach { proj ->
                            val isSelected = (proj.id == activeProjectId)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectProject(proj.id)
                                            coroutineScope.launch { drawerState.close() }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = proj.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Rename project option
                                    IconButton(
                                        onClick = { showRenameProjectDialog = proj },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "重命名项目分类",
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Deletion warning options
                                    IconButton(
                                        onClick = { showDeleteProjectDialog = proj },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除该分类项目",
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "物理数据独立存储隔离机理",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                val wifiEnabled by viewModel.wifiTransferEnabled.collectAsStateWithLifecycle()
                val ipAddress by viewModel.deviceIpAddress.collectAsStateWithLifecycle()
                val wifiPort by viewModel.wifiPort.collectAsStateWithLifecycle()
                val wifiPairingToken by viewModel.wifiPairingToken.collectAsStateWithLifecycle()

                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "打开项目菜单"
                            )
                        }
                    },
                    title = {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = currentProjectName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .basicMarquee(iterations = Int.MAX_VALUE)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // WiFi active dynamic indicator pill on topBar (stacked under title name to avoid changing bar width)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (wifiEnabled) Color(0xFFE8F5E9) else Color(0xFFECEFF1),
                                modifier = Modifier
                                    .clickable {
                                        coroutineScope.launch {
                                            val targetState = !wifiEnabled
                                            viewModel.toggleWifiTransfer(targetState, wifiPort)
                                            Toast.makeText(
                                                context,
                                                "WiFi已${if (targetState) "点击开启中" else "点击关闭"}，3秒后提示端口...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            delay(3000)
                                            if (targetState) {
                                                val freshIp = viewModel.deviceIpAddress.value ?: "127.0.0.1"
                                                Toast.makeText(
                                                    context,
                                                    "WiFi传送端口地址: http://${freshIp}:${wifiPort}/?token=${viewModel.wifiPairingToken.value ?: ""}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "WiFi传送服务已关闭",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (wifiEnabled) Color(0xFF4CAF50) else Color.Gray)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (wifiEnabled) "WiFi传输: 开 (${ipAddress ?: "获取中"}:${wifiPort})" else "WiFi传输: 关",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (wifiEnabled) Color(0xFF2E7D32) else Color.DarkGray
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.startTutorial() },
                            modifier = Modifier.testTag("help_tutorial_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "新手指引",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "清空数据",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val showTutorial by viewModel.showTutorial.collectAsStateWithLifecycle()
                val watermarkEnabled by viewModel.watermarkEnabled.collectAsStateWithLifecycle()

                if (allProjects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "尚未创设任何分类项目",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "系统提示：请新建一个分类项目，或在无线端或左侧菜单栏添加新项目，以启用点检盘点功能。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { showAddProjectDialog = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("立即新建分类项目", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                    if (showTutorial) {
                        item {
                            TutorialGuideCard(
                                onLoadSample = { viewModel.importSampleData() },
                                onCloseTutorial = { viewModel.completeTutorial() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                                         item {
                        TenkenDashboardHeader(
                            projectName = currentProjectName,
                            totalCount = stockItems.size,
                            pendingCount = stockItems.count { it.shouldCheck && it.pdfStatus != "已生成" },
                            photographedCount = stockItems.count { it.shouldCheck && it.pdfStatus == "已生成" }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

// Stats Category Card dynamically mapped
                    item {
                        StatsCategoryCard(
                            onImportClick = { documentImportLauncher.launch(arrayOf("*/*")) },
                            onTemplateClick = {
                                csvTemplateLauncher.launch("盘点表模板.csv")
                            },
                            onOnlinePullClick = onOpenOnlinePull
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Project Assessment Metadata Setup Card
                    item {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Style,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "设置信息",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    val baseDateVal = currentProject?.baseDate ?: "未设定"
                                    val companyVal = currentProject?.companyName ?: "未设定"
                                    val rTypeVal = currentProject?.reportType ?: "评估报告"

                                    Text(
                                        text = "📅 评估基准日: $baseDateVal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                    Text(
                                        text = "🏢 持有单位: $companyVal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "📝 报告类型: $rTypeVal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }

                                Button(
                                    onClick = { showEditMetaDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .testTag("edit_meta_btn")
                                        .padding(start = 12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "修改",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("修改", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Camera Watermarks Configuration & Live Preview Card
                    item {
                        val watermarkEnabled by viewModel.watermarkEnabled.collectAsStateWithLifecycle()
                        val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // NEW CARD STRUCTURE INTRODUCED HERE
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoCamera,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "图片水印",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    val isWatermarking by viewModel.isWatermarking.collectAsStateWithLifecycle()
                                    if (isWatermarking) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(10.dp),
                                                strokeWidth = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("正在重构PDF...", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "为「${activeProject?.name ?: "默认项目"}」启用自动水印",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "包含右上角分类序号标签与左下角实勘定位存证，关闭则转换为无水印纯净PDF",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    Switch(
                                        checked = watermarkEnabled,
                                        onCheckedChange = { targetState ->
                                            showWatermarkConfirmDialog = targetState
                                        },
                                        modifier = Modifier.testTag("watermark_switch").scale(0.85f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val subCountText = remember(watermarkEnabled) {
                                            if (watermarkEnabled) "右上角流水 + 左下角物理元数据" else "水印功能已关闭"
                                        }
                                        Text(
                                            text = subCountText,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
                                        onClick = { showWatermarkSettingsPage = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("detailed_watermark_settings_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("详细设置", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }


                    if (remoteLink != null) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                remoteLink?.lastSyncError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("上次完整同步：" + (remoteLink?.lastSyncAt?.let {
                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(it))
                                } ?: "尚未完成"), style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { showRemoteHistory = true }) {
                                    Text("历史与核对（${remoteBindings.count { !it.active || it.syncState == "conflict" }}）")
                                }
                            }
                        }
                    }
                    val mainCheckList = stockItems.filter { it.shouldCheck }
                    val checkedCount = mainCheckList.size
                    val totalCount = stockItems.size

                    // Tab switching rows: 0 = 待盘点设备(filtered shouldCheck == true), 1 = 全量台账(show checkboxes)
                    item {
                        TabRow(
                            selectedTabIndex = activeTab,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            containerColor = Color.Transparent,
                            divider = {}
                        ) {
                            Tab(
                                selected = (activeTab == 0),
                                onClick = { activeTab = 0 }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("待盘点清单", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            Tab(
                                selected = (activeTab == 1),
                                onClick = { activeTab = 1 }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("台账预览 (${checkedCount}项/${totalCount}项)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (activeTab == 1) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "台账预览 (${checkedCount}项/${totalCount}项)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = if (remoteLink != null) "盘点范围由线上同步决定" else "勾选即纳入盘点",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (stockItems.isEmpty()) {
                            item {
                                EmptyStateView(
                                    onImportClick = { documentImportLauncher.launch(arrayOf("*/*")) },
                                    onSampleClick = { viewModel.importSampleData() }
                                )
                            }
                        } else {
                            items(stockItems, key = { it.uid }) { item ->
                                val seq = try {
                                    if (item.originalRowJson.isNotEmpty()) {
                                        val m = Regex("\"([^\"]*)\"").find(item.originalRowJson)
                                        m?.groupValues?.get(1) ?: "1"
                                    } else "1"
                                } catch (e: Exception) { "1" }
                                val formattedName = "${item.name}（${item.category}-$seq）"

                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = item.shouldCheck,
                                            enabled = remoteBindings.none { it.stockUid == item.uid },
                                            onCheckedChange = { isChecked ->
                                                viewModel.updateItem(item.copy(shouldCheck = isChecked))
                                            },
                                            modifier = Modifier.testTag("checkbox_${item.uid}")
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = formattedName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            CollapsibleMetadataSection(item = item, modifier = Modifier.padding(top = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "需要盘点的资产",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "共计 ${checkedCount} 项需盘点设备/台账共 ${totalCount} 项",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (mainCheckList.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.AssignmentLate, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "本分类项目当前暂无激活点检任务",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = "请切换至「全部台账管理」选项卡勾选要点检盘点的设备，或导入外部台账。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(mainCheckList, key = { it.uid }) { item ->
                                StockItemRow(
                                    item = item,
                                    onCameraClick = { viewModel.startPhotoCapture(item) },
                                    onPdfClick = { viewModel.manualGeneratePdf(item) },
                                    onDeletePdfClick = { viewModel.deleteItemPdf(item) }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }

                // Export Actions Sticky Bar (Floating Bottom Drawer Style)
                val activeListForExport = stockItems.filter { it.shouldCheck }
                if (activeListForExport.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        tonalElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val isBaseDateEmpty = currentProject?.baseDate?.trim()?.isEmpty() ?: true
                                    val isCompanyNameEmpty = currentProject?.companyName?.trim()?.isEmpty() ?: true
                                    if (isBaseDateEmpty || isCompanyNameEmpty) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "⚠️ 导出失败：评估基准日和持有单位不能为空，请先在“设置信息”中填写！",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        val sdf = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault())
                                        val timestampStr = sdf.format(java.util.Date())
                                        val proposedZipName = "${currentProjectName}-盘点表-${timestampStr}.zip"
                                        zipExportLauncher.launch(proposedZipName)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("export_zip_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = "ZIP",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "导出项目文件",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
                }

                // Global Loading Indicator for Streams
                if (isImporting || isExporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (isImporting) "正在读取导入盘点清单并初始化本地 SQLite 库..." else "正在归并生成各资产 PDF 并压缩打包 ZIP 档案...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(220.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick purge validation dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(text = "清空全部数据？") },
            text = { Text(text = "系统将删除 SQLite 中的全部盘点表，同时删除本地缓存中的所有原片及已生成的项目 PDF 档案！此动作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = "确认清空", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(text = "取消")
                }
            }
        )
    }

    // Project Assessment Metadata Setup Dialog
    if (showEditMetaDialog) {
        var baseDateState by remember { mutableStateOf(currentProject?.baseDate ?: "") }
        var companyNameState by remember { mutableStateOf(currentProject?.companyName ?: "") }
        var selectedReportTypeOption by remember {
            mutableStateOf(
                when (currentProject?.reportType) {
                    "评估报告", "咨询报告" -> currentProject.reportType
                    null, "" -> "评估报告"
                    else -> "自定义"
                }
            )
        }
        var customReportTypeState by remember {
            mutableStateOf(
                if (currentProject?.reportType == "评估报告" || currentProject?.reportType == "咨询报告" || currentProject?.reportType.isNullOrEmpty()) ""
                else currentProject?.reportType ?: ""
            )
        }

        val context = LocalContext.current
        val calendar = remember(baseDateState) {
            java.util.Calendar.getInstance().apply {
                try {
                    if (baseDateState.isNotEmpty()) {
                        val sdf = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.CHINA)
                        val date = sdf.parse(baseDateState)
                        if (date != null) {
                            time = date
                        }
                    }
                } catch (e: Exception) {
                    // Ignore, use current date
                }
            }
        }

        val datePickerDialog = remember(context, baseDateState) {
            android.app.DatePickerDialog(
                context,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val formattedDate = String.format(
                        java.util.Locale.CHINA,
                        "%d年%02d月%02d日",
                        selectedYear,
                        selectedMonth + 1,
                        selectedDay
                    )
                    baseDateState = formattedDate
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )
        }

        AlertDialog(
            onDismissRequest = { showEditMetaDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("设定设置信息", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        OutlinedTextField(
                            value = baseDateState,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("评估基准日", fontSize = 12.sp) },
                            placeholder = { Text("点击选择日期", color = Color.Gray, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "选择日期",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_input_base_date"),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { datePickerDialog.show() }
                        )
                    }

                    OutlinedTextField(
                        value = companyNameState,
                        onValueChange = { companyNameState = it },
                        label = { Text("被评估/产权持有单位名称", fontSize = 12.sp) },
                        placeholder = { Text("如：华东科技集团有限公司", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("dialog_input_company_name"),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "报告分类选项",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Column {
                        listOf("评估报告", "咨询报告", "自定义").forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { selectedReportTypeOption = option }
                                    .padding(vertical = 4.dp)
                                    .fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = (selectedReportTypeOption == option),
                                    onClick = { selectedReportTypeOption = option },
                                    modifier = Modifier.testTag("dialog_radio_$option")
                                )
                                Text(
                                    text = option,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (selectedReportTypeOption == "自定义") {
                        OutlinedTextField(
                            value = customReportTypeState,
                            onValueChange = { customReportTypeState = it },
                            label = { Text("输入自定义分类名称", fontSize = 11.sp) },
                            placeholder = { Text("如：内审点检报告", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("dialog_input_custom_report_type"),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalReportType = if (selectedReportTypeOption == "自定义") {
                            customReportTypeState.trim().ifEmpty { "自定义报告" }
                        } else {
                            selectedReportTypeOption
                        }
                        viewModel.updateProjectMeta(activeProjectId, baseDateState.trim(), companyNameState.trim(), finalReportType)
                        showEditMetaDialog = false
                    },
                    modifier = Modifier.testTag("dialog_save_meta_btn")
                ) {
                    Text("保存设定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditMetaDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Project creation Dialog
    if (showAddProjectDialog) {
        var baseDateState by remember { mutableStateOf("") }
        var companyNameState by remember { mutableStateOf("") }
        var selectedReportTypeOption by remember { mutableStateOf("评估报告") }
        var customReportTypeState by remember { mutableStateOf("") }

        val context = LocalContext.current
        val calendar = remember { java.util.Calendar.getInstance() }

        val datePickerDialog = remember(context) {
            android.app.DatePickerDialog(
                context,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val formattedDate = String.format(
                        java.util.Locale.CHINA,
                        "%d年%02d月%02d日",
                        selectedYear,
                        selectedMonth + 1,
                        selectedDay
                    )
                    baseDateState = formattedDate
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )
        }

        AlertDialog(
            onDismissRequest = { showAddProjectDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("创设新评估项目", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        OutlinedTextField(
                            value = baseDateState,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("评估基准日", fontSize = 12.sp) },
                            placeholder = { Text("点击选择日期", color = Color.Gray, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "选择日期",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { datePickerDialog.show() }
                        )
                    }

                    OutlinedTextField(
                        value = companyNameState,
                        onValueChange = { companyNameState = it },
                        label = { Text("被评估/产权持有单位名称", fontSize = 12.sp) },
                        placeholder = { Text("如：华东科技集团有限公司", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "报告分类选项",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Column {
                        listOf("评估报告", "咨询报告", "自定义").forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { selectedReportTypeOption = option }
                                    .padding(vertical = 4.dp)
                                    .fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = (selectedReportTypeOption == option),
                                    onClick = { selectedReportTypeOption = option }
                                )
                                Text(
                                    text = option,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (selectedReportTypeOption == "自定义") {
                        OutlinedTextField(
                            value = customReportTypeState,
                            onValueChange = { customReportTypeState = it },
                            label = { Text("输入自定义分类名称", fontSize = 11.sp) },
                            placeholder = { Text("如：内审点检报告", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp)
                        )
                    }

                    // Show live generated default project name
                    val digits = baseDateState.filter { it.isDigit() }
                    val defaultName = if (companyNameState.trim().isNotEmpty() && digits.isNotEmpty()) {
                        "${companyNameState.trim()}-$digits"
                    } else {
                        "（信息补齐后自动生成项目名称）"
                    }
                    Text(
                        text = "预拟项目名称: $defaultName",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val digits = baseDateState.filter { it.isDigit() }
                        if (baseDateState.isEmpty() || companyNameState.trim().isEmpty() || (selectedReportTypeOption == "自定义" && customReportTypeState.trim().isEmpty())) {
                            Toast.makeText(context, "请补齐所有必填的分类参数信息后再进行创设！", Toast.LENGTH_LONG).show()
                        } else {
                            val finalReportType = if (selectedReportTypeOption == "自定义") {
                                customReportTypeState.trim()
                            } else {
                                selectedReportTypeOption
                            }
                            val autoProjectName = "${companyNameState.trim()}-$digits"
                            viewModel.addProject(
                                name = autoProjectName,
                                baseDate = baseDateState.trim(),
                                companyName = companyNameState.trim(),
                                reportType = finalReportType
                            )
                            showAddProjectDialog = false
                        }
                    }
                ) {
                    Text("创设项目")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddProjectDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Watermark confirmation Dialog
    if (showWatermarkConfirmDialog != null) {
        val targetState = showWatermarkConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showWatermarkConfirmDialog = null },
            title = {
                Text(
                    text = if (targetState) "确认要开启照片水印吗？" else "确认要关闭照片水印吗？",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (targetState) {
                        "开启后，系统将自动对所有已生成的 PDF 照片应用右上角「前缀-编号」红色水印。该过程将重新生成您所有的 PDF 报告，请耐心等待。"
                    } else {
                        "关闭后，系统将重新生成您所有的 PDF 报告并彻底移除其中的水印标识。该过程需要短暂时间运作，请先确认。"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWatermarkConfirmDialog = null
                        viewModel.setWatermarkEnabled(targetState) { count ->
                            val msg = if (targetState) {
                                "照片水印已成功附加至 $count 个已有 PDF 文件！"
                            } else {
                                "已成功清除 $count 个 PDF 文件中的照片水印！"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.testTag("watermark_confirm_btn")
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWatermarkConfirmDialog = null },
                    modifier = Modifier.testTag("watermark_cancel_btn")
                ) {
                    Text("取消")
                }
            }
        )
    }

    // Watermarking Progress loader overlay
    if (isWatermarking) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {} // Not dismissable
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "正在处理 PDF 照片水印...",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "重新生成底册信息，请勿关闭应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Project renaming Dialog
    if (showRenameProjectDialog != null) {
        val proj = showRenameProjectDialog!!
        var editProjectName by remember(proj.id) { mutableStateOf(proj.name) }
        AlertDialog(
            onDismissRequest = { showRenameProjectDialog = null },
            title = { Text("重命名分类项目", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editProjectName,
                    onValueChange = { editProjectName = it },
                    label = { Text("项目名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_project_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editProjectName.isNotBlank()) {
                            viewModel.renameProject(proj.id, editProjectName.trim())
                            showRenameProjectDialog = null
                            Toast.makeText(context, "项目已被重命名为: ${editProjectName.trim()}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("rename_project_confirm")
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameProjectDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Project deleting warnings dialog
    if (showDeleteProjectDialog != null) {
        val projToDelete = showDeleteProjectDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteProjectDialog = null },
            modifier = Modifier.testTag("delete_project_dialog"),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 8.dp))
                    Text("项目清扫警告", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            },
            text = {
                Text(
                    text = "您正在对分类项目「${projToDelete.name}」进行彻底彻底的毁灭性清扫！\n\n警告：这将会彻底物理抹去该项目内所有的资产单据信息、关联的所有实物存证拍照及已归纳生成的 PDF 电子盘点点检单（其他不受影响）。该行为完全物理执行，绝对无法撤回！",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(projToDelete) {
                            Toast.makeText(context, "项目「${projToDelete.name}」已清空清除", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteProjectDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认清扫删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProjectDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRemoteHistory) {
        com.example.onlinepull.RemoteHistoryDialog(stockItems, remoteBindings) { showRemoteHistory = false }
    }

    if (showWatermarkSettingsPage) {
        WatermarkSettingsPage(
            viewModel = viewModel,
            onBackClick = { showWatermarkSettingsPage = false }
        )
    }
}


@Composable
private fun TenkenDashboardHeader(
    projectName: String,
    totalCount: Int,
    pendingCount: Int,
    photographedCount: Int
) {
    val completion = if (totalCount == 0) 0f else photographedCount.toFloat() / totalCount.toFloat()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TENKEN · 现场盘点工作台",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (pendingCount == 0) "应盘资产已完成，可整理成果" else "先完成待盘资产，再生成上传成果",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TenkenMetric(label = "待盘", value = pendingCount.toString(), accent = MaterialTheme.colorScheme.tertiary)
                TenkenMetric(label = "已成册", value = photographedCount.toString(), accent = MaterialTheme.colorScheme.secondary)
                TenkenMetric(label = "总资产", value = totalCount.toString(), accent = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(completion.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondary)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = (completion * 100).toInt().toString() + "% 已完成",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun RowScope.TenkenMetric(label: String, value: String, accent: Color) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
