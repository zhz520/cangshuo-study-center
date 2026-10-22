# 题库导出与账户导出密码

网页端提供按科目导出 CSV 的能力。导出不是普通下载：每个账户需要管理员单独生成一枚随机密码，登录态和导出密码必须同时具备，服务端仍会应用会员、专业和科目访问范围。

## 使用方式

1. 管理员进入「管理后台 → 导出授权」（/admin/exports），搜索账户并点击「生成密码」。
2. 密码只在这时显示一次，格式为 XXXX-XXXX-XXXX-XXXX，有效期为 30 天。管理员应通过可信渠道交付给账户本人。
3. 用户在「题库导出」（/export）选择专业和科目，输入导出密码后下载 CSV。
4. 重新生成会立即让旧密码失效；「撤销」会删除授权，已登录状态也不能再导出。

管理员界面不保存明文密码，只在生成响应中返回一次。数据库 question_export_credentials 仅保存 bcrypt 哈希、有效期、生成人和最近使用时间。

## 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | /api/admin/exports | 管理员 | 分页列出账户与授权状态，不返回密码或哈希 |
| POST | /api/admin/exports/:userId | 管理员 | 生成或重置该账户密码，返回明文一次 |
| DELETE | /api/admin/exports/:userId | 管理员 | 撤销授权 |
| POST | /api/data/export/questions | 登录 + 导出密码 | 按 subject_id 导出 CSV |

导出接口的失败状态：

- 400：科目编号无效。
- 401：未登录或登录过期。
- 403：导出密码错误、过期或已撤销。
- 404：当前账户在该科目没有可导出题目（含专业访问控制拒绝）。
- 413：单次超过 12,000 题，需要按科目拆分。
- 429：触发小时或每日预算，响应带 Retry-After。

## 限制与审计

- 单次最多 12,000 题。
- 每账户每小时最多 3 次导出请求，每 IP 每小时最多 10 次。
- 导出有独立的每日预算（每账户 20,000 题），不占用普通阅读的 5,000 题/日预算；管理员导出不受题量预算限制。
- 每次成功导出写入 question_export_logs：账户、科目、题量、IP、时间。
- 导出内容仍经过 questionFilter。免费账户只能导出每科前 20 题，Plus 账户按专业访问控制导出，管理员按全部有效题目导出。
- CSV 使用 UTF-8 BOM，字段全部加引号；以 =、+、-、@ 开头的单元格会加前缀单引号，避免电子表格公式注入。

## 数据表

```sql
question_export_credentials (
  user_id INT PRIMARY KEY,
  password_hash VARCHAR(255) NOT NULL,
  expires_at DATETIME NOT NULL,
  generated_by INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  last_used_at DATETIME NULL
)

question_export_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  subject_id INT NOT NULL,
  question_count INT NOT NULL,
  ip VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
)
```

初始化脚本是 apps/server/scripts/init_export_credentials.js，只执行 CREATE TABLE IF NOT EXISTS，不修改既有题库、用户或学习记录。

## 部署

1. 备份当前 API 文件、Web 目录和数据库。
2. 上传新增路由、服务和两个表结构脚本。
3. 在 API 目录以 www 用户执行 node scripts/init_export_credentials.js。
4. 重启 Node 服务，确认 /api/public/stats 返回 200。
5. 用管理员账户生成一次密码，导出一个小科目，再撤销；确认日志表和 CSV BOM 正常。

## 边界

导出密码解决的是「谁可以批量取得已授权题目」，不能阻止已经获得内容的用户复制、截图或转存。数据库加密保护静态数据和备份；HTTPS 保护传输；服务端权限决定下发范围。三者职责不同，不能承诺数学意义上的不可逆向。
