# 烁学 Vibe Coding 指南

日期：2026-10-22  
适用仓库：`D:\codeStudy\code\study.zhzgo.cn_83FYC`  
用途：给后续 AI 编码会话提供稳定的项目上下文、边界和验收标准。

## 一句话原则

先用真实代码、真实数据和真实验证建立上下文，再做小步实现。界面可以主动重构；题库密文、会员权限、用户学习数据和历史成绩不能拿来试错。

## 当前目录

```text
apps/
  android/       Kotlin + Jetpack Compose 原生 App
  server/        Node.js + Express API
  web/           Vue 3 + Vite Web 和管理后台
  bot/           QQ 机器人
data/
  legacy-question-bank/  历史题库 JSON，仅作素材，不是运行时数据源
docs/           架构、安全、部署、题库格式、Vibe Coding 文档
ops/
  scripts/      运维、审计、迁移、发布和回归脚本
  baseline/     生产改造前代码基线
  releases/     当前发布包
  reports/      构建、测试和验收记录
artifacts/
  android/      调试 APK、签名发布 APK、发布元数据
archive/
  legacy-2026-10-22/  旧脚本、旧调试文件和迁移残留，不参与运行
```

构建日志、Gradle 项目缓存、旧发布包和未使用的临时文件已移出项目或归档。根目录只保留 `apps/ data/ docs/ ops/ artifacts/ archive/`。

## 新文件放哪里

| 内容 | 位置 |
| --- | --- |
| Android 页面、组件、ViewModel | `apps/android/app/src/main/java/cn/zhzgo/study/` |
| Android 单元测试 | `apps/android/app/src/test/java/cn/zhzgo/study/` |
| Android UI 预览稿 | `apps/android/design/` |
| API 路由、服务、权限 | `apps/server/routes/`、`services/`、`middleware/` |
| 服务端测试 | `apps/server/tests/` |
| 题库加密迁移工具 | `apps/server/scripts/`（只保留受控工具） |
| Web 页面 | `apps/web/src/views/` |
| Web 公共组件和工具 | `apps/web/src/components/`、`src/utils/` |
| 项目文档 | `docs/` |
| 运维、审计和发布脚本 | `ops/scripts/` |
| 发布包 | `ops/releases/<日期>/` |
| 构建和测试报告 | `ops/reports/<日期>/` |
| 生产改造前代码 | `ops/baseline/<日期>/` |
| APK 和可交付二进制 | `artifacts/android/` |
| 历史题库素材 | `data/legacy-question-bank/` |
| 旧脚本、迁移残留 | `archive/legacy-<日期>/`，仅追溯 |
| 临时构建、缓存、下载 | `D:\CodexData\temp\<任务名>`，不要写回源码 |
| 独立报告、导出文件 | `D:\CodexData\outputs\<任务名>` |
| 数据库和密钥备份 | `D:\CodexData\backups\<任务名>` |

构建产物和日志不要放在项目根目录；生成后要么放入 `artifacts/` 或 `ops/reports/`，要么写入 D 盘临时目录。不要把 `.env`、密钥、上传用户文件、数据库转储放进 `docs/`、`apps/` 或 Git。

## 当前状态

| 项目 | 状态 |
| --- | --- |
| Android | Kotlin + Compose，`minSdk 24`、`targetSdk 36`，底部导航为首页 / 题库 / 资源 / 统计 / 我的 |
| Web | `https://study.zhzgo.cn/` |
| API | `https://studyapi.zhzgo.cn/`，内部监听 `127.0.0.1:9898` |
| 数据库 | MySQL `study_center`，普通题和试卷题已 AES-256-GCM 加密 |
| 题目规模 | 普通题 133,444 道，试卷题 834 道 |
| 搜索 | `question_search_tokens` 盲索引，约 16,995,888 token |
| 自动化 | 后端 44、Web 3、Android JUnit 16、正式接口 40 项及导出/页面检查通过 |
| 题库导出 | 管理员按账户生成 30 天随机密码；登录 + 密码 + 服务端会员/专业范围三重校验，CSV 带审计 |
| APK | 调试包 `artifacts/android/app-debug-1.1.2-20261022.apk`；签名快照 `app-release-1.1.2-20261022-2028.apk` |

当前没有连接 Android 设备，未完成真机、TalkBack、不同 ROM 和签名正式发布验收。不要把调试包描述成正式线上版本。

## 开工前读取顺序

1. 本文件。
2. [architecture.md](architecture.md)：技术栈和目录。
3. [security.md](security.md)：权限、加密和已修复漏洞。
4. [deployment-2026-10-22.md](deployment-2026-10-22.md)：生产切换和事故记录。
5. [question-format-migration.md](question-format-migration.md)：题库格式与 284 道内容问题。
6. 最后才读本次改动对应的页面、ViewModel、路由或服务。

不要读取、打印或提交 `apps/server/.env`、`apps/bot/.env`、密钥、生产备份、用户邮箱和 QQ OpenID。需要 SSH 时使用 `ops/scripts/remote_session.py` 的交互式输入，不把密码写入命令或文件。

## 不可破坏的规则

1. 题库只通过 `apps/server/services/questionStorage.js`、`utils/questionCrypto.js` 和 `utils/questionFormat.js` 读写。
2. 查询 `questions` / `exam_paper_questions` 时必须保留主键和要解密的列；解密依赖 mysql2 原始表/列元数据和题目 ID。
3. 会员、管理员和专业访问权由服务端数据库判断；不能相信客户端字段、URL 参数或 JWT 里的 `role`。
4. 免费账号每科只能读取前 20 道有效题；Plus 最多每页 50 道。先定预览范围，再做题型/搜索筛选。
5. 正确性由服务端 `scoreAnswer()` 计算，客户端提交的 `is_correct` 不能直接落库。
6. 缺失题干、选项、答案或章节时保留原记录并标注，不猜补、不伪造。
7. 修改数据前先备份；迁移在独立副本完成、全量解密和指纹核验通过后才允许写正式库。
8. 不把密钥写进代码、Web bundle、APK、日志、截图或对话。
9. 页面返回后不能重置答题进度、草稿、筛选或分页。
10. 不以“能打开”代替“已验证”；至少执行与改动对应的测试、构建或接口回归。

## 题库契约

| 类型 | `type` | 选项 | 答案 |
| --- | --- | --- | --- |
| 单选 | `single` | `{ "A": "...", "B": "..." }` | `A` |
| 多选 | `multiple` | 同上 | `A,C`，去重排序 |
| 判断 | `judge` | `{ "A": "正确", "B": "错误" }` | `A` / `B` |
| 填空 | `fill` | 通常为空 | 原始文字 |
| 排序 | `sort` | 有序数组 | 有序文字数组的 JSON 字符串 |
| 材料 | `material` | 子问题数组 | 按子问题规则 |

服务端新写入必须经过 `normalizeQuestionRecord()`。它能统一选项容器、答案包装和判断题词表，但对重复标号、答案指向不存在选项、空题干等拒绝静默猜测。旧脚本和前端的多格式兼容只用于历史兼容，不能再成为服务端写入来源。

加密格式：AES-256-GCM，前缀 `zhzgo:gcm:v1:`，每条记录独立 IV，AAD 绑定表名和记录 ID。`content` 保存密文，`options` 为 `[]`，`answer`、`analysis` 为空串。`QUESTION_ENCRYPTION_KEY` 丢失后密文不可恢复。

搜索使用派生 HMAC 盲索引，查询先取候选，再解密 `content` 做连续子串确认。不要删除后半段确认。

## 权限边界

请求链路：`verifyToken` → 每次从数据库读账号/角色/会员到期时间 → `questionFilter()` 拼接专业和免费预览范围 → 所有题目入口共用 → 管理端再用数据库角色校验。

不要重新引入：客户端会员遮罩、只限前 20 题的 UI 锁、JWT 角色直通管理端、考试开始提前下发答案、上传只看扩展名、视频解析允许任意 URL 或内网地址。

## UI/UX 方向

截图是已确认的视觉基准，用于理解布局、信息密度和交互位置，不是旧页面的复刻目标。品牌仍为“烁学”，整体遵循现代、克制、清晰的商业 App 标准。

| 用途 | 建议值 |
| --- | --- |
| 背景 / 内容 | `#F8FAFC` / `#FFFFFF` |
| 主 / 次 / 辅助文字 | `#111827` / `#64748B` / `#94A3B8` |
| 品牌蓝 / 浅蓝底 | `#2563EB` / `#EFF6FF` |
| 分割线 | `#E5E7EB` |
| 成功 / 错误 / 警告 | `#16A34A` / `#DC2626` / `#D97706` |

页面水平边距约 20dp，行高 52–60dp，普通按钮 40–44dp，主要操作 46–48dp，触控区至少 44dp。品牌蓝只用于选中、进度、重要操作和关键反馈，不要把所有按钮做成蓝色。

信息架构：首页负责任务与继续学习，题库负责查找，刷题负责专注作答，错题先管理再浏览，收藏独立于错题，资源负责文章与工具，统计负责趋势，我的负责账号、会员和设置。

禁止把所有内容套白卡片、使用大面积渐变、堆彩色图标、用巨大宣传横幅、为了高级感增加无意义留白，或生成 Compose 难以落地的布局。

## 页面衔接

- 主栏目切换 140ms 淡入淡出，详情页进入/返回 220ms 短距离方向滑动。
- 无参数页面加载统一使用 `StudyLoading`，延迟 180ms 出现，缓存命中不闪屏。
- 首页部分数据失败保留上次成功数据。
- 从会员页返回刷题保留当前题、草稿和计时，并补齐当前 50 题批次。
- 收藏、资源、错题返回只刷新状态，不重置已加载列表和筛选。
- Token 刷新只有在明确 400/401/403 时才清会话；网络超时和 5xx 保留会话。
- 切题回顶部；答案保存中阻止离开；保存失败保留草稿和重试入口。
- 检查 320–360dp 窄屏、长题干、公式、图片、深色模式和动态字体。

## 测试命令

后端：

```powershell
Set-Location apps/server
node --test tests/*.test.js
```

Web：

```powershell
Set-Location apps/web
node --test tests/*.test.mjs
node node_modules/vite/bin/vite.js build
```

Android：

```powershell
Set-Location apps/android
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:assembleDebug --offline
```

本机 Gradle wrapper 曾尝试联网下载；如被拦截，使用已缓存的 Gradle 9.2.1 和显式 `JAVA_HOME` / `GRADLE_USER_HOME`。临时文件放 `D:\CodexData\temp`，不要把缓存和日志写回源码目录。

接口回归在 `ops/scripts/smoke-api.cjs`。测试匿名、免费、Plus、管理员和伪造 JWT；写操作只用测试记录并清理。正式回归结果保存在 `ops/reports/2026-10-22/` 和服务器私有备份。

## 部署

生产：`47.109.86.76`，`root` 用户，凭据通过 `ops/scripts/remote_session.py` stdin 输入。

发布前确认：数据库/应用/上传/密钥有备份；隔离副本通过；Web 构建和 Android 编译通过；发布包含 `config/db.js`；Nginx 有 `X-Forwarded-Proto`；API 只监听 `127.0.0.1:9898`；防火墙不开放 9898。

顺序：停接口 → 事务写入和指纹核验 → 覆盖代码/Web → 安装私有 `.env` → `setsid` 启动 → Nginx 检查 → 正式 HTTPS 回归。

加密后回滚不能只回退代码；必须用切换前数据库备份、对应代码和正确密钥。不要在生产库手工解密或重新写明文。

## 题库导出文件

- apps/server/routes/exports.js：网页端按科目导出。
- apps/server/services/questionExport.js：随机密码、bcrypt、CSV 转义。
- apps/server/routes/admin_new/export_credentials.js：管理员生成、查看和撤销。
- apps/web/src/views/ExportView.vue、apps/web/src/views/admin/ExportAuthorization.vue：导出与授权界面。
- apps/server/tests/question-export-routes.test.js：路由级授权、过期、审计和演示角色测试。

## 当前待办

- 处理 284 道原有内容质量问题；不要批量猜改。
- 轮换数据库旧密码和曾暴露的 AI、SMTP、极验凭据；MySQL 3306 未对公网放行。
- 在真实 Android 设备验证加载、切换、TalkBack、网络中断和不同 ROM；重新生成签名正式包。
- 上传图片仍是公开静态文件；若要图片防抓取，需要单独设计鉴权代理。
- 加密无法阻止已授权用户截图、录屏或人工保存可见内容。
- QQ Bot 后端接口可用不代表 webhook/机器人进程已在外部平台全链路启用。

## 提示词模板

### 新会话

```text
请先阅读 D:\codeStudy\code\study.zhzgo.cn_83FYC\docs\vibe-coding.md、
architecture.md、security.md、deployment-2026-10-22.md。
本次只处理指定范围；先确认真实路由、模型和测试，再给简短计划。
不要读取或输出 .env、密钥、用户隐私，不要把客户端字段当作会员或管理员依据。
```

### UI 改动

```text
保持题库、会员和答题业务逻辑不变，按现有设计令牌重做这个页面。
检查加载、空、错误、深色、长题干和窄屏；保留返回后的筛选、分页和草稿。
不要机械复刻截图，也不要给所有内容套白卡片；完成后编译 Android 并说明未做真机验证。
```

### 安全审查

```text
从服务端权限边界审查这个功能：匿名、免费、Plus、管理员、伪造 JWT、改 question_id、
换入口、搜索、收藏/错题练习、批量请求和上传。不要只看客户端遮罩。
先只读检查，再写测试；没有备份和隔离副本时不要修改正式数据。
```

### 数据迁移

```text
先只读盘点题型、选项容器、答案包装、空字段和异常记录，再设计独立副本迁移。
逐条验证幂等、判分含义和富文本不修改；无法判断的记录列出，不要猜测补全。
备份、隔离副本、全量指纹核验和事务写回全部通过后才允许切换。
```

## 完成标准

- 用户行为已实现，加载、空、错误和权限拒绝都有处理；
- 原业务数据与接口兼容性未破坏；
- 服务端权限仍生效，改参数不能扩大范围；
- 相关测试、构建或正式接口回归通过；
- 没有新增明文密钥、敏感日志或临时明文题库副本；
- 部署有备份和回滚路径；
- 未验证部分明确写出，不用“应该可以”代替。

## 关键文件

- `apps/android/app/src/main/java/cn/zhzgo/study/ui/AppNavigation.kt`：导航和转场。
- `apps/android/app/src/main/java/cn/zhzgo/study/ui/components/StudyLoading.kt`：统一加载。
- `apps/android/app/src/main/java/cn/zhzgo/study/ui/screens/QuestionViewModel.kt`：刷题分页、草稿、会员返回补齐。
- `apps/server/services/questionAccess.js`：会员和专业过滤。
- `apps/server/services/questionStorage.js`：加密写入和搜索索引。
- `apps/server/utils/questionCrypto.js`：AES-GCM 和盲索引。
- `apps/server/utils/questionFormat.js`：题型统一。
- `apps/server/routes/questions.js`：题库读取入口。
- `apps/server/config/db.js`：解密适配器和直接写题拦截。
