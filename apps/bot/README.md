# 烁学 QQ Bot

独立的 QQ 机器人进程，通过后端私有服务密钥访问题库接口。配置来自本地 `.env`，不提交到仓库。

```powershell
npm install
npm start
```

后端接口可用不代表外部 QQ webhook 已完成平台配置；启用前需要确认 `WEBHOOK_PORT`、QQ 应用凭据和 Nginx 回调转发。
