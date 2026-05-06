package cn.huacheng.safebaiyun

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.showToast

/**
 * 快捷开门 Activity（桌面快捷方式 / 部件按钮 启动）
 *
 * 通过 Intent extra [EXTRA_DOOR_ID] 指定要开的门禁 ID。
 * 未传则开第一个有效门禁（向后兼容）。
 */
class ShortcutActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DOOR_ID = "door_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
        }
        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            createShortcut()
        } else {
            unlock()
        }
    }

    private fun createShortcut() {
        val intent = Intent()
        val icon = Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher)
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.unlock_door))
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon)
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent(this, ShortcutActivity::class.java))
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun unlock() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasPermission) {
            showToast("请先授予蓝牙权限")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // 优先从 Intent 取指定门禁 id
        val targetDoorId = intent.getIntExtra(EXTRA_DOOR_ID, -1)

        val doors = DataRepo.getDoors()

        val doorToUnlock = if (targetDoorId > 0) {
            doors.find { it.id == targetDoorId }
        } else {
            // 兼容旧逻辑：取第一个有效门禁
            doors.firstOrNull { it.mac.isNotEmpty() && it.key.isNotEmpty() }
                ?: doors.firstOrNull()
        }

        if (doorToUnlock == null || doorToUnlock.mac.isEmpty() || doorToUnlock.key.isEmpty()) {
            showToast("请先初始化门禁")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        showToast("正在解锁 ${doorToUnlock.name}")
        UnlockRepo.unlock(doorToUnlock.mac, doorToUnlock.key)
        finish()
    }
}
