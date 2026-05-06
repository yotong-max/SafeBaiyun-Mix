package cn.huacheng.safebaiyun.compose

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.util.QRCodeUtils
import cn.huacheng.safebaiyun.util.showToast
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRImportView(navController: androidx.navigation.NavHostController) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var scannedData by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var importedDoors by remember { mutableStateOf<List<DoorDevice>?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            showToast("需要相机权限才能扫描二维码")
        }
    }

    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描导入") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showConfirmDialog && importedDoors != null) {
                ImportConfirmDialog(
                    doors = importedDoors!!,
                    onDismiss = {
                        showConfirmDialog = false
                        importedDoors = null
                        scannedData = null
                    },
                    onImport = { replaceAll ->
                        importDoors(importedDoors!!, replaceAll)
                        showConfirmDialog = false
                        importedDoors = null
                        scannedData = null
                        showToast("导入成功")
                        navController.popBackStack()
                    }
                )
            } else if (!hasCameraPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("需要相机权限才能扫描二维码")

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }) {
                        Text("请求相机权限")
                    }
                }
            } else if (scannedData == null) {
                CameraPreview(
                    onQrCodeDetected = { data ->
                        if (!isProcessing) {
                            isProcessing = true
                            scannedData = data
                            val decodedDoors = QRCodeUtils.decodeQRTodoors(data)
                            if (decodedDoors != null && decodedDoors.isNotEmpty()) {
                                importedDoors = decodedDoors
                                showConfirmDialog = true
                            } else {
                                showToast("无效的二维码格式")
                                scannedData = null
                            }
                            isProcessing = false
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    QrScannerOverlay()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "请将二维码对准取景框",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("正在处理...")
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onQrCodeDetected: (String) -> Unit) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            processImageProxy(imageProxy, onQrCodeDetected)
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun processImageProxy(
    imageProxy: androidx.camera.core.ImageProxy,
    onQrCodeDetected: (String) -> Unit
) {

    val mediaImage = imageProxy.image ?: run {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    val scanner = BarcodeScanning.getClient()

    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                if (barcode.format == Barcode.FORMAT_QR_CODE) {
                    barcode.rawValue?.let { value ->
                        onQrCodeDetected(value)
                    }
                }
            }
        }
        .addOnFailureListener { e ->
            e.printStackTrace()
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

@Composable
private fun QrScannerOverlay() {

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val overlaySize = minOf(canvasWidth, canvasHeight) * 0.7f
        val left = (canvasWidth - overlaySize) / 2
        val top = (canvasHeight - overlaySize) / 2

        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            size = Size(canvasWidth, canvasHeight)
        )

        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(overlaySize, overlaySize),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )

        drawRect(
            color = primaryColor,
            topLeft = Offset(left, top),
            size = Size(overlaySize, overlaySize),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun ImportConfirmDialog(
    doors: List<DoorDevice>,
    onDismiss: () -> Unit,
    onImport: (Boolean) -> Unit
) {

    var replaceAll by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现门禁配置") },
        text = {
            Column {
                Text("即将导入以下门禁：")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                doors.forEach { door ->
                    Text(
                        text = "• ${door.name} (${door.mac.takeIf { it.isNotEmpty() } ?: "未配置 MAC"})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "导入方式：")

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = replaceAll,
                        onClick = { replaceAll = true }
                    )
                    Text("替换全部配置")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !replaceAll,
                        onClick = { replaceAll = false }
                    )
                    Text("追加到现有配置")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onImport(replaceAll) }) {
                Text("确认导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun importDoors(newDoors: List<DoorDevice>, replaceAll: Boolean) {
    if (replaceAll) {
        DataRepo.saveDoors(newDoors)
    } else {
        val existingDoors = DataRepo.getDoors()
        val maxId = existingDoors.maxOfOrNull { it.id } ?: 0
        val renumberedDoors = newDoors.mapIndexed { index, door ->
            door.copy(id = maxId + index + 1)
        }
        existingDoors.addAll(renumberedDoors)
        DataRepo.saveDoors(existingDoors)
    }
}
