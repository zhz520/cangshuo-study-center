# 服务器性能、内存与安全优化记录

日期：2026-11-07。服务器：Rocky Linux 9.4，2 vCPU / 1.7G 内存 / 40G 根分区。

## 初始状态

- 根分区 61% 使用，24G 已用、16G 可用。
- 内存 680M 已用、约 1G buff/cache，swap 836M 已用；CPU 负载约 0.03。
- MySQL 监听 `*:3306` 和 `*:33060`，应用实际通过 `::1` 连接。
- SSH 允许 root 密码登录，7 天内记录约 1,953 次失败密码。
- 防火墙公网开放 22、80、443、888、8089、24986；888 为仅本机可访问的 phpMyAdmin，8089 为 Nginx 反向代理的后端 API。
- 研究站点缺少 `X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy` 等响应头，TLS 仍允许 TLS 1.1和 3DES。

## 已实施

### 性能与内存

- 停止并 mask `packagekit.service`，释放约 25M 内存。
- journald 限制为 100M，执行 vacuum 释放约 103M；当前约 89M。
- `scpu-api.service` 设置 `MemoryHigh=256M`、`MemoryMax=384M`，当前约 141M。
- 增加 Nginx `open_file_cache`，Vite 哈希静态资源缓存 1 年。
- 增加 `/etc/logrotate.d/study-nginx`，按天轮转、保留 7 份、压缩、copytruncate。
- MySQL 重启后自动清理了旧 binlog，仅保留当前约 79M 文件，释放约 2.4G。

### 安全

- MySQL 改为只监听 `127.0.0.1,::1`，关闭未使用的 X Protocol 33060。
- 研究站点增加安全响应头、关闭 TLS 1.1、移除 3DES，保留 TLS 1.2/1.3。
- `/backup/`、`/backups/`、`/phpinfo.php` 明确返回 404，避免 SPA 回退掩盖敏感文件。
- SSH 设置 `MaxAuthTries 3`、`LoginGraceTime 30`、关闭 X11 转发，保留现有密码登录。
- fail2ban sshd 规则改为 3 次/10 分钟，封禁 24 小时；过滤器已验证可匹配现有 2,176 条失败记录。
- firewalld 移除 888、8089 和 cockpit 服务；phpMyAdmin 和后端 API 仍可由本机/Nginx 访问。
- `/usr/local/frps/frps.toml` 权限收紧为 600。

## 验证

- `study.zhzgo.cn`、`/admin`、`studyapi.zhzgo.cn` 均返回 200。
- 研究站点源站返回 HSTS、X-Content-Type-Options、X-Frame-Options、Referrer-Policy、Permissions-Policy；CDN 新请求也已透传。
- 静态资源返回 `cache-control: max-age=31536000`。
- TLS 1.1 握手被拒绝，TLS 1.2/1.3 可用。
- MySQL 仅监听 127.0.0.1/::1，Node 应用通过 ::1 正常连接。
- 最终根分区 22G 已用、19G 可用（55%）；内存约 851M 已用、856M 可用，swap 313M。

## 配置备份

`/root/perf-security-20261107/` 保存了 Nginx 研究站点配置、扩展配置、fail2ban 配置和 MySQL 配置。

## 仍需处理

- root 仍允许密码登录。服务器已有 2 个 root 公钥，可在确认可用的密钥登录后改为 `PermitRootLogin prohibit-password`。
- 宝塔面板 24986 仍对公网开放，建议在防火墙或云安全组限制到固定管理 IP。
- SELinux 当前为 Disabled；启用需要逐项验证 Nginx、MySQL、Node 和宝塔面板策略。
- `cangshuo_api` 的 8089 仍在本机监听，但公网防火墙已关闭；如无直连需求，可进一步改绑 127.0.0.1。
- frps 配置中存在明文 dashboard 密码，端口已被防火墙阻断；建议轮换密码并保持配置 600 权限。
