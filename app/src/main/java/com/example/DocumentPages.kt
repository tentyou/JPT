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

// ============================================
// PROJECT-SPECIFIC WATERMARK CONFIGURATION SURFACE
// ============================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkSettingsPage(
    viewModel: com.example.ui.StockViewModel,
    onBackClick: () -> Unit
) {
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val watermarkEnabled by viewModel.watermarkEnabled.collectAsStateWithLifecycle()
    val watermarkTrEnabled by viewModel.watermarkTrEnabled.collectAsStateWithLifecycle()
    val blEnabled by viewModel.watermarkBlEnabled.collectAsStateWithLifecycle()
    val showDate by viewModel.watermarkBlShowDate.collectAsStateWithLifecycle()
    val showTime by viewModel.watermarkBlShowTime.collectAsStateWithLifecycle()
    val showGps by viewModel.watermarkBlShowGps.collectAsStateWithLifecycle()
    val showAddress by viewModel.watermarkBlShowAddress.collectAsStateWithLifecycle()
    val isWatermarking by viewModel.isWatermarking.collectAsStateWithLifecycle()
    val stockItems by viewModel.stockItems.collectAsStateWithLifecycle()

    LaunchedEffect(watermarkEnabled, blEnabled) {
        if (watermarkEnabled && !blEnabled) viewModel.updateWatermarkBlSettings(enabled = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("图片水印设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("当前项目：${activeProject?.name ?: "默认项目"}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isWatermarking) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("启用水印", fontWeight = FontWeight.Bold)
                            Text("水印关闭后导出 PDF 将均不带有水印。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = watermarkEnabled,
                            onCheckedChange = {
                                viewModel.setWatermarkEnabled(it)
                                viewModel.updateWatermarkBlSettings(enabled = it)
                            },
                            modifier = Modifier.testTag("full_page_watermark_main_switch")
                        )
                    }
                }
            }

            if (watermarkEnabled) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("自动索引编号", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = watermarkTrEnabled,
                                    onCheckedChange = viewModel::updateWatermarkTrSetting,
                                    modifier = Modifier.testTag("tr_setting_switch")
                                )
                            }
                            if (watermarkTrEnabled) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Text("索引前缀设置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                val categories = stockItems.map { it.category }.distinct().filter { it.isNotEmpty() }
                                if (categories.isEmpty()) {
                                    Text("请先导入资产清单。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    categories.forEach { category ->
                                        var prefix by remember(category) { mutableStateOf(viewModel.getCategoryPrefix(category)) }
                                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(category, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            OutlinedTextField(
                                                value = prefix,
                                                onValueChange = { prefix = it; viewModel.saveCategoryPrefix(category, it) },
                                                singleLine = true,
                                                modifier = Modifier.width(132.dp),
                                                textStyle = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("水印相机", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    viewModel.updateWatermarkBlSettings(showDate = true, showTime = true, showGps = true, showAddress = true)
                                }) { Text("全选") }
                                TextButton(onClick = {
                                    viewModel.updateWatermarkBlSettings(showDate = false, showTime = false, showGps = false, showAddress = false)
                                }) { Text("取消全选") }
                            }
                            WatermarkOptionRow(
                                firstLabel = "拍摄日期", firstChecked = showDate, firstTag = "chk_detail_date", firstChange = { viewModel.updateWatermarkBlSettings(showDate = it) },
                                secondLabel = "拍摄时间", secondChecked = showTime, secondTag = "chk_detail_time", secondChange = { viewModel.updateWatermarkBlSettings(showTime = it) }
                            )
                            WatermarkOptionRow(
                                firstLabel = "经纬度", firstChecked = showGps, firstTag = "chk_detail_gps", firstChange = { viewModel.updateWatermarkBlSettings(showGps = it) },
                                secondLabel = "位置", secondChecked = showAddress, secondTag = "chk_detail_address", secondChange = { viewModel.updateWatermarkBlSettings(showAddress = it) }
                            )
                        }
                    }
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("水印功能已关闭", fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text("水印关闭后导出 PDF 将均不带有水印。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun WatermarkOptionRow(
    firstLabel: String,
    firstChecked: Boolean,
    firstTag: String,
    firstChange: (Boolean) -> Unit,
    secondLabel: String,
    secondChecked: Boolean,
    secondTag: String,
    secondChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(firstChecked, firstChange, Modifier.testTag(firstTag))
            Text(firstLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(secondChecked, secondChange, Modifier.testTag(secondTag))
            Text(secondLabel, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var bitmaps by remember(file) { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val list = mutableListOf<android.graphics.Bitmap>()
                val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(parcelFileDescriptor)
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // High quality scaling
                    val scaleFactor = 2
                    val width = page.width * scaleFactor
                    val height = page.height * scaleFactor
                    val bitmap = createBitmap(width, height)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    list.add(bitmap)
                    page.close()
                }
                renderer.close()
                parcelFileDescriptor.close()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    bitmaps = list
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    errorMessage = e.message ?: "解析PDF失败"
                }
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PDF预览: ${file.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider()

                    if (errorMessage != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (bitmaps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "渲染中, 请稍候...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Gray.copy(alpha = 0.15f)),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(bitmaps) { bitmap ->
                                Card(
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                ) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "PDF Page",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun CollapsibleMetadataSection(
    item: com.example.data.StockItem,
    remoteBinding: com.example.data.RemoteAssetBinding? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "详细信息",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = if (isExpanded) "收起" else "展开",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (isExpanded) {
            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val itemsToDisplay = listOf(
                    Pair("设备编号", item.originalCode),
                    Pair("资产分类", item.category),
                    Pair("存放位置", item.location)
                ) + listOfNotNull(
                    remoteBinding?.companyName?.takeIf { it.isNotBlank() }?.let { "所属单位" to it },
                    remoteBinding?.subjectName?.takeIf { it.isNotBlank() && it != item.category }?.let { "底稿科目" to it },
                    remoteBinding?.worksheetKey?.takeIf { it.isNotBlank() }?.let { "工作表" to it }
                )

                itemsToDisplay.forEach { (label, value) ->
                    val displayValue = value.ifEmpty { "无" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(displayValue) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (value.isNotEmpty()) {
                                            val clip = android.content.ClipData.newPlainText("metadata", value)
                                            clipboardManager.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(context, "已复制元数据: $value", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$label: ",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = displayValue,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
