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

class MainActivity : ComponentActivity() {
    private val viewModel: StockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.onlinepull.UploadWorkScheduler.cancelAll(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun TutorialGuideCard(
    onLoadSample: () -> Unit,
    onCloseTutorial: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("tutorial_guide_card"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "点检 · 快速上手新手指引",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(
                    onClick = onCloseTutorial,
                    modifier = Modifier.size(28.dp).testTag("close_tutorial_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭引导说明",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "欢迎使用点检系统！跟随以下4步快速体验核心工作流：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("1️⃣ ", style = MaterialTheme.typography.bodyMedium)
                    Column {
                        Text("点击下方按钮「加载示范数据」", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("或通过右上角导入您自己的台账 CSV/Excel 清单文件。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text("2️⃣ ", style = MaterialTheme.typography.bodyMedium)
                    Column {
                        Text("在下方数据列中点击「📷 相机」", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("为该项资产拍照，或直接识别资产本身的条形码、二维码。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text("3️⃣ ", style = MaterialTheme.typography.bodyMedium)
                    Column {
                        Text("完成拍照后，系统会在后台全自动编译", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("生成专业合规的资产记录 PDF，分类编号可定制对应前缀。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text("4️⃣ ", style = MaterialTheme.typography.bodyMedium)
                    Column {
                        Text("一键传送与无线导出", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("开启 Wi-Fi 传单开关，电脑端浏览器扫码/直接输入地址即可轻松拖动上传新表单！", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onLoadSample,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                        .testTag("load_sample_tutorial_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("加载示范数据", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onCloseTutorial,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("close_tutorial_button")
                ) {
                    Text("我知道了", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun StatsCategoryCard(
    onImportClick: () -> Unit,
    onTemplateClick: () -> Unit,
    onOnlinePullClick: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onImportClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("import_csv_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Attachment, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("导入盘点表", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onTemplateClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("download_template_button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("盘点表模板", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 线上作业系统拉取入口
        Button(
            onClick = onOnlinePullClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("online_pull_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("线上拉取（ty.zhrdc.net）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StockItemRow(
    item: StockItem,
    onCameraClick: () -> Unit,
    onPdfClick: () -> Unit,
    onDeletePdfClick: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stock_item_${item.uid}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (item.photoCount > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (item.photoCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Dynamic decorative icon indicating type
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (item.photoCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.photoCount > 0) Icons.Default.CameraAlt else Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = if (item.photoCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Asset texts
                val seq = try {
                    if (item.originalRowJson.isNotEmpty()) {
                        val m = Regex("\"([^\"]*)\"").find(item.originalRowJson)
                        m?.groupValues?.get(1) ?: "1"
                    } else "1"
                } catch (e: Exception) { "1" }
                val formattedName = "${item.name}（${item.category}-$seq）"

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formattedName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action panel right aligned
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick stats pill
                    if (item.photoCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE8F5E9),
                            contentColor = Color(0xFF2E7D32)
                        ) {
                            Text(
                                text = "已拍${item.photoCount}张",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Shutter button on the list right side
                    Button(
                        onClick = onCameraClick,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("snap_button_${item.uid}"),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.photoCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "拍照",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.photoCount > 0) "续拍" else "拍照",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))

            // Sub-row containing details like Category and UUID, and the PDF generation triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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

                // PDF compiling triggers
                if (item.photoCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = onPdfClick,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (item.pdfStatus == "已生成") Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("generate_pdf_${item.uid}"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = if (item.pdfStatus == "已生成") Icons.Default.PictureAsPdf else Icons.Default.Refresh,
                                contentDescription = "PDF",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (item.pdfStatus == "已生成") "合并PDF已绪" else "归并生成PDF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (item.pdfStatus == "已生成") {
                            var showPdfPreview by remember { mutableStateOf(false) }

                            TextButton(
                                onClick = { showPdfPreview = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("preview_pdf_${item.uid}"),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "预览PDF",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "预览",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (showPdfPreview) {
                                val pdfFile = File(context.filesDir, "pdfs/${item.uid}/照片.pdf")
                                if (pdfFile.exists()) {
                                    PdfPreviewDialog(
                                        file = pdfFile,
                                        onDismiss = { showPdfPreview = false }
                                    )
                                } else {
                                    Toast.makeText(context, "未找到生成的PDF预览文件", Toast.LENGTH_SHORT).show()
                                    showPdfPreview = false
                                }
                            }
                        }

                        IconButton(
                            onClick = onDeletePdfClick,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("delete_pdf_${item.uid}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "清除该项全部照片及PDF",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "暂无照片文件",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                }
            }

            if (isExpanded) {
                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
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
}

@Composable
fun EmptyStateView(
    onImportClick: () -> Unit,
    onSampleClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无盘点台账资产数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "工作提示：请导入底账资产数据表格进行点检盘点。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onImportClick,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.FileOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("从手机存储导入台账清单", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEnhancingDialog(
    file: File,
    viewModel: com.example.ui.StockViewModel,
    onFilterSelected: (String) -> Unit,
    onApplyCrop: (Float, Float, Float, Float) -> Unit,
    onDeletePhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    val watermarkEnabled by viewModel.watermarkEnabled.collectAsStateWithLifecycle()
    val watermarkTrEnabled by viewModel.watermarkTrEnabled.collectAsStateWithLifecycle()
    val blEnabled by viewModel.watermarkBlEnabled.collectAsStateWithLifecycle()
    val showDate by viewModel.watermarkBlShowDate.collectAsStateWithLifecycle()
    val showTime by viewModel.watermarkBlShowTime.collectAsStateWithLifecycle()
    val showGps by viewModel.watermarkBlShowGps.collectAsStateWithLifecycle()
    val showAddress by viewModel.watermarkBlShowAddress.collectAsStateWithLifecycle()

    val activeItem by viewModel.activeItemForPhoto.collectAsStateWithLifecycle()
    val stockItems by viewModel.stockItems.collectAsStateWithLifecycle()

    val watermarkText = remember(activeItem, stockItems) {
        val item = activeItem
        if (item != null) {
            val prefix = viewModel.getCategoryPrefix(item.category)
            val filtered = stockItems.filter { it.category == item.category }
            val sorted = filtered.sortedBy { it.originalCode.ifEmpty { it.uid } }
            val idx = sorted.indexOfFirst { it.uid == item.uid }
            val seq = String.format(java.util.Locale.CHINA, "%04d", if (idx != -1) idx + 1 else 1)
            "$prefix-$seq"
        } else {
            "C-0001"
        }
    }

    // Read actual physical metadata of the photographed file!
    val photoMeta = remember(file) { com.example.util.PhotoMetadataUtils.readPhysicalMetadata(file) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "实地勘测存证照片预览",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Photo container with real-time watermark overlay!
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Top Right Serial Overlay
                    if (watermarkEnabled && watermarkTrEnabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Red, RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = watermarkText,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bottom-Left dynamic watermarking overlay on the ACTUAL photo preview!
                    if (blEnabled) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(6.dp)
                        ) {
                            if (showDate) {
                                Text(
                                    text = "拍摄日期：${photoMeta.dateStr}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 1f))
                                )
                            }
                            if (showTime) {
                                Text(
                                    text = "时间：${photoMeta.timeStr}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 1f))
                                )
                            }
                            if (showGps) {
                                Text(
                                    text = String.format(java.util.Locale.CHINA, "经度：%.2f  纬度：%.2f", photoMeta.longitude, photoMeta.latitude),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 1f))
                                )
                            }
                            if (showAddress) {
                                Text(
                                    text = "位置：${photoMeta.address}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 1f)),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Watermark settings togglers directly inside preview screen for rich user interactivity!
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📷 实时叠加实地勘测水印",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Switch(
                                checked = blEnabled,
                                onCheckedChange = { viewModel.updateWatermarkBlSettings(enabled = it) },
                                modifier = Modifier.testTag("dialog_bl_switch").scale(0.7f)
                            )
                        }

                        if (blEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = showDate,
                                        onCheckedChange = { viewModel.updateWatermarkBlSettings(showDate = it) },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                    Text("显示日期", fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = showTime,
                                        onCheckedChange = { viewModel.updateWatermarkBlSettings(showTime = it) },
                                        modifier = Modifier.scale(0.8f)
                                        )
                                    Text("显示时间", fontSize = 11.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = showGps,
                                        onCheckedChange = { viewModel.updateWatermarkBlSettings(showGps = it) },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                    Text("显示经纬", fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = showAddress,
                                        onCheckedChange = { viewModel.updateWatermarkBlSettings(showAddress = it) },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                    Text("显示位置", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "物理元数据 (直接源于照片硬件写入)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (photoMeta.hasLocation) {
                                    Text(String.format(java.util.Locale.CHINA, "经度：%.2f", photoMeta.longitude), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format(java.util.Locale.CHINA, "纬度：%.2f", photoMeta.latitude), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("经纬度：未获取定位", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("参考位置：" + photoMeta.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDeletePhoto,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除照片")
                }

                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

