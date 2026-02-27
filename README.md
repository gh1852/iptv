# IPTV Android 应用

一个基于 Kotlin + Media3 ExoPlayer 的 IPTV 直播应用，兼容 Android 手机与 Android TV 场景。

## 功能特性

- 支持从在线 M3U 地址拉取频道数据：
  - `https://www.iyouhun.com/tv/zb`
- 按频道分类展示（分组列表）
- 同频道多播放源合并为单个频道项显示
- 播放失败自动切换到该频道下一个源，直到源列表结束
- 切换频道时底部显示频道名称与 Logo，3 秒后自动隐藏
- GitHub Actions 自动构建 Debug APK 并上传产物

## 技术栈

- Kotlin
- Android ViewBinding + XML 布局
- AndroidX Media3 ExoPlayer
- Coil（频道 Logo 加载）
- Kotlin Coroutines

## 版本与配置

- `compileSdk = 34`
- `targetSdk = 34`
- `minSdk = 21`
- Java/Kotlin JVM Target: `17`
- Application ID: `com.jons.iptv`

## 项目结构

```text
.
├── app/
│   ├── src/main/java/com/jons/iptv/
│   │   ├── MainActivity.kt
│   │   ├── data/
│   │   │   ├── Channel.kt
│   │   │   ├── CategoryChannels.kt
│   │   │   ├── M3uParser.kt
│   │   │   └── ChannelRepository.kt
│   │   └── ui/
│   │       ├── CategoryAdapter.kt
│   │       └── ChannelAdapter.kt
│   ├── src/main/res/layout/
│   │   ├── activity_main.xml
│   │   ├── item_category.xml
│   │   └── item_channel.xml
│   └── build.gradle.kts
├── gradle/
├── gradlew
└── .github/workflows/android-build.yml
```

## 本地构建

### 1. 准备环境

- JDK 17
- Android SDK（建议包含以下组件）
  - Platform: Android 34
  - Build-Tools: 34.0.0

### 2. 配置 SDK 路径

在项目根目录创建 `local.properties`（该文件已被 `.gitignore` 忽略）：

```properties
sdk.dir=/你的/Android/Sdk/路径
```

例如（WSL）：

```properties
sdk.dir=/mnt/d/workspace/Android/Sdk
```

### 3. 执行构建

```bash
./gradlew tasks
./gradlew assembleDebug
```

构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## CI 构建（GitHub Actions）

工作流文件：`.github/workflows/android-build.yml`

触发方式：

- push 到 `master`
- Pull Request
- 手动触发 `workflow_dispatch`

执行内容：

1. 拉取代码
2. 安装 JDK 17
3. 安装 Android SDK
4. 执行 `./gradlew assembleDebug`
5. 上传 Debug APK Artifact（`app-debug-apk`）

## 核心逻辑说明

- 频道加载与分组：`app/src/main/java/com/jons/iptv/MainActivity.kt`
- M3U 解析与同名频道多源合并：`app/src/main/java/com/jons/iptv/data/M3uParser.kt`
- 播放失败自动切源：`app/src/main/java/com/jons/iptv/MainActivity.kt`
- 切台底部 Overlay 3 秒自动隐藏：`app/src/main/java/com/jons/iptv/MainActivity.kt`

## 说明

本项目当前为 Debug 构建链路与核心播放功能实现版本。若需发布 Release 版本，可在后续增加签名配置、发布流程和更完整的测试用例。