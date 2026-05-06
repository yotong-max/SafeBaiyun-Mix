package cn.huacheng.safebaiyun.compose

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.util.QRCodeUtils
import cn.huacheng.safebaiyun.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRExportView(navController: androidx.navigation.NavHostController) {

    val context = LocalContext.current

    var qrBitmap by remember {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    var showSavedMessage by remember { mutableStateOf(false) }

    val doors = remember { DataRepo.getDoors() }

    LaunchedEffect(doors) {
        if (doors.isNotEmpty()) {
            try {
                val encodedData = QRCodeUtils.encodeDoorsToQR(doors)
                qrBitmap = QRCodeUtils.generateQRCode(encodedData, 512)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("生成二维码失败")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导出配置") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap!!.asImageBitmap(),
                    contentDescription = "门禁配置二维码",
                    modifier = Modifier.size(300.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "已配置 ${doors.size} 个门禁",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "请在新设备上打开应用扫描此二维码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        qrBitmap?.let { bitmap ->
                            if (QRCodeUtils.saveQRCodeToGallery(context, bitmap)) {
                                showToast("已保存到相册")
                            } else {
                                showToast("保存失败")
                            }
                        }
                    }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("保存到相册")
                    }

                    Button(onClick = {
                        qrBitmap?.let { bitmap ->
                            shareQRCode(context, bitmap)
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("分享")
                    }
                }

                if (showSavedMessage) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "✓ 已保存到相册",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (doors.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无门禁配置，请先添加门禁")
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("正在生成二维码...")
                }
            }
        }
    }
}

private fun shareQRCode(context: Context, bitmap: android.graphics.Bitmap) {
    try {
        val cachePath = context.cacheDir
        val file = java.io.File(cachePath, "door_config_qr.png")
        file.delete()

        val stream = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "分享门禁配置"))
    } catch (e: Exception) {
        e.printStackTrace()
        showToast("分享失败")
    }
}
