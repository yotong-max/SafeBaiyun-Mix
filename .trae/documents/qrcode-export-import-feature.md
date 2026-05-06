# 门禁配置二维码导出/导入功能实现计划

## 📋 功能概述

为 SafeBaiyun 应用新增门禁配置的二维码功能，支持：

* **导出**：将当前配置的门禁数据生成二维码图片，方便新设备扫描导入

* **导入**：通过扫描二维码快速导入门禁配置到新设备

***

## 🔍 现有架构分析

### 数据模型

* `DoorDevice`: 包含 `id`, `name`, `mac`, `key` 四个字段

* 已支持 `@Serializable` 序列化注解

* 使用 SharedPreferences + JSON 持久化

### 技术栈

* Kotlin + Jetpack Compose

* Navigation Compose（当前路由："main", "helper"）

* kotlinx.serialization（已引入）

***

## 🎯 实现方案

### 一、依赖管理

#### 1.1 添加二维码生成库

**文件**: `gradle/libs.versions.toml`

```toml
[versions]
zxing = "3.5.3"
camerax = "1.3.4"

[libraries]
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }
zxing-javase = { group = "com.google.zxing", name = "javase", version.ref = "zxing" }
camerax-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
mlkit-barcode-scanning = { group = "com.google.mlkit", name = "barcode-scanning", version = "17.2.0" }
```

#### 1.2 更新 build.gradle.kts

**文件**: `app/build.gradle.kts`

```kotlin
dependencies {
    // 二维码生成
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)

    // 相机 & 扫描
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode.scanning)
}
```

***

### 二、核心工具类开发

#### 2.1 创建 QRCodeUtils 工具类

**文件**: `app/src/main/java/cn/huacheng/safebaiyun/util/QRCodeUtils.kt`

**功能**:

* `generateQRCode(data: String, size: Int): Bitmap` - 生成二维码位图

* `encodeDoorsToQR(doors: List<DoorDevice>): String` - 将门禁列表编码为字符串

* `decodeQRTodoors(data: String): List<DoorDevice>` - 从字符串解码门禁列表

* `saveQRCodeToGallery(context: Context, bitmap: Bitmap)` - 保存到相册

**编码策略**:

```kotlin
// 格式：SBY|Base64(JSON)
val jsonStr = Json.encodeToString(doors)
val encoded = "SBY|" + Base64.encodeToString(jsonStr.toByteArray(), Base64.DEFAULT)
```

* 前缀 "SBY" 用于验证数据来源

* Base64 编码防止乱码并压缩长度

***

### 三、UI 页面开发

#### 3.1 二维码展示页面（导出）

**文件**: `app/src/main/java/cn/huacheng/safebaiyun/compose/QRExportView.kt`

**页面结构**:

```
┌─────────────────────────────┐
│  ← 返回        导出配置      │
├─────────────────────────────┤
│                             │
│     ┌─────────────┐         │
│     │             │         │
│     │   QR Code   │         │
│     │   (300x300) │         │
│     │             │         │
│     └─────────────┘         │
│                             │
│  已配置 X 个门禁            │
│                             │
│  [💾 保存到相册] [📤 分享]  │
└─────────────────────────────┘
```

**交互功能**:

* 显示所有门禁数据的二维码

* 点击"保存到相册"将二维码存入设备图库

* 点击"分享"调用系统分享功能

* 提示用户在新设备上打开应用扫描该二维码

#### 3.2 二维码扫描页面（导入）

**文件**: `app/src/main/java/cn/huacheng/safebaiyun/compose/QRImportView.kt`

**页面结构**:

```
┌─────────────────────────────┐
│  ← 返回        扫描导入      │
├─────────────────────────────┤
│                             │
│  ┌─────────────────────┐    │
│  │                     │    │
│  │   Camera Preview    │    │
│  │   (全屏取景框)       │    │
│  │                     │    │
│  └─────────────────────┘    │
│                             │
│  请将二维码对准取景框        │
│                             │
└─────────────────────────────┘
```

**交互功能**:

* 使用 CameraX + ML Kit 实现实时扫描

* 自动识别二维码并解析内容

* 成功后弹出确认对话框显示将要导入的数据

* 用户确认后合并/替换现有配置

#### 3.3 导入确认对话框

**组件**: 内嵌于 QRImportView

**对话框内容**:

```
┌─────────────────────────────┐
│  发现门禁配置               │
├─────────────────────────────┤
│  即将导入以下门禁：          │
│  • 门禁1 (XX:XX:XX)        │
│  • 门禁2 (YY:YY:YY)        │
│                             │
│  ○ 追加到现有配置           │
│  ● 替换全部配置              │
│                             │
│  [取消]        [确认导入]   │
└─────────────────────────────┘
```

***

### 四、导航与路由更新

#### 4.1 更新 MainActivity

**文件**: `app/src/main/java/cn/huacheng/safebaiyun/MainActivity.kt`

**新增路由**:

```kotlin
composable("qr_export") {
    QRExportView(navController)
}

composable("qr_import") {
    QRImportView(navController)
}
```

#### 4.2 主页面入口

**文件**: `app/src/main/java/cn/huacheng/safebaiyun/compose/MainView.kt`

**位置**: MainTopBar 右侧或 FAB 区域

**建议入口**:

* 方案 A：在 TopBar 添加"导出"图标按钮

* 方案 B：在门禁列表底部添加"导出配置 / 导入配置"两个按钮

* **推荐方案 B**：更直观且不影响顶部布局

**UI 示例**（方案 B）:

```kotlin
Column {
    MainTopBar(...)
    
    LazyColumn(...) {
        // 门禁卡片列表
    }
    
    // 底部操作区
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OutlinedButton(onClick = { navController.navigate("qr_export") }) {
            Icon(Icons.Default.QrCode, contentDescription = null)
            Text("导出配置")
        }
        
        OutlinedButton(onClick = { navController.navigate("qr_import") }) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Text("扫描导入")
        }
    }
}
```

***

### 五、权限管理

#### 5.1 需要的权限

* **相机权限** (`CAMERA`)：用于扫描二维码

* **存储权限** (Android < 10)：保存二维码到相册

#### 5.2 权限请求策略

在 QRImportView 中动态请求相机权限：

```kotlin
val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA) { ... }

LaunchedEffect(Unit) {
    if (!cameraPermissionStatus) {
        cameraPermissionState.launchPermissionRequest()
    }
}
```

***

### 六、数据安全考虑

#### 6.1 加密选项（可选增强）

如果需要更高的安全性，可以在编码前对数据进行 AES 加密：

```kotlin
fun encryptData(data: String): String {
    // 使用固定密钥或设备特定密钥进行 AES 加密
}

fun decryptData(encoded: String): String {
    // 解密
}
```

**注意**: 本计划先实现基础版本，加密可作为后续优化。

#### 6.2 数据校验

* 验证前缀 "SBY" 确保是有效的门禁配置数据

* 校验 JSON 格式正确性

* 校验每个 DoorDevice 的必要字段非空

***

## 📁 文件清单

### 新建文件

1. ✅ `util/QRCodeUtils.kt` - 二维码工具类
2. ✅ `compose/QRExportView.kt` - 导出页面
3. ✅ `compose/QRImportView.kt` - 导入页面

### 修改文件

1. ⚙️ `gradle/libs.versions.toml` - 添加依赖版本
2. ⚙️ `app/build.gradle.kts` - 添加依赖引用
3. ⚙️ `MainActivity.kt` - 新增路由
4. ⚙️ `compose/MainView.kt` - 添加入口按钮
5. ⚙️ `AndroidManifest.xml` - 添加相机权限声明

***

## 🚀 实施步骤顺序

### Phase 1: 基础设施搭建

1. [ ] 更新 `libs.versions.toml` 添加依赖版本定义
2. [ ] 更新 `app/build.gradle.kts` 引入依赖
3. [ ] 在 `AndroidManifest.xml` 中声明相机权限
4. [ ] 创建 `QRCodeUtils.kt` 工具类

### Phase 2: 导出功能开发

1. [ ] 实现 `encodeDoorsToQR()` 编码方法
2. [ ] 实现 `generateQRCode()` 生成方法
3. [ ] 开发 `QRExportView.kt` 页面 UI
4. [ ] 实现保存到相册和分享功能

### Phase 3: 导入功能开发

1. [ ] 实现 `decodeQRTodoors()` 解码方法
2. [ ] 开发 `QRImportView.kt` 扫描页面 UI
3. [ ] 集成 CameraX + ML Kit 扫描功能
4. [ ] 实现导入确认对话框和数据合并逻辑

### Phase 4: 集成与测试

1. [ ] 在 `MainActivity.kt` 中注册新路由
2. [ ] 在 `MainView.kt` 中添加入口按钮
3. [ ] 测试完整导出→导入流程
4. [ ] 边缘情况测试（空数据、超大数量、损坏二维码等）

***

## ⚠️ 注意事项

1. **二维码容量限制**: 单个二维码最多存储约 3KB 数据（2953 字节），对于少量门禁（<10 个）足够
2. **多门禁处理**: 如果门禁数量过多导致数据超出二维码容量，可考虑：

   * 分多个二维码展示

   * 压缩 JSON（移除空格、缩短 key 名）
3. **向后兼容**: 保持旧版数据迁移逻辑不受影响
4. **用户体验**:

   * 导出时提示"请确保新设备已安装本应用"

   * 导入时提供预览和二次确认机制
5. **错误处理**:

   * 无效二维码格式提示

   * 权限拒绝时的友好提示

   * 数据冲突处理（同名门禁）

***

## 🎨 UI 设计原则

* 遵循 Material Design 3 规范（项目已使用 material3）

* 与现有页面风格保持一致

* 使用项目已有的主题色和排版

* 支持深色模式

* 响应式布局适配不同屏幕尺寸

