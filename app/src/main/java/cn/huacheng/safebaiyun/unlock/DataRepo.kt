package cn.huacheng.safebaiyun.unlock

import android.content.Context
import android.content.SharedPreferences
import cn.huacheng.safebaiyun.util.ContextHolder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 多门禁数据仓库
 *
 * 使用 SharedPreferences + JSON 持久化门禁列表，
 * 支持增删改查，向后兼容旧版单组 MAC/Key 数据。
 */
object DataRepo {

    private val json = Json { ignoreUnknownKeys = true }

    private const val KEY_DOORS = "doors_json"
    private const val LEGACY_MAC = "mac"
    private const val LEGACY_KEY = "key"

    private val preferences: SharedPreferences by lazy {
        ContextHolder.get().getSharedPreferences("data", Context.MODE_PRIVATE)
    }

    /**
     * 获取全部门禁列表（首次调用时自动迁移旧数据）
     */
    fun getDoors(): MutableList<DoorDevice> {
        val raw = preferences.getString(KEY_DOORS, null)
        if (raw != null) {
            return try {
                json.decodeFromString<List<DoorDevice>>(raw).toMutableList()
            } catch (e: Exception) {
                migrateLegacy()
            }
        }
        return migrateLegacy()
    }

    /**
     * 保存完整门禁列表
     */
    fun saveDoors(doors: List<DoorDevice>) {
        preferences.edit().putString(KEY_DOORS, json.encodeToString(doors)).apply()
    }

    /**
     * 新增门禁，自动分配 ID
     */
    fun addDoor(door: DoorDevice): List<DoorDevice> {
        val doors = getDoors()
        val newId = (doors.maxOfOrNull { it.id } ?: 0) + 1
        val newDoor = door.copy(id = newId)
        doors.add(newDoor)
        saveDoors(doors)
        return doors
    }

    /**
     * 更新指定门禁
     */
    fun updateDoor(door: DoorDevice) {
        val doors = getDoors()
        val index = doors.indexOfFirst { it.id == door.id }
        if (index >= 0) {
            doors[index] = door
            saveDoors(doors)
        }
    }

    /**
     * 删除指定门禁
     */
    fun deleteDoor(id: Int) {
        val doors = getDoors()
        doors.removeAll { it.id == id }
        saveDoors(doors)
    }

    /**
     * 根据 id 获取单个门禁
     */
    fun getDoorById(id: Int): DoorDevice? {
        return getDoors().find { it.id == id }
    }

    // ── 向后兼容：旧版迁移 ──

    private fun migrateLegacy(): MutableList<DoorDevice> {
        val mac = preferences.getString(LEGACY_MAC, "") ?: ""
        val key = preferences.getString(LEGACY_KEY, "") ?: ""

        val doors: MutableList<DoorDevice> = if (mac.isNotEmpty() && key.isNotEmpty()) {
            mutableListOf(DoorDevice(id = 1, name = "门禁1", mac = mac, key = key))
        } else {
            // 纯净安装，给一个默认空门禁
            mutableListOf(DoorDevice(id = 1, name = "门禁1", mac = "", key = ""))
        }

        saveDoors(doors)

        // 清除旧字段
        preferences.edit()
            .remove(LEGACY_MAC)
            .remove(LEGACY_KEY)
            .apply()

        return doors
    }

    // ── 保留旧接口供 ShortcutActivity 等未改造的调用点兼容 ──

    /**
     * @return 第一个有效门禁的 mac-key 对；兼容旧调用
     * @deprecated 请使用 [getDoors]
     */
    fun readData(): Pair<String, String> {
        val door = getDoors().firstOrNull { it.mac.isNotEmpty() && it.key.isNotEmpty() }
            ?: getDoors().firstOrNull()
        return (door?.mac ?: "") to (door?.key ?: "")
    }
}
