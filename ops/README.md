# 运维目录

| 目录 | 用途 |
| --- | --- |
| `scripts/` | SSH、备份、题库审计、加密演练、发布、接口回归 |
| `baseline/` | 生产改造前代码样本，仅用于对照 |
| `releases/2026-10-22/` | 本次服务端和 Web 发布包 |
| `reports/2026-10-22/` | 构建、测试和验收日志 |

脚本中的服务器路径是生产部署路径；本地启动前先确认当前环境。生产数据库、密钥和用户数据不放在本目录。

常用入口：

- `scripts/remote_session.py`：交互式 SSH，不持久化密码。
- `scripts/smoke-api.cjs`：匿名、免费、Plus、管理员和越权接口回归。
- `scripts/protect_question_storage.js` 不在此目录，位于 `apps/server/scripts/`，属于后端加密迁移工具。
- `scripts/deploy-release.sh`：生产发布，要求备份和切换证明已存在。
