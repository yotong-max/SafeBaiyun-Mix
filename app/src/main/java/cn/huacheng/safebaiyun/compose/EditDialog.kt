package cn.huacheng.safebaiyun.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice

/**
 * 多门禁管理弹窗（替代原来的单组 MAC/Key 编辑弹窗）
 *
 * 支持展开/折叠每项、新增、修改、删除门禁。
 *
 * @param state        控制弹窗显示/隐藏
 * @param initialDoors 初始门禁列表
 * @param onSaved      保存后的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDoorDialog(
    state: MutableState<Boolean>,
    initialDoors: List<DoorDevice>,
    onSaved: () -> Unit,
) {
    // 工作副本，不直接操作源数据
    var doors by remember { mutableStateOf(initialDoors.map { it.copy() }.toMutableList()) }

    // 记录当前正在编辑的门的 id（null 表示折叠）
    var editingId by remember { mutableIntStateOf(-1) }

    // 每个门的临时编辑态
    val editName = remember { mutableStateOf("") }
    val editMac = remember { mutableStateOf("") }
    val editKey = remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = { state.value = false }) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "管理门禁",
                    style = typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    // 保存所有变更
                    DataRepo.saveDoors(doors.toList())
                    onSaved()
                    state.value = false
                }) {
                    Text(text = "保存")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (doors.isEmpty()) {
                Text(
                    text = "暂无门禁",
                    style = typography.bodyMedium,
                    color = colorScheme.outline,
                    modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally)
                )
            }

            // 门禁列表
            doors.forEachIndexed { index, door ->
                DoorEditItem(
                    door = door,
                    isEditing = editingId == door.id,
                    editName = editName,
                    editMac = editMac,
                    editKey = editKey,
                    onToggleExpand = {
                        if (editingId == door.id) {
                            editingId = -1 // 折叠
                        } else {
                            // 展开并加载当前值到输入框
                            editingId = door.id
                            editName.value = door.name
                            editMac.value = door.mac
                            editKey.value = door.key
                        }
                    },
                    onSaveEdit = {
                        // 验证
                        val name = editName.value.trim()
                        val mac = editMac.value.trim()
                        val key = editKey.value.trim()
                        if (name.isEmpty()) return@DoorEditItem

                        doors[index] = door.copy(name = name, mac = mac, key = key)
                        editingId = -1 // 折叠
                    },
                    onDelete = {
                        doors.removeAt(index)
                        if (editingId == door.id) editingId = -1
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 新增按钮
            OutlinedButton(
                onClick = {
                    val nextId = (doors.maxOfOrNull { it.id } ?: 0) + 1
                    doors.add(
                        DoorDevice(
                            id = nextId,
                            name = "门禁${doors.size + 1}",
                            mac = "",
                            key = ""
                        )
                    )
                    // 自动展开新项
                    editingId = nextId
                    editName.value = "门禁${doors.size}"
                    editMac.value = ""
                    editKey.value = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "添加门禁")
            }
        }
    }
}

/**
 * 单个门禁的编辑条目（可折叠/展开）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoorEditItem(
    door: DoorDevice,
    isEditing: Boolean,
    editName: MutableState<String>,
    editMac: MutableState<String>,
    editKey: MutableState<String>,
    onToggleExpand: () -> Unit,
    onSaveEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val fieldModifier = Modifier
        .padding(4.dp)
        .fillMaxWidth()

    CardEditorContent {
        Column(modifier = Modifier.padding(8.dp)) {
            // 折叠状态：只显示名称 + 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = door.name,
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                if (!isEditing) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = colorScheme.primary
                        )
                    }
                } else {
                    TextButton(onClick = onSaveEdit) { Text("完成") }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = colorScheme.error
                    )
                }
            }

            // 展开状态：显示编辑字段
            if (isEditing) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = editName.value,
                    onValueChange = { editName.value = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = fieldModifier
                )
                OutlinedTextField(
                    value = editMac.value,
                    onValueChange = { editMac.value = it },
                    label = { Text("MAC 地址") },
                    singleLine = true,
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    modifier = fieldModifier
                )
                OutlinedTextField(
                    value = editKey.value,
                    onValueChange = { editKey.value = it },
                    label = { Text("加密 Key") },
                    singleLine = true,
                    modifier = fieldModifier
                )
            }
        }
    }
}

/**
 * 简单卡片容器，用于每个门禁条的背景
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardEditorContent(content: @Composable () -> Unit) {
    content()
}
