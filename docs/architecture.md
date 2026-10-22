# 烁学项目架构指南

> **2026-10-22 Android UI 更新**：用户最新要求直接参考截图，原生首页、题库侧栏、错题分层、答题卡/结果、资源与会员设置页已同步调整。主导航为“首页 / 题库 / 资源 / 统计 / 我的”，错题和收藏从首页进入；下文旧视觉与导航描述不再作为 Android 新页面规范。详见 [设计、实施与验证记录](../apps/android/design/README.md)。接口及题库保护保留；原生真机与无障碍实测仍待完成。
> **2026-10-22 生产状态**：Web、API 与机器人密钥已更新；普通题 133,444 道、试卷题 834 道已统一格式并使用 AES-256-GCM 存储加密，搜索索引与服务端会员校验已启用。生产接口 40 项回归通过。部署、备份和验收详情见 [deployment-2026-10-22.md](deployment-2026-10-22.md)；Android 调试包已生成，尚未在连接设备上验收。


> **本文档目的**：让任何 AI 智能体或新开发者在开启新会话时，通过阅读此文件即可立刻掌握项目全局架构、技术栈、编码规范和开发约定。
> **最后更新**：2026-03-21 (v6 — 强化邮件绑定、通知生态、用户签名与全局色彩净化)

---

## 一、项目概述

**烁学 (Zhzgo Study)** 是一个全平台学习系统，包含四个核心端：

| 端              | 技术                     | 目录位置                                                     | 运行地址                     |
| --------------- | ------------------------ | ------------------------------------------------------------ | ---------------------------- |
| **后端 API**    | Node.js + Express        | `D:\codeStudy\code\study.zhzgo.cn_83FYC\apps\server` | `https://studyapi.zhzgo.cn/` |
| **前端 Web**    | Vue 3 + Vite             | `D:\codeStudy\code\study.zhzgo.cn_83FYC\apps\web` | `https://study.zhzgo.cn/`    |
| **Android App** | Kotlin + Jetpack Compose | `D:\codeStudy\code\study.zhzgo.cn_83FYC\apps\android`         | APK 分发                     |
| **QQ Bot**      | Node.js                  | `D:\codeStudy\code\study.zhzgo.cn_83FYC\apps\bot`              | 独立进程                     |

**数据流**：`Android / Vue Web` ↔ `Node.js Express API` ↔ `MySQL 数据库 (study_center)`

---

## 二、技术栈速查

### 后端 (Node.js)

| 分类     | 技术                    | 版本                                |
| -------- | ----------------------- | ----------------------------------- |
| 框架     | Express                 | ^4.18.2                             |
| 数据库   | MySQL (mysql2/promise)  | ^3.6.5                              |
| 认证     | JWT + bcryptjs          | 双令牌：Access 15min + Refresh 30天 |
| 行为验证 | 极验 (Geetest 4.0) SDK  | `/validate` 服务端二次校验          |
| 文件上传 | multer                  | ^2.0.2                              |
| 文档解析 | pdf-parse, xlsx         |                                     |
| 消息推送 | JPush SDK (极光推送)    | ^4.0.0 (Node SDK)                   |
| 无 ORM   | 直接 `pool.query()` SQL |                                     |

### 前端 Web (Vue 3)

| 分类     | 技术                                     | 版本              |
| -------- | ---------------------------------------- | ----------------- |
| 核心     | Vue 3 Composition API (`<script setup>`) | ^3.3.11           |
| 构建     | Vite                                     | ^5.0.8            |
| 路由     | Vue Router 4 (History 模式)              | ^4.2.5            |
| 状态管理 | Pinia (已安装，轻度使用) + localStorage  | ^2.1.7            |
| UI       | TailwindCSS 3 + CSS 变量主题             | ^3.4.0            |
| 安全防护 | 极验 (Geetest 4.0) 前端 Web 探针         | 全局注入 `gt4.js` |
| 图表     | ECharts                                  | ^6.0.0            |
| Markdown | markdown-it + KaTeX                      |                   |
| 富文本   | Quill (@vueup/vue-quill)                 | ^1.2.0            |

### Android 原生端

| 分类     | 技术                                       | 版本/说明         |
| -------- | ------------------------------------------ | ----------------- |
| 语言     | Kotlin (JDK 17)                            | Coroutines + Flow |
| UI       | Jetpack Compose + Material 3               | 声明式 UI         |
| 导航     | Navigation Compose                         | 2.8.0-beta02      |
| 网络     | Retrofit 2 + OkHttp 4 + Gson               |                   |
| 本地存储 | Room Database (KSP), DataStore Preferences |                   |
| 多媒体   | FFmpeg Kit + Media3 (ExoPlayer)            | H.264/MP3/播放器  |
| ML       | ML Kit Subject Segmentation                | 背景抠图          |
| 图片     | Coil Compose                               |                   |
| Markdown | Markwon 4.6.2 + LaTeX 插件                 |                   |
| 社交     | QQ Open SDK 3.52.0                         |                   |
| 安全防护 | 极验 (Geetest 4.0) Android SDK             | aar 本地引入      |
| 消息推送 | JPush Android SDK 6.0.1 (本地 JAR)         | 包含 JCore 4.2.0  |
| SDK      | minSdk 24 / targetSdk 36 / compileSdk 36   |                   |
| 构建     | Gradle KTS + KSP + R8 (minify+shrink)      |                   |

---

## 三、目录结构

### 3.1 后端 `server/`

```
server/
├── app.js                    # 入口：Express + 路由挂载 + CORS
├── config/db.js              # MySQL 连接池
├── middleware/authMiddleware.js  # JWT 验证 + Admin 权限校验
├── services/
│   └── notificationService.js # JPush 消息推送封装
├── routes/
│   ├── auth.js               # 注册/登录/QQ登录/Token刷新/改密
│   ├── data.js               # 核心业务 (1138行, 50+端点)
│   ├── public.js             # 公开接口
│   ├── upload.js             # 文件上传
│   ├── app.js                # App 更新
│   ├── bot.js                # QQ Bot
│   └── admin_new/            # 管理后台 (11个子模块)
│       ├── index.js          #   路由聚合
│       ├── structure.js, import.js, settings.js, stats.js,
│       ├── content.js, app_update.js, cards.js, exams.js, access_control.js
├── scripts/                  # 运维/迁移脚本 (21个)
└── public/                   # 静态资源
```

**API 路由前缀映射**：

- `/api/auth` → `routes/auth.js`
- `/api/data` → `routes/data.js`
- `/api/public` → `routes/public.js`
- `/api/admin` → `routes/admin_new/`
- `/api/upload` → `routes/upload.js`
- `/api/app` → `routes/app.js`
- `/api/bot` → `routes/bot.js`

### 3.2 前端 `client/src/`

```
src/
├── main.js                   # Vue 入口 (createApp + Pinia + Router)
├── App.vue                   # 根组件 (暗色模式 + demo横幅)
├── style.css                 # Tailwind 基础
├── assets/theme.css          # CSS变量主题 (亮/暗色 + 玻璃拟态)
├── api/index.js              # Axios 封装 (拦截器 + Token刷新)
├── router/index.js           # 路由表 + 认证守卫
├── constants/daily-content.js
├── utils/
│   ├── question-normalizer.js
│   ├── export-docx.js
│   └── sessionExpired.js     # 会话过期弹窗(单例)
├── components/ui/            # 通用 UI 组件 (4个)
│   ├── ConfirmDialog.vue, Toast.vue, IconPicker.vue, MarkdownRenderer.vue
└── views/                    # 页面 (12个 + admin + layouts)
    ├── LandingView, LoginView, HomeView, ExamView, StatsView...
    ├── layouts/ (AppLayout, AuthLayout)
    └── admin/ (9个管理页 + AdminLayout)
```

### 3.3 安卓端 `app/src/main/java/cn/zhzgo/study/`

```
cn/zhzgo/study/
├── MainActivity.kt           # 唯一 Activity (入口)
├── MyApplication.kt          # Application 子类
├── data/                     # 数据层
│   ├── Models.kt             #   API 模型 (30+ data class，全部 @Immutable)
│   ├── UserPreferences.kt    #   DataStore 封装 (15个 preference key，含 category_order, tools_order)
│   ├── AppDatabase.kt        #   Room 数据库
│   ├── ToolDao.kt, ToolEntity.kt, UpdateModels.kt
├── network/                  # 网络层
│   ├── ApiService.kt         #   Retrofit 接口 (40+ suspend fun)
│   └── RetrofitClient.kt     #   Retrofit 工厂 + AuthInterceptor
├── ui/                       # UI 层
│   ├── AppNavigation.kt      #   全局路由 (40+ 路由)
│   ├── theme/ (Color.kt, Theme.kt, Type.kt)
│   ├── components/ (Common.kt, QuestionComponents.kt, LoadingOverlay.kt...)
│   ├── screens/ (28个 Screen)
│   │   └── tools/ (26个工具 Screen)
│   └── viewmodels/ (9个 ViewModel)
├── utils/ (QQLoginHelper, UpdateManager, UpdateReceiver, TinkerManager)
└── service/
    └── PushNotificationService.kt # JPush 消息接收逻辑
```

---

## 四、核心开发规范

### 4.1 如何新增一个 API 接口

**后端**：

1. 在 `routes/data.js` 或相应的 `routes/admin_new/*.js` 中添加路由
2. 需要认证的接口加 `verifyToken` 中间件，管理接口加 `isAdmin`
3. 直接使用 `pool.query()` 写 SQL，无 ORM

**前端 Vue**：

1. 在 `src/api/index.js` 的对应导出对象（`data`/`admin`/`auth`）中添加方法
2. 页面中 `import { data } from '@/api'` 调用

**Android**：

1. 在 `ApiService.kt` 中添加 `suspend fun`，注解指定 HTTP 方法和路径
2. 如需新数据类，在 `Models.kt` 中添加，**必须标注 `@Immutable`**
3. 在对应 ViewModel 中调用 `apiService.xxx()`

### 4.2 如何新增一个前端页面 (Vue)

1. 在 `src/views/` 下创建 `XxxView.vue`（使用 `<script setup>` + Composition API）
2. 在 `src/router/index.js` 中添加路由对象：
   ```javascript
   { path: '/xxx', name: 'xxx', component: () => import('../views/XxxView.vue'), meta: { requiresAuth: true } }
   ```
3. 样式使用 TailwindCSS 类名 + `theme.css` 中定义的 CSS 变量
4. 暗色模式通过 `dark:` 前缀或 CSS 变量自动适配

### 4.3 如何新增一个安卓页面 (Compose Screen)

1. 在 `ui/screens/` 下创建 `XxxScreen.kt`：
   ```kotlin
   @Composable
   fun XxxScreen(onBack: () -> Unit) { ... }
   ```
2. 在 `AppNavigation.kt` 的 `NavHost` 内添加：
   ```kotlin
   composable("xxx") { XxxScreen(onBack = { navController.navigateUp() }) }
   ```
3. 如需 ViewModel，创建并继承 `AndroidViewModel(application)`
4. 使用 `RetrofitClient.create(application)` 获取 API（在 init 中调用一次）
5. 状态用 `MutableStateFlow` + `collectAsState()` 驱动 UI

### 4.4 如何新增一个工具 (Tools)

**Android**：

1. 在 `ui/screens/tools/` 下创建 `XxxScreen.kt`
2. 在 `AppNavigation.kt` 中添加 `composable("tools/xxx") { ... }`
3. 在 `ToolsListScreen.kt` 的工具列表中注册名称和路由

---

## 五、关键编码约定

### 5.1 认证流程与极验 (Geetest 4.0) 防护

为了防御自动化机器人的恶意攻击，项目在发信和鉴权相关的四大涉敏接口 (`/login`, `/register`, `/send-code`, `/forgot-password`) 中全量部署了 **极验行为验证 4.0 (Geetest)**。

**完整认证数据流**：

```text
1. 客户端拦截 → 取消默认点击行径，拉起极验对话框 (Web 端触发 initGeetest4，Android 触发 GTCaptchaUtils.verify)。
2. 用户交互验证 → 滑块/拼图验证通过后，取得极验三大件 (lot_number, captcha_output, pass_token, gen_time)。
3. 请求附加 → 客户端发起核心请求时，将 Geetest 参数合并提交至业务后端。
4. 后端二次校验 → auth.js 路由先阻断请求，调用 geetestService 结合服务端密钥计算签名，向极验 API发起二次查验。
5. 验证成功 → 下发 accessToken (15min) + refreshToken (30天)
      → 存储: Vue=localStorage / Android=DataStore
```

- **Vue 端**：`utils/geetest.js` 封装 `verifyGeetest()` 提供 Promise。
- **Android 端**：`utils/GTCaptchaUtils.kt` 通过 `suspendCancellableCoroutine` 使传统 Callback SDK 完全适配协程模型。
- **后端**：`services/geetestService.js` 中使用 `client_type` 自动区分 Android 和 Web 的私钥，提供无视异常静默拒绝的高容错校验机制。

### 5.1.1 自动鉴权刷新

- **Vue 端**：Axios 拦截器 + 并发请求排队 (`failedQueue`)
- **Android 端**：OkHttp `AuthInterceptor` + `synchronized(lock)` 线程安全
- **后端**：`verifyToken` 中间件解析 JWT，`isAdmin` 校验角色

### 5.2 状态管理模式

| 平台    | 全局状态                           | 页面状态                        |
| ------- | ---------------------------------- | ------------------------------- |
| Vue     | `localStorage` (token/user/theme)  | `ref` / `reactive` 局部变量     |
| Android | `DataStore Preferences` (13个 key) | `MutableStateFlow` in ViewModel |

### 5.3 样式规范

**Vue 前端**：

- TailwindCSS 类名为主
- 颜色使用 CSS 变量 (`--primary`, `--bg-body` 等)
- 暗色模式：`darkMode: 'class'`，通过 `html.dark` 切换
- 自定义工具类：`.glass-card`、`.card-hover`、`.text-gradient`

**Android 端**（严格执行！）：

- **配色**：全局黑白灰单色调，参考chatgpt的APP的ui，ios端的ui，简洁带高级感 (`onSurface` + 不同 `alpha`)
- **图标容器 (Icon Container)**：小工具图标统一采用**主色 (Primary)** + **极淡主色背景 (10% Alpha Primary)**，取消五颜六色的独立配色，确保视觉高度统一且具备高级感。
- **加载**：使用骨架屏 (Shimmer)，不用转圈/Loading Overlay
- **卡片**：边框 `onSurface.copy(alpha=0.1f)`，圆角 8-12dp，无/极轻阴影
- **分隔线**：0.5dp，`onSurface.copy(alpha=0.05f)`
- **Tab**：胶囊式分段选择器（浅灰底 + 白色选中块），不用系统 `TabRow`
- **字体**：标题 `FontWeight.Bold`，正文 `Medium/Normal`，辅助信息低透明度灰色
- **多媒体**：视频播放统一使用 `androidx.media3.exoplayer.ExoPlayer`，UI 采用 `PlayerView`。
- **文件下载**：使用 `DownloadManager` 时，**必须添加 User-Agent 请求头**（模拟移动端浏览器），否则快手等平台会返回 403。同时使用 `MimeTypeMap` 动态识别扩展名，严禁硬编码 `.jpg`，防止 webp 资源触发安装提示。
- **间距**：元素间 12-20dp，留白充足
- **Plus 尊享视觉 (Premium UI)**：
  - **极光背景 (Aurora Background)**：使用渐变动画背景 (`flowingLinearGradient`) 增强会员身份感。
  - **玻璃拟态 (Glassmorphism)**：会员卡片采用半透明磨砂质感，结合 0.5dp 白色边框增强对比度。
  - **动态扫光 (Shimmer Badge)**：`PlusBadge` 具有周期性流光效果，提升精致感。

### 5.4 Data Class 约定 (Android)

- **所有** API 数据类必须标注 `@Immutable`（Compose 稳定性优化）
- 字段命名与后端 JSON 一致（snake_case），Gson 自动映射
- 需要向后兼容时可添加别名字段 + 兼容读取方法

---

## 六、⚠️ 开发陷阱清单

| #   | 陷阱                            | 说明                                                                                                                                                                             |
| --- | ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | **Compose 图标导入**            | 使用 `Icons.Default.Xxx` 时必须手动 `import androidx.compose.material.icons.filled.Xxx`                                                                                          |
| 2   | **RetrofitClient 非单例**       | `RetrofitClient.create(context)` 每次创建新实例，必须在 ViewModel init 中只调用一次                                                                                              |
| 3   | **API 修改需三端同步**          | 改了后端 routes 返回结构 → 必须同步更新 Android `Models.kt` + Vue `api/index.js`                                                                                                 |
| 4   | **构建命令**                    | 验证 Kotlin 代码用 `./gradlew compileDebugKotlin`，不要用 `assembleDebug`（Java 21 可能报 toolchain 异常）                                                                       |
| 5   | **Android 网络请求**            | 使用 `RetrofitClient.create(context)` 而非全局单例，Context 必须从 Compose 层传递                                                                                                |
| 6   | **Demo 角色写保护**             | `isAdmin` 中间件对 demo 角色仅放行 GET，前端 `App.vue` 显示橙色警告横幅                                                                                                          |
| 7   | **Vue HTML 响应检测**           | Axios 响应拦截器会检测 `<!doctype html>` 返回，防止 Nginx 配错时的静默失败                                                                                                       |
| 8   | **AuthInterceptor runBlocking** | OkHttp 拦截器中用 `runBlocking` 读 DataStore，若 DataStore 阻塞可能导致请求超时                                                                                                  |
| 9   | **Android 分区存储**            | 文件操作必须通过 `MediaUtils` 适配 Android 11+ Scoped Storage                                                                                                                    |
| 10  | **Vue 暗色模式**                | 通过 `html` 元素的 `class="dark"` 控制，不是 `prefers-color-scheme` 媒体查询                                                                                                     |
| 11  | **DownloadManager 403**         | 快手等平台限制无 UA 的下载请求，必须 `addRequestHeader("User-Agent", ...)` 模拟手机浏览器。                                                                                      |
| 12  | **SSR 爬虫识别误导**            | 快手 SSR 页面可能同时包含图集和视频标记，解析逻辑应**优先尝试提取视频 URL**，避免将视频误判为图集。                                                                              |
| 13  | **保存图片触发安装**            | 若 MimeType 设为 `image/jpeg` 但实际 URL 是 `.apk` 或系统识别错误，会跳转安装。需确保后缀名与实际内容匹配。                                                                      |
| 14  | **测试文件必须清理**            | 开发过程中产生的临时测试文件、调试脚本等，在验证完成后必须自行删除，**严禁遗留在项目目录中**。                                                                                   |
| 15  | **MySQL 时间格式陷阱**          | 后端写入 `subscription_end_date` 时，必须使用 `YYYY-MM-DD HH:mm:ss` 格式。避免使用 ISO 字符串或 Date 对象直写，否则 Linux/Docker 环境下的 MySQL 可能因区域设置不同导致解析失败。 |
| 16  | **隐私保护 (Privacy)**          | `UserProfileScreen` 中，**禁止**显示非本人用户的 `subscription_end_date`。会员状态应通过 `PlusBadge` 呈现，而非具体的到期时间。                                                  |
| 17  | **AI 智能体测试清理**           | **AI 智能体在用完测试文件、验证脚本或临时文件后必须立即删除**，严禁将其留在项目目录中，保持代码库纯净。                                                                          |

---

## 七、快速命令参考

```bash
# === 后端 ===
cd apps/server
npm run dev                     # 启动开发服务器 (nodemon)
npm start                       # 生产启动
npm run seed                    # 填充种子数据

# === 前端 ===
cd apps/web
npm run dev                     # Vite 开发服务器
npm run build                   # 生产构建 → dist/

# === Android ===
cd D:\codeStudy\code\study.zhzgo.cn_83FYC\apps\android
./gradlew compileDebugKotlin    # 快速验证 Kotlin 编译
./gradlew clean assembleDebug   # 完整 Debug APK 构建 (可能有 toolchain 问题)
```

---

## 八、Vue 前端路由表

| 路径                    | 组件                    | 权限     |
| ----------------------- | ----------------------- | -------- |
| `/`                     | LandingView             | 公开     |
| `/login`                | LoginView               | 公开     |
| `/register`             | RegisterView            | 公开     |
| `/home`                 | HomeView                | 需登录   |
| `/exam/:subjectId`      | ExamView                | 需登录   |
| `/stats`                | StatsView               | 需登录   |
| `/analysis`             | AnalysisView            | 需登录   |
| `/data`                 | DataManageView          | 需登录   |
| `/article/:id`          | ArticleView             | 公开     |
| `/download`             | AppDownloadView         | 公开     |
| `/agreement`            | UserAgreementView       | 公开     |
| `/privacy`              | PrivacyPolicyView       | 公开     |
| `/admin`                | AdminLayout             | 需管理员 |
| `/admin/users`          | UserManagement          | 需管理员 |
| `/admin/questions`      | QuestionManagement      | 需管理员 |
| `/admin/settings`       | SettingsView            | 需管理员 |
| `/admin/structure`      | StructureManagement     | 需管理员 |
| `/admin/app-update`     | AppManagement           | 需管理员 |
| `/admin/articles`       | ArticleManagement       | 需管理员 |
| `/admin/comments`       | CommentManagement       | 需管理员 |
| `/admin/exams`          | ExamManagement          | 需管理员 |
| `/admin/access-control` | AccessControlManagement | 需管理员 |

## 九、Android 路由表

**底部导航 Tab**：`home`(首页) / `study`(题库) / `resources`(资源) / `favorites`(收藏)

**认证**：`login` / `register` / `bind_qq/{openId}`

**学习**：`question/{subjectId}/{subjectName}` / `favorite_question` / `leaderboard`

**内容**：`article/{articleId}` / `user_profile/{userId}` / `contribution`

**设置**：`settings` / `account_settings` / `stats` / `data_center` / `webview/{url}/{title}` / `subscription_plus`

**工具集** (`tools/` 前缀，27 条)：
`base_converter` / `bmi_calculator` / `calculator` / `color_picker` / `date_calculator` / `dev_http_test` / `dev_json_regex` / `doc_image_to_pdf` / `doc_markdown` / `doc_pdf_to_image` / `image_compress` / `image_filter` / `image_segment` / `image_watermark` / `kinship_calculator` / `media_audio_format` / `media_video_compress` / `media_video_to_audio` / `password_generator` / `random_generator` / `security_codec` / `security_crypto` / `security_jwt` / `stopwatch` / `text_counter` / `timestamp_converter` / `unit_converter` / `video_parser`

---

## 十、数据库设计与实际数据剖析

项目使用 MySQL 关系型数据库，当前包含 **20** 张核心业务表。以下是关键表结构与实际数据特征分析：

### 10.1 核心题库数据 (Questions & Subjects)

- **`questions` 表**: 项目中**最大的数据表**（当前实测数据约 **9.5万+** 条）。
  - 关键字段：`subject_id` (关联科目), `type` (题型：`single`/`multiple`/`judge`/`fill`/`material`/`sort`), `difficulty`, `content` (题干), `options` (选项，**JSON 格式存储，各题型结构严谨但差异极大**), `answer` (答案，**格式与数据类型随题型剧烈变化**), `analysis` (解析，部分为 Markdown 格式), `tags`。
- **⚠️ 关键开发陷阱 — 题型数据结构异构性 (Detailed Data Formats)**：

| 题型 (`type`)         | `options` 结构    | `answer` 格式        | 示例                                                       |
| :-------------------- | :---------------- | :------------------- | :--------------------------------------------------------- |
| **`single`** (单选)   | JSON 对象 (Map)   | 字符串 (A-Z)         | `opt:{"A":"..","B":".."}`, `ans:"A"`                       |
| **`multiple`** (多选) | JSON 对象 (Map)   | 逗号分隔字符串       | `opt:{"A":"..","B":".."}`, `ans:"A,C"`                     |
| **`judge`** (判断)    | JSON 数组 (Array) | JSON 字符串数组      | `opt:[{"label":"A","content":"正确"}...]`, `ans:"[\"A\"]"` |
| **`fill`** (填空)     | 空数组 `[]`       | 纯文本字符串         | `opt:[]`, `ans:"南京"`                                     |
| **`sort`** (排序)     | JSON 数组 (Array) | JSON 字符串数组      | `opt:[{"label":"A"}]`, `ans:"[\"第一步\",\"第二步\"]"`     |
| **`material`** (材料) | 空或按子题定义    | 结合 `sub_questions` | 复合结构                                                   |

- **具体 JSON 示例**：
  - **单选题**：
    ```json
    {
      "type": "single",
      "content": "计算机之父是？",
      "options": { "A": "图灵", "B": "冯·诺依曼", "C": "香农" },
      "answer": "B"
    }
    ```
  - **多选题**：
    ```json
    {
      "type": "multiple",
      "content": "属于输入设备的是？",
      "options": { "A": "鼠标", "B": "键盘", "C": "显示器", "D": "打印机" },
      "answer": "A,B"
    }
    ```
  - **判断题** (注意双重序列化倾向)：
    ```json
    {
      "type": "judge",
      "content": "1+1=2 对吗？",
      "options": [
        { "label": "A", "content": "正确" },
        { "label": "B", "content": "错误" }
      ],
      "answer": "[\"A\"]"
    }
    ```

- **💡 前端容错处理方案 (Frontend Normalization Pipeline)**：
  为应对基础架构演进中的数据差异，`utils/question-normalizer.js` 实施了**强制运行时归一化**：
  1. **解析保底**：对 `options` 和 `answer` 进行 `try-parse` 逻辑。
  2. **选项对象化**：将 `judge` 型的 `[{label: 'A', content: '...'}]` 数组拍平为标准 `{'A': '...'}` 以便 UI 渲染统一。
  3. **答案清洗**：多选题利用正则 `/[^A-Z]/g` 统一转换为大写字母数组。
     _(注：后继跨端开发如 Android，必须参考此清洗逻辑以防反序列化崩溃。)_

- **`subjects` 表**: 共 500+ 个科目（如“交职院万维系统两千题”），通过 `major_id` 关联至 18 个主修专业 (`majors` 表)。

### 10.2 用户与认证系统 (Users & Auth)

- **`users` 表**: 存储基础账户信息（当前 8 位注册用户）。
  - `role` 枚举：`user`（普通用户）/ `admin`（管理员）/ `demo`（只读演示者）。
  - 支持绑定 QQ（`qq_openid`, `avatar_icon`, `nickname` 等独立字段存放）。
  - `password` 使用 bcrypt Hash 加密（如 `$2a$10$...`）。
- **`refresh_tokens` 表**: 实现双令牌（Access+Refresh）机制的核心表，存储设备长效登录态，包含 `expires_at`。

### 10.3 学学学学记录与状态 (Progress & Favorites)

- **`user_progress` 表**: 记录用户的答题历史（当前近 500 条）。字段包括 `user_id`, `question_id`, `is_correct` (tinyint), `answer` (用户的实际作答，如 `["C"]`)。支持实时同步和多端进度对齐。
- **`user_favorites` 表**: 收藏题目映射记录。
- **`user_daily_usage` 表**: 每日活跃度统计，用于生成用户的热力图或学习热度打卡记录（如记录 2026-02-16 的答题次数）。

### 10.4 社区与资源管理 (Articles & Comments)

- **`articles` 表**: 包含学习资料、资讯文章。支持 HTML (`content`) 或 Markdown (`content_markdown`)，通过 `content_format` 区分渲染方式。拥有 `category`, `is_pinned`, `view_count` 等属性。
- **`article_comments` 表**: 针对文章的评论，具有审核状态（`status`: `pending`/`approved`/`rejected`）。
- **`user_push_tokens` 表**: 存储用户的极光推送 Registration ID (`user_id`, `push_token`)。
- **`notifications` 表**: 系统通知历史记录，关联 `link_id` 可跳转至文章。
- **`resource_categories` 表**: 资料站分类定义（如“考研资料”、“四六级”），包含 `icon_res` 图标。

### 10.5 运营与动态配置 (App & UI Settings)

- **`app_versions` / `app_patches`**: 管理 Android 端的 APK 整包更新与热修复（最新更新日志提及防闪退、QQ登录绑定）。表内包含版本描述 `update_log`, 强制更新标志 `is_force` 及远端下载链接 `download_url`。
- **`settings` 表**: K-V 结构全局配置。关键设置项包括：
  - `ai_day_limit_free`: 普通用户每日 AI 讲解次数限制（默认 `1`）
  - `ai_day_limit_plus`: Plus 订阅用户每日 AI 讲解次数限制（默认 `50`）
  - `registration_open`: 是否开放注册（`true`/`false`）
  - `app_announcement_enabled`: APP 首页公告开关
  - `app_announcement_text`: APP 首页公告文本
- **`activation_cards` 表**: Plus 订阅卡密管理。字段包括 `code`（如 `PLUS-XXXX-XXXX-XXXX`）、`duration_days`、`is_used`、`used_by`、`used_at`。卡密通过后台管理页面批量生成。
- **`countdowns` / `external_links` / `daily_contents`**: 驱动 App 首页推荐数据（倒计时组件、外部导航跳转、每日格言/古诗词等，含双语翻译字段）。

### 10.6 模拟考试系统表 (Exam System)

- **`exam_papers` 表**: 维护试卷元数据（标题、描述、限时、合格分）。
- **`exam_paper_questions` 表**: 试卷与题库的关联表，包含题目在试卷中的 `question_index` 和 `score`（分值）。
- **`exam_records` 表**: 存储用户的考试尝试。关键字段：`start_time`, `submit_time`, `score`, `status` (`ongoing`/`completed`/`abandoned`), `can_retry` (是否允许重考)。
- **`exam_record_answers` 表**: 存储每场考试的具体作答详情。
- **`major_access_control` 表** [核心权限]: 维护 `user_id` 与 `major_id` 的多对多映射，决定用户是否有权访问特定专业分类下的题库资源。

### 10.7 最新导入事项 (Recent Import Records)

- **2026-03-20 - 计算机机房题库导入**：
  - **背景**：从本地“机房题库”文件夹（40 个 JSON 文件）批量导入。
  - **内容**：新增 Major `计算机机房题库` (ID 1011)，下辖 40 个 Subject (`机房题库1` - `机房题库40`)。
  - **数据量**：共 **13,648** 道题目。
  - **现状**：此批次题目目前 `answer` 和 `analysis` 字段为空，仅保留题干与选项供基础展示/录入使用。
  - **工具**：使用 `scripts/import_computer_room.js` 进行本地导入并生成线上 SQL 脚本。

### 10.8 课程表系统 (Schedule System)

- **`schedules` 表**: 存储个人课程表数据。
  - 核心字段：`schedule_name` (学期名称), `subject` (课程名), `location` (地点), `day_of_week` (1-7), `period` (课次, 如 "1-2"), `sort_order`。
  - **CRUD 模块**：后端 `routes/admin_new/schedule.js`，Web 端 `ScheduleManagement.vue`。
  - **App 端数据结构**：`ApiService.getSchedule()` 返回 `List<ScheduleCell>`。

> **数据开发提示**：
>
> 1. **大数据量警告**：`questions` 表已有近 10 万条数据，执行关联查询时请务必评估性能，注意添加分页限制 (`LIMIT`) 并使用索引。
> 2. **JSON 数据结构**：`options` 和用户选择的 `answer` 在数据库中存为 JSON 字符串，但在向客户端（特别是 Android 端）投递时，可能需要处理反序列化。

---

## 十一、Plus 订阅系统 (Subscription System)

### 11.1 架构概述

烁学采用 **Plus 订阅** 模式进行功能分级（术语统一为 `Plus`/`订阅`，**禁止使用 VIP**）：

```
用户注册 → 免费用户 (Go 版)
        → 获取卡密 → 兑换激活 → Plus 用户 (有效期内)
```

### 11.2 订阅状态判断

**后端**：查询 `users.subscription_end_date`，与当前时间比较。

```javascript
const isSubscribed = subEndDate && new Date(subEndDate) > new Date();
```

**Android 端**：从 `DataStore` 读取 `subscription_end_date` Flow，在 Composable 中判断：

```kotlin
val subscriptionEndDate by userPreferences.subscriptionEndDate.collectAsState(initial = null)
val isPlus = remember(subscriptionEndDate) {
    try { sdf.parse(it)?.after(Date()) } catch (e: Exception) { false }
}
```

### 11.3 功能分级对照

| 功能           | 免费用户 (Go) | Plus 用户  |
| -------------- | ------------- | ---------- |
| 题库刷题       | 每科前 20 题  | 全量题库   |
| AI 智能讲解    | 每天 1 次     | 每天 50 次 |
| 视频无水印解析 | ✗             | ✓          |
| AI 学情分析    | 基础分析      | 深度分析   |

### 11.4 题目锁定机制 (Question Locking)

**客户端实现**（服务端返回全部题目，锁定逻辑在客户端）：

1. **答题卡** (`AnswerSheetDrawer`)：接收 `isPlus` 和 `freeLimit=20` 参数。
   - 前 20 题正常显示序号
   - 第 21 题起显示 🔒 锁图标（使用 `Icons.Filled.Lock`）
   - 锁定题目点击后触发 `onQuestionClick` 回调，由 `QuestionScreen` 判断弹出订阅引导

2. **做题页面** (`QuestionScreen`)：
   - "下一题" 按钮在到达第 20 题时显示锁标识，点击弹出订阅对话框
   - 答题卡中点击锁定题目弹出 `AlertDialog`，引导至 `SubscriptionScreen`
   - 对话框使用紫色主题 (`0xFFA855F7`)，包含 "查看 Plus 订阅" 和 "暂不需要" 按钮

3. **后端** (`data.js`)：`GET /api/data/questions` 不再截断返回，返回全部题目。

### 11.5 AI 次数限制 (AI Rate Limiting)

**后端** (`data.js` `/ai-explain`)：

1. 查询用户 `subscription_end_date` 判断订阅状态
2. 根据订阅状态读取不同设置：`ai_day_limit_free` 或 `ai_day_limit_plus`
3. 查询 `user_daily_usage` 表当日使用次数
4. 超限返回 403，消息区分免费/Plus 用户（免费用户提示"升级 Plus 可获得更多次数"）

**Android 端** (`QuestionViewModel.kt`)：

- `requestAiExplanation` 的 catch 块解析 `HttpException` 响应体中的 `error` 字段
- 将服务端实际错误消息（如次数超限提示）直接显示给用户

**后台管理** (`SettingsView.vue`)：

- 两个独立设置项：👤 普通用户（蓝色）和 ✨ Plus 用户（紫色），各有独立输入框和保存按钮

### 11.6 卡密兑换流程

```
后台生成卡密 (PLUS-XXXX-XXXX-XXXX, N天有效期)
  → 用户在 SubscriptionScreen 输入卡密
  → POST /api/data/subscribe {code}
  → 后端验证卡密有效性 (事务锁 FOR UPDATE)
  → 更新 users.subscription_end_date (新订阅或延长)
  → 标记 activation_cards.is_used = TRUE
  → 客户端乐观更新 DataStore

### 11.7 状态同步逻辑 (Status Synchronization)

1. **启动同步 (App-Start Sync)**：
   - `MainActivity` 启动时触发 `UserSyncViewModel.syncUserStatus()`。
   - 后台静默调用 `getUserProfile("me")` 获取最新到期时间。
   - 同步至 `DataStore`，确保 UI 上的 Plus 标识即时准确。

2. **手动刷新 (Manual Restore)**：
   - 订阅页面提供“恢复订阅”操作，用户点击后触发即时同步并弹出 Toast 反馈。

3. **按需刷新**：
   - 兑换卡密或修改个人资料后，客户端执行本地合并保存。

### 11.8 关键文件索引

| 功能            | 文件                                                  |
| --------------- | ----------------------------------------------------- |
| 订阅页面 UI     | `SubscriptionScreen.kt`                               |
| 题目锁定 UI     | `QuestionComponents.kt` (`AnswerSheetDrawer`)         |
| 做题页锁定逻辑  | `QuestionScreen.kt`                                   |
| 后端跨表查询    | `data.js` (`/articles` 等需要 JOIN `users.subscription_end_date`) |
| 后台设置 UI     | `SettingsView.vue` (AI 限制配置)                      |
| 数据库初始化    | `scripts/init_usage_table.js`                         |

### 11.9 Plus 头衔与头像视觉 (Plus Badge & Avatar)

**Android 客户端统一视觉展示**：
但凡在客户端展现用户头像（如账号设置、文章作者、评论列表、排行榜、个人主页），均需动态解析其 `subscription_end_date` 并附加 Plus 专属视觉标识。

1. **核心组件 (`PlusComponents.kt`)**:
   - `PlusAvatarWrapper`: 接收 `isPlus` 状态。若为 True，则在头像外层包裹一圈周期性旋转的渐变色光环边缘（SweepGradient 3000ms 匀速动画）。
   - `PlusBadge`: 一个自带流光微扫动效果（Shimmer）的高级紫色渐变徽章小标签，紧跟用户昵称右侧摆放。
2. **状态注入 (State Injection)**:
   - 从 `users` 表关联查询出的 `subscription_end_date`（在前端模型被定义为 `author_subscription_end_date` 或同名属性）。
   - 解析利用公共工具函数 `isUserPlus(dateStr)`，如果有效且在未来，即激活该用户的 Plus UI，不再局限于判断“本机登入用户”。
```

---

## 十二、安卓工具系统架构 (Tools System Evolution)

### 12.1 排序机制变迁

项目已彻底废弃“基于点击量 (Usage-based)”的自动排序，全面转向**用户自定义两层排序**：

1.  **数据流转**：
    - `Room` (`ToolDao`) 返回基础工具列表（默认按名称升序）。
    - `DataStore` (`UserPreferences`) 提供 `category_order` 和 `tools_order` 字符串。
    - `ToolsViewModel` 通过 `combine` 操作符将三者聚合，生成最终 UI 状态。
2.  **两层逻辑**：
    - **第一层 (Category)**：由 `category_order` 决定分类板块的整体上下顺序。
    - **第二层 (Tool)**：在每个分类内部，由 `tools_order` 决定工具的相对位置。工具**严禁**跨分类移动，以保持结构清晰。

### 12.2 UI 视觉演进

1.  **Monochromatic Premium 设计**：
    - 废弃了早期的彩色 icon 背景（Vibrant Colors），转而使用统一的 **Primary Tint 风格**。
    - 图标固定为主色，背景为 10% 透明度的主色。这种设计在保持功能识别度的同时，极大地提升了应用的高级感和一致性。
2.  **交互式排序 (Reorder Mode)**：
    - 通过右上角“排序”按钮进入独立模式。
    - 分类标题和工具卡片均提供显式的 ↑↓ 交互按钮，点击即刻触发 DataStore 更新并流式刷新 UI。

---

## 十三、排行榜 UI 进阶 (Leaderboard Assets)

排行榜前三名采用**差异化尊享视觉**：

- **前三名勋章**：动态渲染金/银/铜色渐变勋章（`SweepGradient` + 中间 Icon）。
- **奖杯底座**：对应的渐变颜色块，配以 `Rank` 数字悬浮背景。
- **头像光环**：前三名头像自动获得与排名对应的呼吸感外发光效果。

---

## 十四、内容渲染与分页架构 (Content Rendering & Pagination)

### 14.1 文章分页 (Article Pagination)

项目采用**双端配合的分页方案**：

1.  **服务端 (Node.js)**：
    - 路径：`GET /api/data/resources/articles`
    - 参数：`page`, `limit`, `category`
    - 逻辑：通过 SQL `LIMIT ?, ?` 实现，计算偏移量并返回结果集。
2.  **App 端 (Kotlin)**：
    - 驱动：`ResourcesViewModel` 维护 `currentPage` 和 `isEndReached` 状态。
    - 交互：`ResourcesScreen` 使用 `LazyListState` 监听滚动，当滚动至倒数第 1-2 项时自动触发 `fetchData`。
    - 刷新：支持下拉刷新重置分页状态。

### 14.2 增强型渲染引擎 (Enhanced Markdown Engine)

针对极其复杂的 Markdown (含 LaTeX、复杂代码块、多图混排)，详情页由传统的 TextView 渲染转向 **WebView 混合渲染**：

- **核心库**：`marked.js` (解析) + `highlight.js` (语法高亮)。
- **CSS 注入**：内置精心调优的 MD 主题，支持系统级暗色模式切换、圆角图片阴影及 1.8 倍行高舒适阅读。
- **通信桥梁**：通过 `JavascriptInterface` (名为 `Android`) 实现图片点击全屏预览与链接劫持跳转。
- **域名补全**：客户端 `Article` 模型自动对 `/uploads/` 路径进行 `https://studyapi.zhzgo.cn` 域名补全，确保跨域资源可用性。

### 14.3 首页动态数据看板 (Landing Page Dynamic Stats)

为保障官网数据透明且真实可信，平台首页（`LandingView.vue`）的“题库数量”和“注册用户”等关键数据墙已实现实时动态拉取：

- **后端设计**：新增无权限校验的 `GET /api/public/stats` 端点 (`routes/public.js`)，直接执行 `SELECT COUNT(*)` 高效统计核心表实时总量。
- **前端接入**：在首页组件的 `onMounted` 阶段进行预取，并借助 Vue 3 的局部响应式 API 对原本硬编码的数据集合做原地覆盖（携带 `toLocaleString()` 格式化）。

---

## 十五、消息推送系统 (Push Notification System)

### 15.1 极光推送 (JPush) 集成方案

由于 Maven 仓库连接不稳定，Android 端采取了 **本地 SDK 导入** 方案：

1. **物理结构**：从官网下载 SDK，将 `jpush-android-6.0.1.jar` 和 `jcore-android-4.2.0.jar` 等本地文件置于 `app/libs` 下。
2. **配置陷阱**：JAR 包导入不支持 AndroidManifest 自动合并。必须手动在主项目的 `AndroidManifest.xml` 中声明极光的 10+ 个核心组件（Activity/Service/Receiver），并手动添加 `JPUSH_APPKEY` 和 `JPUSH_CHANNEL` 的 `meta-data`。

### 15.2 注册与同步生命周期

1. **客户端初始化**：`MainActivity` 启动时调用 `JPushInterface.init`，并通过 **10 秒重试循环** 获取 `RegistrationID`。
2. **Token 同步**：获取到 RID 后，通过 `UserSyncViewModel.syncPushToken(rid)` 上传至后端 `/api/user/push-token` 接口，存入 `user_push_tokens` 表。
3. **运行时权限**：针对 Android 13+，`MainActivity` 必须在启动时主动请求 `POST_NOTIFICATIONS` 权限，否则通知会被系统静默拦截。

### 15.3 业务触发逻辑

后端通过 `notificationService.js` 统一封装推送接口，目前覆盖以下场景：

| 触发点               | 通知对象 | 附加数据 (`extras`)                        |
| -------------------- | -------- | ------------------------------------------ |
| **文章收到新评论**   | 文章作者 | `{ type: "article_comment", articleId }`   |
| **投稿审核通过发布** | 投稿者   | `{ type: "article_published", articleId }` |
| **投稿审核未通过**   | 投稿者   | `{ type: "article_rejected", articleId }`  |

### 15.4 客户端接收与跳转

- **Receiver**: `PushNotificationService.kt` 继承 `JPushMessageReceiver`。
- **逻辑**: 解析 `extras` 中的 `type` 和 `articleId`，构建 `Intent` 并在 `MainActivity` 中解析。
- **跳转**: 自动根据 ID 导航至 `ArticleDetailScreen` 页面。

---

## 十六、题目报错与工单系统 (Question Report System)

### 16.1 架构概述

为了收集用户对题库内容的纠错反馈，系统实现了**端到端**的题目报错机制，涵盖客户端提交、邮件通知与后台工单管理。

### 16.2 数据库设计

- **`question_reports` 表**: 存储报错记录。包含 `user_id`、`question_id`、`reason` (报错分类)、`detail` (补充说明) 和 `status` (状态：`pending`/`resolved`/`rejected`)。
- **`settings` 表扩展**: 新增 SMTP 邮件发送配置 (`smtp_host`, `smtp_user`, `smtp_pass`) 与管理员接收邮箱 (`admin_email_for_reports`)。

### 16.3 交互流转

1. **客户端 (Android)**：用户在 `QuestionScreen` 中点击题目旁的报错图标(`Warning`)，弹窗选择错误类型并填写补充信息。通过 `POST /api/data/questions/:id/report` 提交。
2. **后端 (Node.js)**：
   - 将报错写入 `question_reports`。
   - 读取 `settings` 的 SMTP 配置，使用 `nodemailer` 即时发送 HTML 格式的提醒邮件到管理员邮箱，内容包含报错原因、用户ID、题目ID和题干内容。
3. **管理后台 (Vue)**：
   - 增加独立的**题目报错处理**页面 (`QuestionReports.vue`)。
   - 管理员可查看全部报错记录（关联显示用户名和题目概览）。
   - 可一键将状态变更为 `resolved` 或 `rejected`。

### 16.4 AI 讲解接口修正 (Server-Sent Events)

- Web 端和 Android 端的 AI 讲解均已切换为原生的 SSE 数据流解析（Android 采用 `byteStream().bufferedReader()` 手动切分并解析 `data: ` 行），彻底修复了由于特定网络拦截器或日志输出导致的流数据阻塞和截断问题。

---

## 十七、邮件与通知生态 (Email & Notification Ecosystem)

为了增强用户互动的可靠性，系统建立了基于 SMTP 的邮件通知机制，并与极光推送 (JPush) 形成双重触达。

### 17.1 核心服务 (`emailService.js`)

- **SMTP 集成**：通过 `nodemailer` 实现。SMTP 配置（host, user, pass）存储在 `settings` 表中，由后端动态读取，避免硬编码。
- **模板化通知**：支持发送 HTML 格式的审核结果（通过/拒绝）、评论回复提醒、题目报错受理通知等。

### 17.2 强制邮箱绑定 (Mandatory Email Binding)

- **触发功能**：以下交互功能要求用户必须绑定有效邮箱：
  - **投稿 (Resources)**：保障审核结果能即时送达。
  - **评论 (Comments)**：保障违规告知或被回复提醒。
  - **纠错 (Reports)**：保障报错处理进度反馈。
- **校验逻辑**：后端 `data.js` 在相关路由入口执行 `user.email` 非空校验。

## 十八、用户身份与个性化 (User Identity & Profile)

### 18.1 多维度身份属性

- **昵称 (Nickname)**：用户可自定义昵称，若未设置则默认显示用户名。
- **个性签名 (Slogan)**：支持用户设置一段个人签名（Slogan）。
- **官方认证**：数据库中 `is_official` 标志位的用户将在 App 中获得“官方账户”专属徽章。

### 18.2 资料聚合接口

- `GET /api/data/user/:id` 接口已增强，一次性返回用户的 `nickname`, `slogan`, `avatar_icon`, `is_official`, `subscription_end_date` 等完整画像数据。

## 十九、设计系统与视觉标准 (Visual Standards)

### 19.1 Monochrome Premium 准则

- **色调控制**：严格遵循 **纯黑 (PureBlack)**、**纯白 (PureWhite)** 与 **靛蓝色 (Indigo)** 的组合。
- **色彩净化 (Anti-Pink)**：
  - 显式定义 Material 3 的 `surfaceContainer` 系列颜色，消除组件默认的粉紫色 fallback。
  - 所有的 `PullToRefresh` (下拉刷新) 指示器背景统一使用主题色 (`surfaceContainerHigh`)。
- **高级分类色彩**：
  - 题目类型与文章分类采用：绿色 (Green: `#4CAF50`)、蓝色 (Blue: `#2196F3`)、橙色 (Orange: `#FF9800`)、靛蓝 (Indigo: `#3F51B5`)、青色 (Teal: `#009688`)、灰色 (Grey: `#607D8B`)。
  - **严禁**使用粉色 (`#E91E63`) 或紫色 (`#9C27B0`)。

### 19.2 UI 组件标准化

- **指示器 (Indicators)**：下拉刷新组件必须手动同步 `PullToRefreshState`，确保容器与进度条颜色的一致性。
- **弹窗 (Dialogs)**：所有底部弹窗和对话框背景统一使用 `MaterialTheme.colorScheme.surface`。

---

## 二十、联考成绩查询与 AI 插件系统 (Score Query & AI Analysis)

### 20.1 系统概述

为中职考生开发的“职教高考联合体模拟考试”成绩查询系统，集成了 DeepSeek AI 深度学情诊断功能。

### 20.2 核心组件与数据流

- **后端 (Node.js)**：
  - `POST /api/public/score-query`：通过 `student_name` + `exam_number`/`school_name` 查询成绩。
  - `GET /api/public/schools`：获取所有参考学校列表供前端检索。
  - `POST /api/public/score-analysis`：调用 DeepSeek API 生成流式 AI 诊断报告。
- **前端 Web (Vue 3)**：
  - `ScoreQueryView.vue`：全屏“深海玻璃拟态”查询页。
  - `ScoreAnalysisCard.vue`：包含 ECharts 雷达图、百分比得分率算法及 AI 诊断流式输出。
- **Android 端**：
  - `ScoreQueryScreen.kt`：遵循 Monochrome 视觉规范的查询页面。
  - 首页“常用功能”集成：新增“联考成绩”高亮卡片（第一行）。
  - 修复了 JSON 异构解析问题，使用强类型 `ScoreDetails` 处理嵌套成绩明细。

### 20.3 AI 诊断算法与 Prompt 逻辑

- **满分自适应**：针对中职计算机专业（总分 350-500 不等，专业课 200/350 动态变化），算法统一采用 **“实际得分/满分”百分率** 划定诊断基准，而非硬编码分值。
- **DeepSeek 角色设定**：定义为“国家高级职业教育升学规划师”，输出包含：
  1. **整体画像**（优劣势概览）
  2. **各科诊断**（针对语文、数学、英语、专业的细节分析）
  3. **提分策略**（优先级排序）
  4. **升学预测**（对标历年公办本科/公办专科录取线）

### 20.4 数据库同步 (Import Flow)

1. **SQL 结构**：`exam_scores` 表，使用 `json` 类型存储 `score_details`。
2. **导入脚本**：`scripts/import_scores.js`。解析 Excel 时会根据 8 位一个周期的规律自动提取各学科的总分、客客观分及排名。
3. **性能建议**：查询时强制 `LIMIT 5` 并使用 `student_name` 索引，防止重名导致的长列表。

---

## 二十一、模拟考试系统 (Simulation Exam System)

### 21.1 核心流程

1. **环境准备**：`ExamPrepScreen.kt` 展示试卷说明、限时、合格标准。
2. **沉浸式作答**：`ExamTakingScreen.kt` 进入全屏答题模式，支持单选/多选/判断。
3. **结果审阅**：`ExamResultScreen.kt` 结束后展示得分、用时、对错分布及原题解析。

### 21.2 绝命监控 — “防作弊”中止机制

为确保考试严肃性，Android 客户端集成了一套严厉的状态监控：

- **触发条件**：Activity 生命周期 `ON_PAUSE` (切后台、息屏、接电话)。
- **惩罚逻辑**：一旦触发，立刻调用 `/api/data/exams/abandon` 将当前考试状态设为 `abandoned`，成绩判定为 **0 分**。
- **恢复策略**：零分记录需管理员在 Web 后台手动开启 `can_retry` 权限后方可重考。

---

## 二十二、题库资源访问控制 (Resource Access Matrix)

### 22.1 权限粒度

权限控制在 **专业 (Major)** 层级。如果用户未获得某 Major 的授权，其在 App 的“刷题”模块中将无法看到该分类下的任何科目。

### 22.2 管理端实现 (Access Control Matrix)

- **技术栈**：Vue 3 + Tailwind CSS + 玻璃拟态 UI。
- **交互**：采用“用户-专业”二维矩阵网格。管理员可快速勾选变更，支持**批量 Diff 缓存更新**（一次提交仅处理真正发生变更的用户权限，优化后端性能）。
- **逻辑**：后端通过 `major_access_control` 表实时拦截无权限的 API 请求。

---

## 二十三、试卷数据迁移与标准化 (Exam Data Migration & Standardization)

### 23.1 自动化迁移流程

为了快速将第三方或外部分散的考试 JSON 数据导入烁学平台，系统建立了一套基于 Node.js 的自动化 SQL 生成流程（如 `generate_sql.js`）。

1.  **JSON 预处理**：针对原始 JSON 中可能存在的非法转义字符、未转义的双引号进行清洗，确保 `JSON.parse` 成功。
2.  **内容净化 (Tag Removal)**：自动识别并剔除题目内容开头的冗余标签（如 `【.*?】` 系列），保持题干简洁规范。
3.  **动态算分机制**：
    - **合格分 (pass_score)**：系统不再使用固定分值，而是根据试卷总分（`total_score`）动态计算，统一设定为总分的 **60%**（使用 `Math.ceil` 向上取整）。
4.  **SQL 安全注入**：
    - **转义处理**：对内容中的单引号 (`'`) 和反斜杠 (`\`) 进行双重转义，防止破坏 SQL 语法。
    - **关系链接**：使用 `LAST_INSERT_ID()` 动态获取 `exam_papers` 插入后的 ID，并作为外键维护 `exam_paper_questions` 的关联关系。

### 23.2 近期导入纪录 (Recent Import Series)

- **2026-04-16 - 对口拔高与压轴系列**：
  - **批次内容**：包含《对口拔高（八）》至《对口拔高（十）》以及《压轴（一）》、《压轴（二）》共 5 套核心试卷。
  - **技术特性**：全量试卷均采用 60% 动态合格分逻辑及题干标签自动清洗逻辑。
  - **存储位置**：`c:\Users\沧烁\Desktop\模拟题\`。
