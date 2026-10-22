# 第三方服务配置（极验与 AI 模型）

管理后台「系统与内容 → 第三方服务」（/admin/integrations）可以修改极验人机验证和 AI 接口，不需要改服务器 .env 或重启服务。

## 极验 Geetest

| 字段 | 说明 |
| --- | --- |
| 启用 | 关闭后登录、注册、找回密码不再要求极验 |
| Web Captcha ID | 极验控制台的 Web 验证 ID，Web 登录页动态读取 |
| Web Key | 服务端校验用，只保存到 settings，不返回明文 |
| Android Captcha ID | Android 端验证 ID，供服务端校验 |
| Android Key | Android 服务端校验 Key |

读取顺序是数据库配置优先、环境变量兜底。Web 端通过 /api/public/auth-config 取得 Captcha ID，接口只返回 ID 和启用状态，不返回 Key。关闭极验后，服务端跳过校验；启用但缺少 Key 时失败关闭，不会放行登录。

Android 安装包内的 Captcha ID 是编译期常量。后台修改 Android Captcha ID 后，服务端会立即使用新 ID 校验，但旧 APK 仍使用内置 ID，需要发布新 APK 才能完全切换。Android Key 可以立即轮换。

## AI 模型

接口按 OpenAI Chat Completions 协议调用，所以 DeepSeek、OpenAI、Moonshot、通义、硅基流动、自建兼容网关都可以使用。

| 字段 | 默认值 | 用途 |
| --- | --- | --- |
| 服务类型 | DeepSeek | 选择后自动填入默认地址 |
| 接口地址 | https://api.deepseek.com/v1 | 必须是公网 HTTPS |
| API Key | 环境变量 DEEPSEEK_API_KEY | 只保存到 settings，不返回明文 |
| 题目讲解模型 | deepseek-chat | 普通 AI 讲解 |
| 深度讲解模型 | deepseek-reasoner | 流式深度讲解，过滤思维链 |
| 学情分析模型 | deepseek-chat | 学情报告、成绩诊断、首页分析 |
| 超时 | 30000ms | 5–120 秒 |

管理页的「测试连接」会使用题目讲解模型发送一条最小请求，返回模型名和延迟。密钥留空表示保持原值；填写新值才会覆盖。

## 安全边界

- 管理接口要求数据库中的 admin 角色，演示账号被拒绝。
- GET 只返回掩码（例如 sk-****abc），不返回完整 Key。
- 接口地址必须是 HTTPS 公网地址，拒绝 localhost 和常见内网网段，避免把服务端当作内网代理。
- 配置缓存 30 秒，保存后立即失效；登录校验读取数据库，不需要重启 Node。
- 现有题库、用户进度、收藏和考试成绩不受影响。

## 验证

- 后端测试覆盖公开配置不含密钥、后台掩码、密钥留空不覆盖、关闭极验、拒绝内网地址和超时范围。
- 正式接口回归检查 /api/public/auth-config 不泄露 Key，演示账号不能读取 /api/admin/integrations。
