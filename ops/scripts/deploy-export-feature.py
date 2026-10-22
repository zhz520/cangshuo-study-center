"""Deploy the export feature with immutable backup and code-only rollback.

Only two additive tables are created. Existing questions, keys, user progress and
uploads are not rewritten. Password authentication is read interactively only.
"""
import getpass
import hashlib
import io
import json
import posixpath
import shlex
import tarfile
import time
from datetime import datetime, timezone
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[2]
HOST = '47.109.86.76'
REMOTE_APP = '/www/wwwroot/study_center'
REMOTE_WEB = '/www/wwwroot/study.zhzgo.cn'
RELEASE = datetime.now(timezone.utc).strftime('study-export-%Y%m%dT%H%M%SZ')
BACKUP = '/www/backup/codex/' + RELEASE
STAGE = '/root/' + RELEASE
FILES = {
    'app.js': 'apps/server/app.js',
    'routes/exports.js': 'apps/server/routes/exports.js',
    'routes/admin_new/index.js': 'apps/server/routes/admin_new/index.js',
    'routes/admin_new/export_credentials.js': 'apps/server/routes/admin_new/export_credentials.js',
    'middleware/authMiddleware.js': 'apps/server/middleware/authMiddleware.js',
    'services/securityLimits.js': 'apps/server/services/securityLimits.js',
    'services/questionExport.js': 'apps/server/services/questionExport.js',
    'services/integrationSettings.js': 'apps/server/services/integrationSettings.js',
    'services/geetestService.js': 'apps/server/services/geetestService.js',
    'routes/admin_new/integrations.js': 'apps/server/routes/admin_new/integrations.js',
    'routes/data.js': 'apps/server/routes/data.js',
    'routes/public.js': 'apps/server/routes/public.js',
    'scripts/question_export_migration.sql': 'apps/server/scripts/question_export_migration.sql',
    'scripts/init_export_credentials.js': 'apps/server/scripts/init_export_credentials.js',
}

SNAPSHOT = """const mysql=require('/www/wwwroot/study_center/node_modules/mysql2/promise');
require('/www/wwwroot/study_center/node_modules/dotenv').config({path:'/www/wwwroot/study_center/.env'});
(async()=>{const c=await mysql.createConnection({host:process.env.DB_HOST||'localhost',user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME});
const result={};for(const table of ['users','user_progress','user_favorites','exam_records','questions','exam_paper_questions']){
const [[count]]=await c.query('SELECT COUNT(*) total FROM '+table);result[table]=count.total;
if(table==='questions'||table==='exam_paper_questions'){
const [[check]]=await c.query(`SELECT COUNT(*) total,SUM(content LIKE 'zhzgo:gcm:v1:%') encrypted,
SUM(answer<>'' OR analysis<>'' OR (options IS NOT NULL AND CAST(options AS CHAR)<>'[]')) plaintext,
BIT_XOR(CRC32(CONCAT(id,':',content))) fingerprint FROM ${table}`);result[table]=check;}}
await c.end();console.log(JSON.stringify(result));})().catch(()=>{console.error('Data snapshot failed');process.exitCode=1;});"""


def main():
    dist = ROOT / 'apps/web/dist'
    if not (dist / 'index.html').is_file():
        raise RuntimeError('Build Web dist before deployment')
    for local in FILES.values():
        if not (ROOT / local).is_file():
            raise RuntimeError('Missing deployment file: ' + local)

    password = getpass.getpass('SSH credential: ')
    client = paramiko.SSHClient()
    client.load_system_host_keys()
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    try:
        client.connect(HOST, username='root', password=password, timeout=20,
                       auth_timeout=20, banner_timeout=20, look_for_keys=False, allow_agent=False)
    finally:
        password = None

    def progress(step, **detail):
        print(json.dumps({'step': step, **detail}, ensure_ascii=False), flush=True)

    def run(command, timeout=90):
        _, stdout, stderr = client.exec_command(command, timeout=timeout)
        channel = stdout.channel
        output, errors = [], []
        deadline = time.monotonic() + timeout
        while not channel.exit_status_ready() or channel.recv_ready() or channel.recv_stderr_ready():
            if channel.recv_ready():
                output.append(channel.recv(65536))
            if channel.recv_stderr_ready():
                errors.append(channel.recv_stderr(65536))
            if time.monotonic() > deadline:
                channel.close()
                raise RuntimeError('Remote operation timed out; inspect before retrying')
            time.sleep(.03)
        code = channel.recv_exit_status()
        if code:
            raise RuntimeError('Remote command failed (%s): %s' % (code, b''.join(errors).decode('utf8', 'replace')[-800:]))
        return b''.join(output).decode('utf8', 'replace')

    def restart():
        run("pkill -TERM -u www -f '^/usr/bin/node app.js$' || true")
        for _ in range(25):
            if not run("ss -lntp | grep -q ':9898 ' && echo open || true").strip():
                break
            time.sleep(1)
        else:
            raise RuntimeError('Existing API did not stop')
        run("runuser -u www -- env HOME=/tmp bash -c 'cd /www/wwwroot/study_center && exec setsid -f /usr/bin/node app.js >> /www/wwwlogs/nodejs/study_center.log 2>&1 < /dev/null'")
        for _ in range(30):
            code = run("curl -s --max-time 3 -o /dev/null -w '%{http_code}' -H 'X-Forwarded-Proto: https' http://127.0.0.1:9898/api/public/stats || true")
            if code.strip() == '200':
                run("pgrep -u www -f '^/usr/bin/node app.js$' | head -1 > /www/server/nodejs/vhost/pids/study_center.pid")
                return
            time.sleep(1)
        raise RuntimeError('API health check failed')

    installed = False
    original_files = []
    try:
        sftp = client.open_sftp()
        run('umask 077; mkdir ' + shlex.quote(STAGE) + '; mkdir -m 700 ' + shlex.quote(BACKUP))
        progress('connected', backup=BACKUP)
        for relative, local in FILES.items():
            remote = STAGE + '/' + relative
            run('mkdir -p ' + shlex.quote(posixpath.dirname(remote)))
            sftp.put(str(ROOT / local), remote)
            actual = run('sha256sum ' + shlex.quote(remote)).split()[0]
            expected = hashlib.sha256((ROOT / local).read_bytes()).hexdigest()
            if actual != expected:
                raise RuntimeError('Upload hash mismatch: ' + relative)
            exists = run('test -f ' + shlex.quote(REMOTE_APP + '/' + relative) + ' && echo yes || true').strip()
            if exists:
                original_files.append(relative)
        for name in ['backup-production.cjs', 'verify-export-production.cjs', 'smoke-api.cjs']:
            sftp.put(str(ROOT / 'ops/scripts' / name), STAGE + '/' + name)
        sftp.putfo(io.BytesIO(SNAPSHOT.encode()), STAGE + '/snapshot.cjs')
        before = json.loads(run('node ' + STAGE + '/snapshot.cjs'))
        sftp.putfo(io.BytesIO(json.dumps(before, indent=2).encode()), BACKUP + '/data-before.json')

        progress('backing_up_database_and_application')
        backup_info = json.loads(run('STUDY_BACKUP_DIR=' + BACKUP + ' node ' + STAGE + '/backup-production.cjs', timeout=360))
        run('gzip -t ' + BACKUP + '/study_center.sql.gz', timeout=120)
        run('umask 077; tar -czf ' + BACKUP + '/web-before.tar.gz --exclude=.user.ini -C ' + REMOTE_WEB + ' .', timeout=120)
        run('umask 077; tar -czf ' + BACKUP + '/api-before.tar.gz -C ' + REMOTE_APP + ' ' + ' '.join(shlex.quote(x) for x in original_files))
        sftp.putfo(io.BytesIO(json.dumps(original_files).encode()), BACKUP + '/original-api-files.json')
        progress('backup_verified', files=backup_info['files'])

        for relative in FILES:
            if relative.endswith('.js'):
                run('node --check ' + shlex.quote(STAGE + '/' + relative))
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode='w:gz') as archive:
            for item in dist.rglob('*'):
                if item.is_file():
                    archive.add(item, arcname=item.relative_to(dist).as_posix())
        buffer.seek(0)
        sftp.putfo(buffer, STAGE + '/web.tar.gz')

        installed = True
        for relative in FILES:
            run('install -m 644 -o www -g www ' + shlex.quote(STAGE + '/' + relative) + ' ' + shlex.quote(REMOTE_APP + '/' + relative))
        run('cd ' + REMOTE_APP + ' && runuser -u www -- /usr/bin/node scripts/init_export_credentials.js', timeout=120)
        progress('additive_schema_ready')
        run('tar -xzf ' + STAGE + '/web.tar.gz -C ' + REMOTE_WEB)
        run('find ' + REMOTE_WEB + ' -name .user.ini -prune -o -exec chown www:www {} +')
        restart()
        progress('api_restarted')
        verification = json.loads(run('STUDY_EXPORT_REPORT=' + BACKUP + '/export-smoke.json node ' + STAGE + '/verify-export-production.cjs', timeout=180))
        progress('export_verified', checks=len(verification['checks']))
        smoke = json.loads(run('STUDY_SMOKE_REPORT=' + BACKUP + '/live-api-smoke.json node ' + STAGE + '/smoke-api.cjs --live', timeout=180))
        progress('api_smoke_passed', checks=len(smoke['checks']))
        after = json.loads(run('node ' + STAGE + '/snapshot.cjs'))
        sftp.putfo(io.BytesIO(json.dumps(after, indent=2).encode()), BACKUP + '/data-after.json')
        for table in ['questions', 'exam_paper_questions']:
            if before[table] != after[table]:
                raise RuntimeError('Question data changed unexpectedly: ' + table)
        progress('deployed', backup=BACKUP, dataBefore=before, dataAfter=after)
    except Exception:
        if installed:
            progress('rolling_back_code')
            run('tar -xzf ' + BACKUP + '/api-before.tar.gz -C ' + REMOTE_APP)
            for relative in FILES:
                if relative not in original_files:
                    run('rm -f -- ' + shlex.quote(REMOTE_APP + '/' + relative))
            run('tar -xzf ' + BACKUP + '/web-before.tar.gz -C ' + REMOTE_WEB)
            restart()
            progress('code_rolled_back', backup=BACKUP)
        raise
    finally:
        client.close()


if __name__ == '__main__':
    main()
