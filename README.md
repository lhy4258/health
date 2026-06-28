# 健康运动

健康运动是一款原生 Android 健康社交应用，围绕运动记录、运动计划、饮食记录、好友互动、群聊、动态、相册和个人主页管理展开。项目使用 Java 编写，数据与即时通信能力主要基于 LeanCloud，界面采用 AndroidX、Material Components 和传统 XML 布局实现。

## 功能概览

- 用户认证：支持注册、登录、邮箱验证提示、密码重置邮件、登出、登录失败锁定和单设备登录检测。
- 运动模块：支持步行、跑步、骑行、健身四类运动；可计时记录运动时长、估算卡路里和距离，并在首页展示累计时长。
- 运动热力图：运动首页提供日、周、月维度切换，通过环形热力图展示 0-24 点运动分布。
- 计划与饮食：支持每日运动计划编辑、周计划自动汇总；支持饮食记录、三餐与加餐管理、图片上传和周视图统计。
- 好友与消息：支持好友搜索、二维码添加好友、好友请求处理、好友删除、会话入口和群组列表。
- 聊天：基于 LeanCloud IM 支持文本消息、图片消息、历史消息加载和头像缓存。
- 动态社区：支持发布动态、图片预览、点赞、评论、分享、删除、收藏和分页加载。
- 个人中心：支持头像、背景图、昵称、签名、二维码名片、扫码、动态、收藏、相册、设置和主页点赞统计。
- 相册与收藏：支持照片相册管理、动态收藏和收藏列表展示。

## 技术栈

- 语言：Java
- 构建：Gradle Wrapper 9.1.0、Android Gradle Plugin 9.0.0
- Android：compileSdk 35、minSdk 26、targetSdk 35
- UI：AndroidX AppCompat、Material Components、ConstraintLayout、ViewPager2、RecyclerView、SwipeRefreshLayout
- 后端与实时能力：LeanCloud Storage、LeanCloud Realtime/IM、LiveQuery
- 网络接口定义：Retrofit、OkHttp、Gson
- 图表与可视化：MPAndroidChart、自定义 `AnnularHeatMapView`
- 图片：Glide、Android Photo Picker/系统选择器、LeanCloud File
- 二维码：ZXing
- 测试：JUnit、AndroidX Test、Espresso

## 运行环境

- Android Studio，建议使用带有新版 Android Gradle Plugin 支持的版本。
- JDK 17 或 Android Studio 自带 JDK。
- Android SDK Platform 35。
- 一台 Android 8.0（API 26）及以上设备或模拟器。
- 可访问 LeanCloud 服务的网络环境。

## 快速开始

1. 使用 Android Studio 打开项目根目录。

2. 确认本地 SDK 配置存在。Android Studio 通常会自动生成 `local.properties`：

   ```properties
   sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
   ```

3. 同步 Gradle 依赖。

4. 运行 Debug 包：

   ```powershell
   .\gradlew.bat assembleDebug
   ```

   Debug APK 默认输出到：

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

5. 安装到已连接设备：

   ```powershell
   .\gradlew.bat installDebug
   ```

在 macOS 或 Linux 上可使用：

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## LeanCloud 配置

应用启动时在 [MyApplication.java](app/src/main/java/com/example/health/MyApplication.java) 中完成 LeanCloud 初始化，并注册项目的数据模型：

```java
LeanCloud.initialize(this, "APP_ID", "APP_KEY", "SERVER_URL");
```

当前仓库中已经写入一组 LeanCloud 配置，可直接用于当前开发环境。如果要部署到自己的 LeanCloud 应用，请替换为自己的 App ID、App Key 和 Server URL，并确认已启用以下能力：

- 数据存储，用于用户、运动、计划、饮食、动态、评论、相册、收藏、点赞等数据。
- 文件存储，用于头像、背景图、动态图片、饮食图片和聊天图片。
- 即时通信，用于私聊和群聊。
- LiveQuery，用于好友信息、点赞数量等实时刷新。
- 邮件服务，用于注册邮箱验证和密码重置邮件。

生产环境建议不要把 LeanCloud 密钥硬编码在源码里，可以改为从安全的构建配置或服务端下发。

## 项目结构

```text
health/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/health/
│       │   │   ├── MyApplication.java
│       │   │   ├── auth/
│       │   │   ├── data/
│       │   │   │   ├── api/
│       │   │   │   └── model/
│       │   │   ├── ui/
│       │   │   │   ├── plandiet/
│       │   │   │   ├── sport/
│       │   │   │   └── widget/
│       │   │   └── utils/
│       │   └── res/
│       │       ├── drawable/
│       │       ├── layout/
│       │       ├── menu/
│       │       └── values/
│       ├── test/
│       └── androidTest/
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew.bat
```

## 关键模块说明

- `auth/AuthManager.java`：注册、登录、登出、密码重置、邮箱验证、失败次数限制和单设备登录校验。
- `MyApplication.java`：LeanCloud SDK 初始化与模型注册。
- `ui/MainActivity.java`：底部导航入口，包含运动、饮食计划、好友、个人中心四个 Tab。
- `ui/SportFragment.java` 和 `ui/sport/`：运动统计、热力图、运动详情、计时记录和历史记录。
- `ui/plandiet/PlanListFragment.java`：每日运动计划和周计划汇总。
- `ui/plandiet/DietListFragment.java`：饮食记录、三餐/加餐、图片上传和周视图统计。
- `ui/FriendsFragment.java`、`FriendListFragment.java`、`FriendRequestsFragment.java`、`GroupListFragment.java`：好友、好友请求、群组和消息入口。
- `ui/GroupChatActivity.java`：LeanCloud IM 聊天页面，支持文字和图片消息。
- `ui/MomentsActivity.java`、`PublishMomentActivity.java`、`MomentDetailActivity.java`：动态列表、发布、详情、评论、点赞、收藏和分享。
- `ui/ProfileFragment.java`、`ProfileDetailActivity.java`、`UserProfileActivity.java`：个人资料、二维码、扫码添加好友、主页点赞和他人主页。
- `ui/AlbumActivity.java`、`AlbumDetailActivity.java`、`FavoriteActivity.java`：相册、照片和收藏。
- `ui/widget/AnnularHeatMapView.java`：运动热力图自定义视图。

## 数据模型

项目会向 LeanCloud 注册并使用以下主要数据模型：

- `_User` / `User`：用户资料、邮箱、手机号、头像、背景图、签名、当前设备 ID。
- `LoginAttempt`：登录失败次数与账号锁定状态。
- `SportRecord`：运动类型、开始时间、结束时间、时长、卡路里、距离和所属用户。
- `PlanRecord`：每日计划、周计划、四类运动分钟数、日期和备注。
- `DietRecord`：饮食日期、三餐/加餐 JSON、图片和备注。
- `Moment`：动态作者、内容、图片、点赞用户列表。
- `Comment`：动态评论。
- `Album`、`Photo`：相册和照片。
- `Favorite`：收藏的动态内容。
- `LikeDailyRecord`、`ProfileLike`：主页点赞记录、每日限制和总数统计。
- `Friend`、`FriendRequest`、`UserConversation`：好友关系、好友请求和会话记录，以普通 `LCObject` 方式使用。

`data/api/` 中还保留了 `FriendApi`、`GroupApi`、`MessageApi` 等 Retrofit 接口定义，面向 LeanCloud 云引擎 REST API；当前多数页面直接通过 LeanCloud SDK 和 IM SDK 完成读写。

## 常用命令

```powershell
# 构建 Debug APK
.\gradlew.bat assembleDebug

# 运行本地单元测试
.\gradlew.bat testDebugUnitTest

# 运行设备/模拟器上的仪器测试
.\gradlew.bat connectedDebugAndroidTest

# 构建 Release APK
.\gradlew.bat assembleRelease
```

Release 包发布前请在 Android Studio 或 Gradle 中配置正式签名信息。

## 测试说明

当前仓库包含：

- `ExampleUnitTest`：基础本地单元测试。
- `ExampleInstrumentedTest`：校验应用包名。
- `FavoriteRenderingTest`：校验收藏条目布局的关键视图是否存在。

涉及 LeanCloud、即时通信、扫码、图片选择、文件上传等能力的功能需要在真机或模拟器上结合可用账号和网络环境手动验证。

## 开发注意事项

- `local.properties` 是本机 SDK 路径配置，不应提交到版本库。
- `AndroidManifest.xml` 当前允许明文流量 `usesCleartextTraffic="true"`，生产环境需要根据实际接口安全策略重新评估。
- LeanCloud 配置当前写在源码中，生产环境建议迁移到更安全的配置方案。
- 饮食图片、聊天图片、头像和背景图会压缩后上传到 LeanCloud File，单张图片按代码逻辑控制在 5 MB 以内。
- 运动记录中的距离和卡路里为按运动类型、时长和默认体重计算的估算值，不等同于专业健康设备数据。
- 好友、会话、点赞和部分个人资料刷新依赖 LiveQuery，调试时需确认 LeanCloud 控制台已启用相关服务。

## 许可证

当前仓库未包含明确许可证文件。发布、分发或商用前请先补充许可证与第三方依赖合规说明。
