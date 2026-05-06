package cn.huacheng.safebaiyun.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppSettingsAlt
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.showToast

/**
 * 主页面 —— 门禁列表 + 开门操作
 */
@Composable
fun MainView(navController: NavHostController) {

    val context = LocalContext.current

    val hasPermission = remember {
        mutableStateOf(false)
    }

    val showManageDialog = remember {
        mutableStateOf(false)
    }

    // 门禁列表状态（响应式刷新）
    val doors = remember {
        mutableStateOf<List<DoorDevice>>(DataRepo.getDoors())
    }

    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission.value =
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            hasPermission.value = true
        }
    }

    Column {
        MainTopBar(onEditClick = {
            showManageDialog.value = true
        }, onHelperClick = {
            navController.navigate("helper")
        })
        
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp), contentAlignment = Alignment.Center
        ) {
            if (hasPermission.value) {
                DoorListContent(doors = doors, onRefresh = {
                    doors.value = DataRepo.getDoors()
                })
            } else {
                PermissionView(hasPermission)
            }
        }

        // 底部二维码操作按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = { navController.navigate("qr_export") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AppSettingsAlt, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("导出配置")
            }

            Spacer(modifier = Modifier.size(8.dp))

            OutlinedButton(
                onClick = { navController.navigate("qr_import") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.HelpOutline, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("扫描导入")
            }
        }

        if (showManageDialog.value) {
            ManageDoorDialog(
                state = showManageDialog,
                initialDoors = doors.value,
                onSaved = {
                    doors.value = DataRepo.getDoors()
                }
            )
        }
    }
}

/**
 * 门禁列表区域
 */
@Composable
private fun DoorListContent(
    doors: MutableState<List<DoorDevice>>,
    onRefresh: () -> Unit,
) {
    if (doors.value.isEmpty()) {
        Text(text = "暂无门禁，请点击右上角添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(doors.value, key = { it.id }) { door ->
            DoorCard(door)
        }
        item {
            Spacer(modifier = Modifier.size(60.dp)) // 底部留白，不遮挡 FAB
        }
    }
}

/**
 * 单个门禁卡片
 */
@Composable
private fun DoorCard(door: DoorDevice) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = door.name,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.size(8.dp))
            Button(
                onClick = {
                    if (door.mac.isEmpty() || door.key.isEmpty()) {
                        showToast("请先配置该门禁的 MAC 和 Key")
                        return@Button
                    }
                    showToast("正在解锁 ${door.name}")
                    UnlockRepo.unlock(door.mac, door.key)
                },
                modifier = Modifier.size(144.dp, 48.dp)
            ) {
                Text(text = stringResource(id = R.string.unlock_door), fontSize = 16.sp)
            }
            // 显示 MAC 前缀便于识别（脱敏）
            if (door.mac.isNotEmpty()) {
                Text(
                    text = formatMacShort(door.mac),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 将 AA:BB:CC:DD:EE:FF 显示为 AA:BB:CC:... */
private fun formatMacShort(mac: String): String {
    val parts = mac.split(":")
    return if (parts.size >= 3) "${parts[0]}:${parts[1]}:${parts[2]}..." else mac
}

// ── 权限请求视图（保持不变） ──

@Composable
private fun PermissionView(hasPermission: MutableState<Boolean>) {
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
            hasPermission.value = isGranted
        }

    Button(modifier = Modifier.size(144.dp, 56.dp),
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }

        }) {
        Text(text = stringResource(id = R.string.request_permission), fontSize = 18.sp)

    }
}
