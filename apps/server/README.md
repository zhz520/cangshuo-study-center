# 烁学 (Zhzgo Study) - 后端 API

烁学全栈学习系统的后端核心，基于 Node.js 和 Express 构建。

## 🚀 技术栈

- **框架**: Express ^4.18.2
- **数据库**: MySQL (mysql2/promise)
- **认证**: JWT (Access Token + Refresh Token)
- **多媒体处理**: 视频/图集解析逻辑
- **机器人支持**: QQ Bot 接口集成

## 🛠️ 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 环境配置

创建 `.env` 文件并配置以下变量：

- `DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`
- `ACCESS_TOKEN_SECRET`, `REFRESH_TOKEN_SECRET`

### 3. 运行项目

```bash
# 开发模式
npm run dev

# 生产模式
npm start
```

## 📂 核心结构

- `routes/`: API 路由定义 (Auth, Data, Admin, Upload)
- `middleware/`: 鉴权与权限校验中间件
- `utils/`: 通用工具类 (包括多平台视频解析逻辑)
- `scripts/`: 运维与数据库迁移脚本

## 📜 许可

本项目仅供学习交流使用。
