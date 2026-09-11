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
    val blAddress by viewModel.watermarkBlAddress.collectAsStateWithLifecycle()
    val blLat by viewModel.watermarkBlLat.collectAsStateWithLifecycle()
    val blLng by viewModel.watermarkBlLng.collectAsStateWithLifecycle()
    val isWatermarking by viewModel.isWatermarking.collectAsStateWithLifecycle()
    val stockItems by viewModel.stockItems.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "专属水印相机参数配置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "当前项目: ${activeProject?.name ?: "默认项目"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回主页"
                        )
                    }
                },
                actions = {
                    if (isWatermarking) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = {
                            // Force-trigger refresh/validation of PDF cache on this project
                            viewModel.setWatermarkEnabled(watermarkEnabled)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "重构PDF缓存",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))

                // Card 1: Main Toggle
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "启用本项专属自动水印",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "实时开关：关闭自动转换为无水印纯净版PDF，开启自动附加物理实勘定位",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Switch(
                                checked = watermarkEnabled,
                                onCheckedChange = { viewModel.setWatermarkEnabled(it) },
                                modifier = Modifier.testTag("full_page_watermark_main_switch").scale(0.9f)
                            )
                        }
                    }
                }
            }

            if (watermarkEnabled) {
                // Widget 2: Top-right sequence watermarks
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "右上角分类流水号标签",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "附加红色独立实勘流水标签",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "根据台账前缀与设备序列自动生成类似于 [C4-6-4-0001] 的红色序列贴纸",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Switch(
                                    checked = watermarkTrEnabled,
                                    onCheckedChange = { viewModel.updateWatermarkTrSetting(it) },
                                    modifier = Modifier.testTag("tr_setting_switch").scale(0.85f)
                                )
                            }

                            if (watermarkTrEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "📋 资产分类编号前缀对应表 (可编辑字段):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                val categories = stockItems.map { it.category }.distinct().filter { it.isNotEmpty() }
                                if (categories.isEmpty()) {
                                    Text(
                                        text = "当前清单中暂无资产分类。请先导入资产台账。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    categories.forEach { cat ->
                                        var prefixValue by remember { mutableStateOf(viewModel.getCategoryPrefix(cat)) }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = cat,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = prefixValue,
                                                onValueChange = { newVal ->
                                                    prefixValue = newVal
                                                    viewModel.saveCategoryPrefix(cat, newVal)
                                                },
                                                placeholder = { Text("例如 C-1-1", fontSize = 11.sp) },
                                                singleLine = true,
                                                textStyle = TextStyle(fontSize = 12.sp),
                                                modifier = Modifier
                                                    .width(130.dp)
                                                    .height(46.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Widget 3: Bottom-left coordinates and references watermarks
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "左下角相机实地定位水印",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "附加硬件环境实拍参考水印",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "展示高精度时间刻度戳与经纬度参考背板",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Switch(
                                    checked = blEnabled,
                                    onCheckedChange = { viewModel.updateWatermarkBlSettings(enabled = it) },
                                    modifier = Modifier.testTag("bl_setting_switch").scale(0.85f)
                                )
                            }

                            if (blEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "选择要显示的水印字段内容:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Perfect Grid alignment using equal weights
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = showDate,
                                            onCheckedChange = { viewModel.updateWatermarkBlSettings(showDate = it) },
                                            modifier = Modifier.testTag("chk_detail_date").scale(0.85f)
                                        )
                                        Text("拍摄日期", fontSize = 12.sp)
                                    }
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = showTime,
                                            onCheckedChange = { viewModel.updateWatermarkBlSettings(showTime = it) },
                                            modifier = Modifier.testTag("chk_detail_time").scale(0.85f)
                                        )
                                        Text("拍摄时刻", fontSize = 12.sp)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = showGps,
                                            onCheckedChange = { viewModel.updateWatermarkBlSettings(showGps = it) },
                                            modifier = Modifier.testTag("chk_detail_gps").scale(0.85f)
                                        )
                                        Text("传感器GPS经纬度", fontSize = 12.sp)
                                    }
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = showAddress,
                                            onCheckedChange = { viewModel.updateWatermarkBlSettings(showAddress = it) },
                                            modifier = Modifier.testTag("chk_detail_address").scale(0.85f)
                                        )
                                        Text("物理存放参考位置说明", fontSize = 12.sp)
                                    }
                                }

                                // Informational Shield Box
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "物理数据自动采集保障声明",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "为满足金融行业严格的现场实勘审计和存证合规要求，系统的拍摄日期时间、经纬度坐标与物理位置说明均将全自动、非对称解密地直接从设备硬件GPS模块 and 照片原始物理元数据流（EXIF）中采集提取，排除任何人性的填写漏洞和数据篡改空间。",
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Widget 4: Dynamic preview mockup render
                item {
                    Text(
                        text = "🔍 专属水印效果实时预览",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                color = Color(0xFF1E293B),
                                size = size
                            )
                            val gridColor = Color(0xFF334155)
                            val spacingValue = 30f
                            var offsetValue = 0f
                            while (offsetValue < size.width + size.height) {
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(offsetValue, 0f),
                                    end = androidx.compose.ui.geometry.Offset(offsetValue - size.height, size.height),
                                    strokeWidth = 2f
                                )
                                offsetValue += spacingValue
                            }
                        }

                        // Top-Right Sticker Preview
                        if (watermarkTrEnabled) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Red, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                val sampleCategory = stockItems.firstOrNull()?.category ?: "默认分类"
                                val prefix = viewModel.getCategoryPrefix(sampleCategory).ifEmpty { "C-1" }
                                Text(
                                    text = "$prefix-0001",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Bottom-Left Canvas Overlay
                        if (blEnabled) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                    .padding(6.dp)
                            ) {
                                if (showDate) {
                                    Text(
                                        text = "拍摄日期：2026年06月01日",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 1f))
                                    )
                                }
                                if (showTime) {
                                    Text(
                                        text = "时间：14:38:58",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 1f))
                                    )
                                }
                                if (showGps) {
                                    val dLat = blLat.toDoubleOrNull()
                                    val dLng = blLng.toDoubleOrNull()
                                    val locationText = if (dLat != null && dLng != null && dLat != 0.0 && dLng != 0.0) {
                                        String.format(java.util.Locale.CHINA, "经度：%.2f  纬度：%.2f", dLng, dLat)
                                    } else {
                                        "经纬度：未获取定位"
                                    }
                                    Text(
                                        text = locationText,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 1f))
                                    )
                                }
                                if (showAddress) {
                                    Text(
                                        text = "位置：${blAddress.ifBlank { "未获取定位" }}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 1f)),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "专属水印功能已关闭",
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "关闭水印后生成的交付版本 PDF 将恢复为无水印纯面台账文档",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
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
                    Pair("存放位置", item.location),
                    Pair("设备 UID", item.uid)
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
