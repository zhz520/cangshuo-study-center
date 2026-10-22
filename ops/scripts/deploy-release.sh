#!/bin/bash
set -euo pipefail
ROOT=/www/wwwroot/study_center
REL=/root/study-security-release/server
BACKUP=/www/backup/codex/study-center-20261022
WEB=/www/wwwroot/study.zhzgo.cn
test -s "$BACKUP/encryption-promoted.json"
test -s "$BACKUP/release.env"

# The environment file contains the existing encrypted data key; preserve private mode.
install -m 600 -o www -g www "$BACKUP/release.env" "$ROOT/.env"
tar -czf /root/study-release-overlay.tgz -C "$REL" app.js package.json package-lock.json \
  routes services utils middleware config/security.js config/db.js config/jpush-config.json scripts/protect_question_storage.js
tar -xzf /root/study-release-overlay.tgz -C "$ROOT"
chown -R www:www "$ROOT/routes" "$ROOT/services" "$ROOT/utils" "$ROOT/middleware" "$ROOT/scripts"
chown www:www "$ROOT/app.js" "$ROOT/package.json" "$ROOT/package-lock.json" "$ROOT/config/security.js" "$ROOT/config/db.js" "$ROOT/config/jpush-config.json"
chmod 600 "$ROOT/.env"

tar -xzf /root/web-release-final.tar.gz -C "$WEB"
find "$WEB" -name .user.ini -prune -o -exec chown www:www {} +

# Give the bot the private backend service key and the local API endpoint.
python3 - <<'PY'
from pathlib import Path
import re
release=Path('/www/backup/codex/study-center-20261022/release.env').read_text(encoding='utf-8')
secret=re.search(r"^BOT_API_SECRET='([^']+)'",release,re.M).group(1)
p=Path('/www/wwwroot/qq-bot/.env')
lines=p.read_text(encoding='utf-8').splitlines() if p.exists() else []
out=[]
seen=set()
for line in lines:
    key=line.split('=',1)[0] if '=' in line else ''
    if key=='BOT_API_SECRET': out.append("BOT_API_SECRET='"+secret+"'"); seen.add(key)
    elif key=='BACKEND_API_BASE': out.append('BACKEND_API_BASE=http://127.0.0.1:9898/api/bot'); seen.add(key)
    else: out.append(line)
if 'BOT_API_SECRET' not in seen: out.append("BOT_API_SECRET='"+secret+"'")
if 'BACKEND_API_BASE' not in seen: out.append('BACKEND_API_BASE=http://127.0.0.1:9898/api/bot')
p.write_text('\n'.join(out)+'\n',encoding='utf-8')
p.chmod(0o600)
PY
chown www:www /www/wwwroot/qq-bot/.env

# Start under the same user as the previous service.
pkill -TERM -u www -f '^/usr/bin/node app.js$' 2>/dev/null || true
for attempt in $(seq 1 20); do
  if ! ss -lntp | grep -q ':9898 '; then break; fi
  sleep 1
done
runuser -u www -- env HOME=/tmp bash -c 'cd /www/wwwroot/study_center && exec setsid -f /usr/bin/node app.js >> /www/wwwlogs/nodejs/study_center.log 2>&1 < /dev/null'
sleep 1
pgrep -u www -f '^/usr/bin/node app.js$' | head -1 > /www/server/nodejs/vhost/pids/study_center.pid
for attempt in $(seq 1 30); do
  code=$(curl -sk --max-time 3 -o /dev/null -w '%{http_code}' --resolve studyapi.zhzgo.cn:443:127.0.0.1 https://studyapi.zhzgo.cn/ || true)
  if [ "$code" = 200 ]; then echo RELEASE_STARTED; exit 0; fi
  sleep 1
done
echo RELEASE_START_FAILED
tail -40 /www/wwwlogs/nodejs/study_center.log
exit 1
