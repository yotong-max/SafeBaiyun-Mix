package cn.huacheng.safebaiyun.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.ShortcutActivity

/**
 * 三开门大部件 —— 同时展示最多3个门禁，每个一键解锁
 *
 * 尺寸：约 4×2 格（宽幅大部件）
 */
class ExtraLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = ExtraLargeWidget
}

object ExtraLargeWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    /** Intent 参数 key：门禁 ID */
    private val KEY_DOOR_ID = ActionParameters.Key<Int>("door_id")

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        // 从 DataRepo 读取前 3 个门禁（Glance 环境可直接读 SP）
        val doors = cn.huacheng.safebaiyun.unlock.DataRepo.getDoors().take(3)

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(170.dp)
                .cornerRadius(24.dp)
                .background(GlanceTheme.colors.surface)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 三按钮行
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                doors.forEach { door ->
                    DoorButton(
                        context = context,
                        doorId = door.id,
                        name = door.name,
                        hasConfig = door.mac.isNotEmpty() && door.key.isNotEmpty()
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }

                // 不足 3 个时补空位占位
                repeat(3 - doors.size) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }

            // 底部标题
            Text(
                text = "白云通 · 三键快开",
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
                modifier = GlanceModifier.padding(top = 4.dp)
            )
        }
    }

    /**
     * 单个门禁按钮：图标 + 名称 + 开门 CircleButton
     */
    @Composable
    private fun DoorButton(
        context: Context,
        doorId: Int,
        name: String,
        hasConfig: Boolean,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.width(90.dp).fillMaxHeight(),
        ) {
            Box(
                modifier = GlanceModifier.defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.unlock),
                    contentDescription = "解锁$name",
                    backgroundColor = if (hasConfig) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant,
                    contentColor = if (hasConfig) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant,
                    onClick = actionRunCallback<UnlockDoorAction>(
                        actionParametersOf(KEY_DOOR_ID to doorId)
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = name,
                style = TextStyle(fontSize = 13.sp),
                maxLines = 1
            )
        }
    }
}

/**
 * 解锁门禁的回调 Action
 */
class UnlockDoorAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val doorId = parameters[KEY_DOOR_ID]
        if (doorId != null) {
            val intent = Intent(context, ShortcutActivity::class.java).apply {
                putExtra(ShortcutActivity.EXTRA_DOOR_ID, doorId.toInt())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    companion object {
        private val KEY_DOOR_ID = ActionParameters.Key<Int>("door_id")
    }
}
