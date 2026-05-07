# 平安回家 Mix

> 基于 [SafeBaiyun](https://github.com/dogproton/SafeBaiyun) 的增强版 — 更多门、更快传、更好用

广州市白云区蓝牙门禁的离线版本，只需门禁的 MAC 地址和加密 Key 即可开门，无需网络连接。

## 亮点（vs 原版）

| 功能 | 原版 SafeBaiyun | SafeBaiyun Mix |
|:---|:---:|:---:|
| 离线蓝牙开门 | ✅ | ✅ |
| 桌面快捷方式 | ✅ | ✅ |
| 中号/大号小部件 | ✅ | ✅ |
| **多门禁管理** | ❌ 单门 | ✅ 添加/编辑/删除多个门禁 |
| **二维码导出** | ❌ | ✅ 生成 QR + 保存相册 + 分享 |
| **二维码导入** | ❌ | ✅ CameraX 扫码，替换/追加两种模式 |
| **三键快开部件** | ❌ | ✅ 特大号部件，同时展示 3 个门禁 |
| **旧数据自动迁移** | ❌ | ✅ 单门数据无缝升级为多门格式 |
| **CI/CD 自动构建** | ❌ | ✅ GitHub Actions 构建 + 签名发布 |

## 功能特性

### 核心功能

- 🚪 **离线开门** — 无需网络，本地蓝牙 BLE 连接即可开门
- 📱 **多门禁管理** — 支持添加、编辑、删除多个门禁设备，自定义名称（如"家里"、"公司"）
- 🎯 **快捷方式** — 桌面快捷方式一键开门，无需打开 App

### 增强功能（Mix 独有）

- 📲 **二维码导出** — 将所有门禁配置编码为二维码，支持保存到相册或分享给他人
- 📷 **二维码导入** — CameraX + ML Kit 实时扫码，支持「替换全部」或「追加到现有」两种导入模式
- 🖼️ **三键快开部件** — 特大号桌面小部件，同时展示 3 个门禁，每个一键解锁
- 🔄 **旧数据迁移** — 自动将原版单门数据迁移为多门格式，升级无忧
- 🔧 **自动构建发布** — 推送标签即可通过 GitHub Actions 自动构建并发布签名 APK

## 使用方法

1. 提取门禁的 MAC 地址及加密密钥，[获取方式](extract.md)
2. 点击右上角编辑按钮，添加门禁并填入 MAC 和 Key，保存
3. 点击对应门禁的开门按钮即可

> 💡 支持添加多个门禁，每个可自定义名称便于区分。

## 二维码同步

### 导出配置

1. 在主界面底部点击「导出配置」
2. 自动生成包含所有门禁配置的二维码
3. 可保存到相册或直接分享给他人

### 导入配置

1. 在主界面底部点击「扫描导入」
2. 对准二维码即可自动识别
3. 选择导入方式：
   - **替换全部** — 清除现有配置，使用扫码数据
   - **追加到现有** — 保留现有配置，将扫码数据添加到末尾

### 二维码格式

导出的二维码数据格式为 `SBY|<Base64>`，其中 Base64 解码后为门禁列表的 JSON 序列化（`kotlinx.serialization`），与 `DoorDevice` 数据模型对应。第三方工具可据此解析。

## 桌面小部件

本应用提供三种尺寸的桌面小部件（基于 AndroidX Glance，需 Android 12+）：

| 尺寸 | 说明 |
|:---|:---|
| 中号 | 紧凑横条，显示"白云通"+ 一键解锁按钮 |
| 大号 | 带广州塔插图 + 一键解锁按钮 |
| **特大号** | 三键快开，同时展示 3 个门禁，各自独立解锁（Mix 独有） |

> 在桌面长按空白处 → 添加小部件 → 选择「平安回家」即可。

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **导航**：Navigation Compose
- **序列化**：kotlinx.serialization
- **蓝牙**：Android BLE (connectGatt)
- **二维码生成**：ZXing
- **二维码扫描**：CameraX + ML Kit Barcode Scanning
- **桌面部件**：AndroidX Glance
- **CI/CD**：GitHub Actions

## 下载

在 [Releases](https://github.com/yotong-max/SafeBaiyun-Mix/releases) 页面下载最新的 APK：

- **app-release.apk** — 已签名发布版本（推荐）
- **app-debug.apk** — 调试版本

## 本地构建

```bash
# 克隆项目
git clone https://github.com/yotong-max/SafeBaiyun-Mix.git
cd SafeBaiyun-Mix

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本（需配置签名环境变量）
./gradlew assembleRelease
```

Release 签名需设置以下环境变量：`KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

## 构建与发布

本项目使用 GitHub Actions 进行自动构建和发布：

- 推送到 `main` 分支或创建 PR 时自动构建 APK
- 推送 `v*` 格式标签时自动构建并发布到 GitHub Releases

## 版本历史

### v1.1 (2026-05-06)

- ✨ 新增多门禁管理（添加/编辑/删除）
- ✨ 新增二维码导出门禁配置（生成 QR + 保存相册 + 分享）
- ✨ 新增二维码扫描导入（CameraX + ML Kit 实时扫码，替换/追加模式）
- ✨ 新增特大号桌面小部件（三键快开）
- ✨ 新增旧版单门数据自动迁移为多门格式
- ✨ 新增 GitHub Actions 自动构建与签名发布
- 🎨 Material 3 主题适配
- 🔒 添加 Release 签名配置

## 致谢

- [dogproton/SafeBaiyun](https://github.com/dogproton/SafeBaiyun) — 原版项目，本仓库基于其增强
