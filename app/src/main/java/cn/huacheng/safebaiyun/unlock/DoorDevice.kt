package cn.huacheng.safebaiyun.unlock

import kotlinx.serialization.Serializable

/**
 * 门禁设备数据模型
 *
 * @param id   唯一标识（用于部件等场景传递）
 * @param name 显示名称，如"门禁1"、"家里"、"公司"
 * @param mac  蓝牙MAC地址
 * @param key  加密密钥
 */
@Serializable
data class DoorDevice(
    val id: Int = 0,
    var name: String,
    var mac: String,
    var key: String
)
