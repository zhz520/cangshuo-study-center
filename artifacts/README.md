# 交付产物

公开 GitHub 仓库只保留本说明文件，不提交 APK 或构建元数据；下列文件保存在本机 `artifacts/android/`，用于本次验收和后续签名发布。

## Android

| 文件 | 说明 |
| --- | --- |
| `app-debug-1.1.2-20261022.apk` | 最终调试验证包，包含统一加载、页面衔接和会员返回修复 |
| `app-release-1.1.2-20261022-2028.apk` | 2026-10-22 20:28 的签名快照，早于最后一次调试包修正 |
| `release-output-metadata.json` | release 构建元数据 |

两个 APK 的包名均为 `cn.zhzgo.study`，版本 1.1.2（112）。调试包不等于签名正式发布包；正式分发前应重新生成、签名并在真实设备验收。

更早的 2026-04-15 发布包已移入 `D:\CodexData\backups\study-center-20261022\android-legacy`。
