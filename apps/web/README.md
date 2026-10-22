# 烁学 (Zhzgo Study) - 网页前端

烁学全栈学习系统的网页前端，基于 Vue 3 和 Vite 构建，采用现代响应式设计。

## ✨ 特性

- **现代化 UI**: 采用 TailwindCSS 3 + CSS 变量主题，支持极致流畅的暗色模式。
- ** administrative Dashboard**: 完善的管理后台，支持用户、题目、资料及 APP 更新管理。
- **高级渲染**: 集成 `markdown-it` 和 `KaTeX`，完美支持富文本与数学公式。
- **会话管理**: 基于 JWT 的双令牌登录与自动续期机制。
- **响应式设计**: 完美适配手机、平板与桌面端。

## 🚀 技术栈

- **框架**: Vue 3 (Composition API)
- **构建工具**: Vite ^5.0.8
- **路由**: Vue Router 4
- **状态管理**: Pinia
- **样式**: TailwindCSS 3
- **组件库**: 原生 UI 组件 + Tailwind 定制

## 🛠️ 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 环境配置

根据 `.env.development` 和 `.env.production` 配置 API 地址。

### 3. 开发运行

```bash
npm run dev
```

### 4. 生产构建

```bash
npm run build
```

## 📜 许可

本项目仅供学习交流使用。
