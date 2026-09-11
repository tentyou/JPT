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

@Composable
fun CameraCaptureScreen(viewModel: StockViewModel, activeItem: StockItem) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] ?: hasCameraPermission
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (hasCameraPermission) {
        CameraPreviewWidget(viewModel = viewModel, activeItem = activeItem)
    } else {
        CameraPermissionDeniedWidget(
            onRequestClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onBackClick = { viewModel.endPhotoCapture {} }
        )
    }
}

@Composable
fun CameraPermissionDeniedWidget(onRequestClick: () -> Unit, onBackClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "未获得相机授权",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "连续拍照盘点功能必须使用手机相机物理采集镜头。程序不会未经授权读取您的其他数据保护隐私，请授权开启相机工作权限。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("申请开启相机权限", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onBackClick) {
                Text("取消并返回盘点列表", color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun CameraPreviewWidget(viewModel: StockViewModel, activeItem: StockItem) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Load active item session photo files
    val sessionPhotos by viewModel.activeSessionPhotos.collectAsStateWithLifecycle()
    var selectedImageForFilter by remember { mutableStateOf<File?>(null) }
    var confirmRetake by remember { mutableStateOf(false) }
    var confirmDeletePhoto by remember { mutableStateOf(false) }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var showFlashOverlay by remember { mutableStateOf(false) }

    // Shutter animation state
    val flashAlpha by animateFloatAsState(
        targetValue = if (showFlashOverlay) 0.8f else 0.0f,
        animationSpec = tween(durationMillis = 80),
        label = "ShutterFlashAlpha"
    )

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    // Coroutine scope
    val scope = rememberCoroutineScope()

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val bindCamera: (PreviewView) -> Unit = { previewView ->
        val executor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            try {
                cameraProvider.unbindAll()
                imageCapture = capture
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, executor)
    }

    // Rebind the active lens instead of leaving the preview unbound.
    LaunchedEffect(lensFacing) {
        previewViewRef?.let { bindCamera(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fullscreen dynamic camera viewfinder
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewViewRef = previewView
                bindCamera(previewView)
                previewView
            },
            modifier = Modifier.fillMaxSize(),
            update = { /* Update preview overlay if dynamic state fluctuates */ }
        )

        // Simulated shutter mechanical flash overlay
        if (flashAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
        }

        // Camera Header Bar Overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    viewModel.endPhotoCapture {}
                },
                modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = activeItem.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
                Text(
                    text = "连续拍照关联模式",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "反转镜头",
                    tint = Color.White
                )
            }
        }

        // Camera Footer Controls Panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .navigationBarsPadding()
                .padding(vertical = 16.dp)
        ) {
            // Part A: LazyRow thumbnail gallery of currently snapped session photos
            AnimatedVisibility(
                visible = sessionPhotos.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "本次拍摄记录 (已拍摄 ${sessionPhotos.size} 张照片)",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.Red.copy(alpha = 0.8f),
                            modifier = Modifier.clickable { confirmRetake = true }
                        ) {
                            Text(
                                text = "重拍",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(sessionPhotos) { imageFile ->
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedImageForFilter = imageFile
                                    }
                            ) {
                                AsyncImage(
                                    model = imageFile,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    if (selectedImageForFilter != null) {
                        DocumentEnhancingDialog(
                            file = selectedImageForFilter!!,
                            viewModel = viewModel,
                            onFilterSelected = { filter ->
                                viewModel.applyFilterToPhoto(selectedImageForFilter!!, filter)
                            },
                            onApplyCrop = { top, bottom, left, right ->
                                viewModel.applyCropToPhoto(selectedImageForFilter!!, top, bottom, left, right)
                                selectedImageForFilter = null
                            },
                            onDeletePhoto = { confirmDeletePhoto = true },
                            onDismiss = { selectedImageForFilter = null }
                        )
                    }
                    if (confirmDeletePhoto) {
                        AlertDialog(
                            onDismissRequest = { confirmDeletePhoto = false },
                            title = { Text("删除照片？") },
                            text = { Text("删除后无法恢复，生成的 PDF 也会在下次生成时更新。") },
                            confirmButton = { Button(onClick = { viewModel.deletePhoto(selectedImageForFilter!!); selectedImageForFilter = null; confirmDeletePhoto = false }) { Text("确认删除") } },
                            dismissButton = { OutlinedButton(onClick = { confirmDeletePhoto = false }) { Text("取消") } }
                        )
                    }
                    if (confirmRetake) {
                        AlertDialog(
                            onDismissRequest = { confirmRetake = false },
                            title = { Text("重新拍摄？") },
                            text = { Text("将删除本次已拍摄的全部照片，是否继续？") },
                            confirmButton = { Button(onClick = { viewModel.retakeItem(activeItem); confirmRetake = false }) { Text("确认重拍") } },
                            dismissButton = { OutlinedButton(onClick = { confirmRetake = false }) { Text("取消") } }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (sessionPhotos.isEmpty()) {
                        "提示：按白色内环快门可以连拍多张实物图，最后点完成即合并PDF！"
                    } else {
                        "已拍摄 ${sessionPhotos.size} 张多角度物理卡片"
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (com.example.BuildConfig.DEBUG) {
                    Button(
                        onClick = {
                            viewModel.simulateCapture(activeItem)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .testTag("simulate_capture_button")
                        .height(32.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Yellow
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "云测试/无硬件？一键模拟实物拍照存证",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    }
                }
            }

            // Part B: Large physical-shutter buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Secondary Dismiss buttons
                TextButton(
                    onClick = { viewModel.endPhotoCapture {} }
                ) {
                    Text("取消", color = Color.White, fontSize = 16.sp)
                }

                // Shutter Outer Target Frame
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .clickable {
                            val imgCapture = imageCapture
                            if (imgCapture == null) {
                                Toast
                                    .makeText(context, "相机还未完全就绪，请稍后...", Toast.LENGTH_SHORT)
                                    .show()
                                return@clickable
                            }

                            // Build unique photo path under filesDir/photos/{item.uid}/
                            val targetFile = File(
                                context.filesDir,
                                "photos/${activeItem.uid}/photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}.jpg"
                            )
                            targetFile.parentFile?.mkdirs()

                            val outputOptions = ImageCapture.OutputFileOptions
                                .Builder(targetFile)
                                .build()
                            val executor = ContextCompat.getMainExecutor(context)

                            // Quick shutter flash
                            showFlashOverlay = true
                            scope.launch {
                                delay(100)
                                showFlashOverlay = false
                            }

                            imgCapture.takePicture(
                                outputOptions,
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            com.example.util.PhotoMetadataUtils.writePhysicalMetadata(context, targetFile, activeItem)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                viewModel.refreshActiveSessionPhotos(activeItem.uid)
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        exception.printStackTrace()
                                        Toast
                                            .makeText(
                                                context,
                                                "拍图失败，请检验硬件支持：${exception.message}",
                                                Toast.LENGTH_LONG
                                            )
                                            .show()
                                    }
                                }
                            )
                        }
                        .testTag("camera_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }

                // Finish and Close Shutter button
                Button(
                    onClick = {
                        viewModel.endPhotoCapture {
                            Toast.makeText(context, "后台拼合中，已即刻返回。拼合成功后将弹窗提示", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sessionPhotos.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.DarkGray
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("camera_done_button")
                ) {
                    Text("完成", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
