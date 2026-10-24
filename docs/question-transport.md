# 2.0.0 题库响应保护与发布记录

日期：2026-10-23（Asia/Shanghai）。线上 App 为 **2.0.0 / versionCode 200**，Web 与 API 配套发布。

此前数据库已加密，HTTPS 也已启用，但 API 解密后返回普通 JSON，因此浏览器 Network 能看到题干、选项、答案和解析。本次增加应用层响应加密，并减少提前交付的答案；不把隐藏 Network 内容等同于阻止授权客户端提取。

## 实现与边界

- Web 和 Android 为每个受保护请求生成临时 P-256 密钥对，只上传公钥与随机请求标识。服务端生成独立临时密钥对，经 ECDH、HKDF-SHA256 派生 AES-256-GCM 密钥。请求方法、原始路径与查询、随机标识和 Authorization 摘要绑定派生上下文与认证数据。
- 响应只包含协议版本、公钥、salt、IV、请求标识及带认证标签的密文。浏览器私钥不可导出；两端临时私钥不持久化。不使用固定前端密钥，也不把数据库密钥发给 Web 或 APK。
- 缺少或错误的加密参数返回 `426 QUESTION_TRANSPORT_REQUIRED`；不能通过移除请求头退回明文。Web 成功响应也拒绝明文降级。账号验证、会员/专业权限、限流仍由服务端执行，密码学参数不是账号授权。
- 覆盖题库列表、搜索、单题、错题、收藏及练习、推荐、进度、历史解析、考试开始/提交/记录，以及管理员题库、试卷和题目报错响应。错误响应只含提示，不附带受保护正文。缓存继续为 `private, no-store`。
- 普通练习列表不预发答案/解析。材料小题也去除嵌套答案和解析。每题附带 HMAC 阅读凭证，绑定账户、题号及一小时有效期。
- 提交作答必须通过阅读凭证和当前权限校验，由服务端判分，只返回本题参考答案与解析。Android 错题复习使用 `review_only`，不覆盖原来的服务端学习记录。
- 重新进入已经作答的题目，通过 `/api/data/questions/:id/review` 按当前题加载解析，需要本人的非删除作答记录和有效阅读凭证；不从列表批量预取历史标准答案。批量进度导入仍保留，返回处理数量，不返回标准答案。
- 非管理员正文预算收紧为每账户每分钟 100 条、每小时 300 条、每天 1500 条，每 IP 每小时 3000 条；列表、重复阅读、判分返回和历史解析均计数。判分另有每账户每分钟 30 次限制。超限返回 `429` 和 `Retry-After`。真实管理员保留管理权限；管理员授权的 CSV 导出使用独立预算。
- 管理员为账户生成密码的 CSV 导出是明确授权的明文文件交付，仍保留登录、独立密码、会员/专业范围、预算与审计。机器人仍为持有私有服务密钥的服务间 HTTPS 接口，继续校验关联账号权限和阅读预算，不向客户端公开此密钥。

临时密钥与请求绑定可以防止直接复制响应后用固定密钥解开，也能拒绝篡改或将响应交换给另一请求；它不是完整的请求防重放机制。相同登录身份的重复请求仍会经过服务端授权和额度计数。

拥有正常访问权限并控制客户端的人，仍可修改解密/渲染代码、拦截已解密内容、使用 OCR 或人工整理。非导出私钥也不能阻止恶意脚本在解密后复制正文。本次保护提高直接响应采集和批量取题成本，不能保证“任何人或 AI 都拿不到明文”；不要将此方案描述为不可逆向或客户端 DRM。

## 主要代码

| 位置 | 作用 |
| --- | --- |
| `apps/server/services/questionTransport.js` | 强制响应加密，协议路径与认证上下文 |
| `apps/server/middleware/authMiddleware.js` | 身份确认后、业务处理前验证安全通道 |
| `apps/server/services/questionDelivery.js` | 阅读凭证与未作答题目的答案移除 |
| `apps/server/routes/questions.js` | 列表、判分、按题恢复解析 |
| `apps/web/src/utils/question-transport.js` | WebCrypto 临时密钥与解密 |
| `apps/web/src/api/index.js` | Axios 统一接入，Token 刷新重试生成新请求密钥 |
| `apps/android/.../network/QuestionTransportInterceptor.kt` | OkHttp 解密，位于身份拦截器之后、Gson 转换之前 |
| `apps/android/app/build.gradle.kts` | `defaultConfig.versionName = "2.0.0"`、`versionCode = 200` |

## 发布与数据核对

正式包：`artifacts/android/app-release-2.0.0-20261023.apk`。

下载：[2.0.0 APK](https://study.zhzgo.cn/downloads/app-release-2.0.0-20261023.apk)。SHA-256：`acdbd69563a899f645678c0a96e6290e9bb5729d611fcfb09137d8ca177ffb13`。

使用用户原有签名，证书 SHA-256：`5b96d3e20cac51c21df5ad09a054b04e80ba4e2bb90f2c94404288786e637fdc`，与此前正式包一致。签名密钥与密码未写入源码、文档或发布脚本。升级接口已登记 200，强制升级；旧 APK 不支持加密协议，必须升级后刷题。

发布工具：`ops/scripts/deploy-question-protection.py`。先验证数据库/应用/Web 备份，再在只监听 loopback 的隔离 Node 服务验证；随后安装 API、Web、签名 APK，验证两个域名与常规接口，最后登记升级版本。隔离检查只写入并清理临时测试账户，不修改现有账户的密码、权限或学习数据。

备份：`/www/backup/codex/study-protection-20261023T092232Z`，含完整数据库、应用配置与上传文件、Web、覆盖前 API 和校验结果。数据库包 SHA-256 为 `f066365e4c8c7a8aef88ec47f9cbe2ab4ab3c18984e085ea38b18d03031f3590`。未重做题库迁移、未轮换数据库加密密钥。

本次发现网页域名 `/api` 路径落入 SPA 页面并返回 HTML；原 Web 生产包直接使用 API 域名，因此此前未暴露这一转发缺失。已增加 `/www/server/panel/vhost/nginx/extension/study.zhzgo.cn/study-api.conf`，转发至 `127.0.0.1:9898`，校验并重载 Nginx。第一次正式检查遇到该问题后自动回滚代码，再修复并重新验证发布。

| 数据 | 发布前后 |
| --- | --- |
| 用户 | 53 → 53 |
| 学习记录 | 46,638 → 46,638 |
| 收藏 | 680 → 680 |
| 考试记录 | 73 → 73 |
| 普通题 | 133,444，全部存储密文，明文计数 0；内容指纹 3908969749 一致 |
| 试卷题 | 834，全部存储密文，明文计数 0；内容指纹 3171105095 一致 |

## 验证范围

- 后端 57 项测试通过，包括凭证过期/篡改/串账户/串题拒绝、答案隐藏、材料题服务端判分、现有授权/加密/导出/统计回归。
- Web 7 项测试通过，包括真实 Node 加密响应与 WebCrypto 互通、非导出私钥、篡改/错误私钥/错误账号令牌/错误请求上下文拒绝、明文降级拒绝与入口覆盖。生产构建通过。
- Android 正式构建、调试 Kotlin 与测试源码编译通过，原有 JUnit 19 项通过；正式 APK 签名验证和版本元数据核对通过。
- 隔离服务 22 项检查；正式 API 域名 22 项、网页域名 22 项；导出 16 项；原有 API/网页回归 62 项均通过。升级接口返回 2.0.0 / 200 / 强制升级，下载 200，远端 APK 与本地 SHA-256 一致。
- 浏览器以隔离虚构数据验证真实加密响应正常显示、切题与答题卡分页。该预览默认禁止写入，不代表对生产账户进行了作答操作。
- 没有已连接 Android 设备，未完成覆盖安装、真机解密、TalkBack 与不同 ROM 验收。不能把构建及签名通过等同于真机验收通过。

本地脱敏报告和日志位于 `ops/reports/2026-10-23/`。实现依据：[MDN WebCrypto 密钥派生](https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/deriveKey)、[OWASP 业务流程防滥用](https://api-security.owasp.org/editions/2023/en/0xa6-unrestricted-access-to-sensitive-business-flows/)。
