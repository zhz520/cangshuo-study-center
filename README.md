# 烁学 Zhzgo Study

烁学是一套面向职教高考和计算机考试的学习系统，包含 Android App、Web 前端、Node API 和 QQ Bot。

当前题库已完成格式统一和 AES-256-GCM 存储加密，会员与题库访问权限由服务端控制。最近一次生产发布和验证记录见 [docs/deployment-2026-10-22.md](docs/deployment-2026-10-22.md)。
2026-10-23 页面统一、答题卡分页、排行榜、今日学习统计与 Plus 个性化记录见 [docs/ui-completion-2026-10-23.md](docs/ui-completion-2026-10-23.md)。
2026-10-24 公告栏、Plus 头像与会员铭牌、网页题库目录、答题工具菜单及后台模块统一记录见 [docs/ui-coherence-2026-10-24.md](docs/ui-coherence-2026-10-24.md)。

GitHub 仓库：<https://github.com/zhz520/cangshuo-study-center>。公开仓库只包含源码、文档和构建所需的小体积 SDK 文件；原始题库 JSON、APK、用户上传内容、真实 `.env`、Firebase 配置和推送私钥保留在本机或私有备份，不进入公开仓库。

## 目录

```text
apps/
  android/   Android 原生客户端
  server/    Node.js API 与后台服务
  web/       Vue Web 与管理员界面
  bot/       QQ 机器人
data/
  legacy-question-bank/  历史题库 JSON 素材
docs/       项目文档和 AI 协作规范
ops/        运维脚本、基线、发布包、验收报告
artifacts/  APK 等交付产物
archive/    旧脚本和迁移残留，不参与运行
```

## 入口

| 内容 | 地址 / 位置 |
| --- | --- |
| Web | <https://study.zhzgo.cn/> |
| API | <https://studyapi.zhzgo.cn/> |
| Android 发布包 | 本机 `artifacts/android/app-release-2.0.1-20261024.apk`（versionCode 201，原证书签名；公开仓库不包含 APK） |
| 项目架构 | [docs/architecture.md](docs/architecture.md) |
| 安全与会员边界 | [docs/security.md](docs/security.md) |
| AI 协作规范 | [docs/vibe-coding.md](docs/vibe-coding.md) |

## 常用命令

首次配置 Android 构建时，把 `apps/android/app/google-services.example.json` 复制为 `google-services.json` 并填入自己的 Firebase 配置。真实的 Firebase 配置、服务端 `.env`、JPush 私钥和机器人 `.env` 不会进入仓库。

```powershell
Set-Location apps/server
node --test tests/*.test.js

Set-Location ..\web
node --test tests/*.test.mjs
node node_modules/vite/bin/vite.js build

Set-Location ..\android
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:assembleDebug --offline
```

生产发布前先阅读 [docs/deployment-2026-10-22.md](docs/deployment-2026-10-22.md) 和 [docs/vibe-coding.md](docs/vibe-coding.md)。任何题库写入、加密迁移和生产部署都不能绕过备份、隔离副本和接口回归。
