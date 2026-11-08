# Web 提示、加载、动画与性能统一

日期：2026-11-08。

## 统一反馈

- 新增常驻 `GlobalFeedback` 宿主，在应用启动时挂载全局 `Toast` 和 `ConfirmDialog`。
- 移除页面中的原生 `alert/confirm`，统一使用 `notify()` 和 `confirmAction()`。
- `ConfirmDialog` 同时支持组件 ref 调用和 `v-model:show` 受控调用，覆盖后台 CRUD 与答题页两套使用方式。
- Toast 去掉彩色 emoji、玻璃模糊和重阴影，改为左侧状态色细线加中性卡片。

## 加载与状态

- 新增 `LoadingState`、`ErrorState`，答题页和学情诊断页先使用统一加载样式。
- NProgress 顶部进度条接管懒加载路由和页面切换反馈。
- 统一空状态、加载状态和错误状态的字号、间距和重试按钮样式。

## 动画

- 新增唯一运动时长和缓动令牌：`--motion-fast`、`--motion-base`、`--ease-out`。
- 统一 `animate-fade-in-up`、`animate-scale-up`、`animate-slide-up` 的定义。
- 页面切换继续使用轻量淡入，保留 `prefers-reduced-motion` 关闭动画。
- 删除学情、统计、答题和成绩查询中未使用的旧动画、玻璃样式和渐变粒子样式。

## 性能

- 官网、首页、登录、注册和答题页改为路由级懒加载。
- 入口 JS 从约 1.1MB 降到约 208KB（gzip 约 80KB），入口 CSS 从约 166KB 降到约 109KB（gzip 约 19KB）。
- ECharts、Markdown、Quill、FileSaver 等大依赖只在对应路由加载。
- 题库导出不再由答题页本地生成 Word/打印，统一走 `/export` 的登录态、导出密码、会员/专业权限和审计。

## 验证

- `npm run build` 通过。
- Web 7 项测试通过。
- 27 个路由在 1440 和 390 视口无横向溢出、无控制台错误。
- 浏览器实测全局 Toast、确认框、题库导出路由和答题设置面板。
