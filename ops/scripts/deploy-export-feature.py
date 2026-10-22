"""Deploy the additive export-auth feature and the rebuilt Web UI.

The script has a fixed action list: backup existing files, install audited files,
create two additive tables, rebuild Web static files, restart the API, and run a
real export smoke test. It never prints the credential. Passwords are read from
stdin only.
"""
import getpass
import io
import json
import posixpath
import tarfile
import time
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[2]
HOST = "47.109.86.76"
USER = "root"
REMOTE_APP = "/www/wwwroot/study_center"
REMOTE_WEB = "/www/wwwroot/study.zhzgo.cn"
BACKUP = "/www/backup/codex/study-center-20261022"
STAGE = "/tmp/study-export-20261022"

FILES = {
    "app.js": "apps/server/app.js",
    "routes/exports.js": "apps/server/routes/exports.js",
    "routes/admin_new/index.js": "apps/server/routes/admin_new/index.js",
    "routes/admin_new/export_credentials.js": "apps/server/routes/admin_new/export_credentials.js",
    "middleware/authMiddleware.js": "apps/server/middleware/authMiddleware.js",
    "services/securityLimits.js": "apps/server/services/securityLimits.js",
    "services/questionExport.js": "apps/server/services/questionExport.js",
    "scripts/question_export_migration.sql": "apps/server/scripts/question_export_migration.sql",
    "scripts/init_export_credentials.js": "apps/server/scripts/init_export_credentials.js",
}


def main():
    password = getpass.getpass("SSH credential: ")
    client = paramiko.SSHClient()
    client.load_system_host_keys()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=password, timeout=20,
                   auth_timeout=20, banner_timeout=20, look_for_keys=False, allow_agent=False)
    password = None

    def run(command, timeout=90):
        _, stdout, stderr = client.exec_command(command, timeout=timeout)
        code = stdout.channel.recv_exit_status()
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        if code != 0:
            raise RuntimeError(f"Remote command failed ({code}): {err[-800:]}")
        return out

    try:
        sftp = client.open_sftp()
        run(f"mkdir -p {STAGE}")
        for relative, local in FILES.items():
            src = ROOT / local
            if not src.is_file():
                raise RuntimeError(f"Missing deployment file: {src}")
            remote = f"{STAGE}/{relative}"
            run(f"mkdir -p {posixpath.dirname(remote)}")
            sftp.put(str(src), remote)

        # Keep the current Web and touched API files before replacement.
        run(f"mkdir -p {BACKUP}/export-feature-before")
        run(f"tar -czf {BACKUP}/export-feature-before/api-before.tar.gz -C {REMOTE_APP} app.js routes/admin_new/index.js middleware/authMiddleware.js services/securityLimits.js")
        run(f"tar -czf {BACKUP}/export-feature-before/web-before.tar.gz --exclude=.user.ini -C {REMOTE_WEB} .")

        for relative in FILES:
            target = f"{REMOTE_APP}/{relative}"
            run(f"install -m 644 -o www -g www {STAGE}/{relative} {target}")

        # Two additive tables only; no existing row is rewritten here.
        migration = run(f"cd {REMOTE_APP} && runuser -u www -- node scripts/init_export_credentials.js", timeout=120)

        # Build a byte-accurate static archive from the verified dist directory.
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode="w:gz") as archive:
            for item in (ROOT / "apps/web/dist").rglob("*"):
                if item.is_file():
                    archive.add(item, arcname=item.relative_to(ROOT / "apps/web/dist").as_posix())
        buffer.seek(0)
        remote_web = f"{STAGE}/web.tar.gz"
        sftp.putfo(buffer, remote_web)
        run(f"tar -xzf {remote_web} -C {REMOTE_WEB}")
        run(f"find {REMOTE_WEB} -name .user.ini -prune -o -exec chown www:www {{}} +")

        # Restart only after the additive migration succeeded.
        run("pkill -TERM -u www -f '^/usr/bin/node app.js$' || true")
        for _ in range(25):
            if not run("ss -lntp | grep -q ':9898 ' && echo open || true").strip():
                break
            time.sleep(1)
        run("runuser -u www -- env HOME=/tmp bash -c 'cd /www/wwwroot/study_center && exec setsid -f /usr/bin/node app.js >> /www/wwwlogs/nodejs/study_center.log 2>&1 < /dev/null'")
        for _ in range(30):
            code = run("curl -sk --max-time 3 -o /dev/null -w '%{http_code}' --resolve studyapi.zhzgo.cn:443:127.0.0.1 https://studyapi.zhzgo.cn/api/public/stats")
            if code.strip() == "200":
                break
            time.sleep(1)
        else:
            raise RuntimeError("API did not become healthy after restart")

        # End-to-end export check using a short-lived admin token; revoke in finally.
        verifier = f'''const fs=require("fs");const mysql=require("{REMOTE_APP}/node_modules/mysql2/promise");const jwt=require("{REMOTE_APP}/node_modules/jsonwebtoken");
require("{REMOTE_APP}/node_modules/dotenv").config({{path:"{REMOTE_APP}/.env"}});
(async()=>{{const c=await mysql.createConnection({{host:process.env.DB_HOST||"localhost",user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME}});const [[admin]]=await c.query("SELECT id FROM users WHERE role='admin' ORDER BY id LIMIT 1");const [[subject]]=await c.query("SELECT subject_id id FROM questions WHERE is_active=1 GROUP BY subject_id HAVING COUNT(*)<=100 ORDER BY COUNT(*) DESC LIMIT 1");if(!admin||!subject)throw new Error("Admin or small subject missing");const token=jwt.sign({{id:admin.id}},process.env.JWT_SECRET,{{expiresIn:"3m"}});const h={{Authorization:"Bearer "+token,"Content-Type":"application/json"}};let credential;try{{const generated=await fetch("https://studyapi.zhzgo.cn/api/admin/exports/"+admin.id,{{method:"POST",headers:h}});const body=await generated.json();if(generated.status!==201)throw new Error("credential generation failed");credential=body.password;const exported=await fetch("https://studyapi.zhzgo.cn/api/data/export/questions",{{method:"POST",headers:h,body:JSON.stringify({{subject_id:subject.id,password:credential}})}});const text=await exported.text();if(exported.status!==200||text.charCodeAt(0)!==0xFEFF||!text.includes("题目ID"))throw new Error("CSV export verification failed");console.log(JSON.stringify({{generated:true,exported:true,bytes:Buffer.byteLength(text),status:exported.status,subject:subject.id}}));}}finally{{if(credential)await fetch("https://studyapi.zhzgo.cn/api/admin/exports/"+admin.id,{{method:"DELETE",headers:h}});await c.end();}}}})().catch(e=>{{console.error(e.message);process.exitCode=1}});'''
        sftp.putfo(io.BytesIO(verifier.encode()), f"{STAGE}/verify-export.js")
        verify = run(f"cd {REMOTE_APP} && node {STAGE}/verify-export.js", timeout=120)
        print(json.dumps({"deployed": True, "migration": "tables_ready", "verification": json.loads(verify)}, ensure_ascii=False))
    finally:
        client.close()


if __name__ == "__main__":
    main()
