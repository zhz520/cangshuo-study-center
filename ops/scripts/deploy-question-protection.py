"""Publish the paired Web/API/APK after backup and isolated HTTP verification.

No question migration, key change or user-data restore is performed. The only
persistent database change is the 2.0.0 app version entry; tests clean their users.
"""
import getpass
import hashlib
import importlib.util
import io
import json
import os
import re
import posixpath
import shlex
import tarfile
import time
from datetime import datetime, timezone
from pathlib import Path
import paramiko

ROOT = Path(__file__).resolve().parents[2]
APP = '/www/wwwroot/study_center'
WEB = '/www/wwwroot/study.zhzgo.cn'
RELEASE = os.environ.get('STUDY_DEPLOY_RELEASE') or datetime.now(timezone.utc).strftime('study-protection-%Y%m%dT%H%M%SZ')
if not re.fullmatch(r'study-protection-\d{8}T\d{6}Z', RELEASE): raise ValueError('Invalid release identity')
STAGE = '/root/' + RELEASE
BACKUP = '/www/backup/codex/' + RELEASE
APK = 'app-release-2.0.0-20261023.apk'
NGINX_PROXY = '/www/server/panel/vhost/nginx/extension/study.zhzgo.cn/study-api.conf'
PROXY_CONFIG = '''# Managed study API proxy; keep ciphertext bytes and authentication headers intact.
location ^~ /api/ {
    proxy_pass http://127.0.0.1:9898;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 120s;
    proxy_buffering off;
}
'''
FILES = ['app.js', 'middleware/authMiddleware.js', 'routes/questions.js', 'routes/data.js',
         'services/securityLimits.js', 'services/questionTransport.js', 'services/questionDelivery.js', 'utils/scoreAnswer.js']
CHECKS = ['protected-api-client.cjs', 'verify-question-transport.cjs', 'verify-export-production.cjs', 'smoke-api.cjs']

spec = importlib.util.spec_from_file_location('prior_deploy', Path(__file__).with_name('deploy-export-feature.py'))
prior = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prior)

def main():
    dist = ROOT / 'apps/web/dist'
    apk = ROOT / 'artifacts/android' / APK
    assert (dist / 'index.html').is_file() and apk.is_file()
    client = paramiko.SSHClient()
    client.load_system_host_keys()
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    password = getpass.getpass('SSH credential: ')
    try:
        client.connect('47.109.86.76', username='root', password=password, look_for_keys=False, allow_agent=False, timeout=20)
    finally:
        password = None
    def report(step, **data):
        print(json.dumps({'step': step, **data}, ensure_ascii=False), flush=True)
    def run(command, timeout=120):
        _, stdout, stderr = client.exec_command(command, timeout=timeout)
        channel = stdout.channel
        output, errors = [], []
        deadline = time.monotonic() + timeout
        while not channel.exit_status_ready() or channel.recv_ready() or channel.recv_stderr_ready():
            if channel.recv_ready(): output.append(channel.recv(65536))
            if channel.recv_stderr_ready(): errors.append(channel.recv_stderr(65536))
            if time.monotonic() > deadline:
                channel.close()
                raise RuntimeError('Remote operation timed out; inspect before retrying')
            time.sleep(.05)
        if channel.recv_exit_status():
            raise RuntimeError('Remote operation failed: ' + b''.join(errors).decode('utf8', 'replace')[-900:] + b''.join(output).decode('utf8', 'replace')[-1800:])
        return b''.join(output).decode('utf8')
    def restart():
        run("pkill -TERM -u www -f '^/usr/bin/node app.js$' || true")
        for _ in range(30):
            if not run("ss -lntp | grep -q ':9898 ' && echo open || true").strip(): break
            time.sleep(1)
        else: raise RuntimeError('Old API did not stop')
        run("runuser -u www -- bash -c 'cd /www/wwwroot/study_center && exec setsid -f /usr/bin/node app.js >> /www/wwwlogs/nodejs/study_center.log 2>&1 < /dev/null'")
        for _ in range(30):
            if run("curl -s --max-time 3 -o /dev/null -w '%{http_code}' -H 'X-Forwarded-Proto: https' http://127.0.0.1:9898/api/public/stats || true").strip() == '200':
                run("pgrep -u www -f '^/usr/bin/node app.js$' | head -1 > /www/server/nodejs/vhost/pids/study_center.pid")
                return
            time.sleep(1)
        raise RuntimeError('API health check failed')
    def stop_shadow():
        run("if test -f " + STAGE + "/shadow.pid; then pid=$(cat " + STAGE + "/shadow.pid); case $pid in ''|*[!0-9]*) exit 1;; esac; if test -d /proc/$pid; then test $(readlink /proc/$pid/cwd) = " + STAGE + "/shadow && kill -TERM $pid; fi; fi")
    installed = False
    proxy_installed = False
    proxy_existed = False
    original = []
    try:
        sftp = client.open_sftp()
        if os.environ.get('STUDY_DEPLOY_RELEASE'):
            # A prior attempt stopped before installation. Reuse only its immutable backup.
            before = json.loads(run('cat ' + BACKUP + '/data-before.json'))
            original = json.loads(run('cat ' + BACKUP + '/original-files.json'))
            manifest = json.loads(run('cat ' + BACKUP + '/manifest.json'))
            for file, item in manifest['files'].items():
                if run('sha256sum ' + shlex.quote(BACKUP + '/' + file)).split()[0] != item['sha256']: raise RuntimeError('Backup verification failed')
            current = json.loads(run('node ' + STAGE + '/snapshot.cjs'))
            for table in ['questions', 'exam_paper_questions']:
                if before[table] != current[table]: raise RuntimeError('Question content changed since backup')
            report('backup_reverified', backup=BACKUP)
        else:
            run('umask 077; mkdir -m 700 ' + STAGE + ' ' + BACKUP + '; mkdir ' + STAGE + '/shadow')
            sftp.putfo(io.BytesIO(prior.SNAPSHOT.encode()), STAGE + '/snapshot.cjs')
            before = json.loads(run('node ' + STAGE + '/snapshot.cjs'))
            sftp.putfo(io.BytesIO(json.dumps(before, indent=2).encode()), BACKUP + '/data-before.json')
            report('backing_up', backup=BACKUP)
            sftp.put(str(ROOT / 'ops/scripts/backup-production.cjs'), STAGE + '/backup-production.cjs')
            backup = json.loads(run('STUDY_BACKUP_DIR=' + BACKUP + ' node ' + STAGE + '/backup-production.cjs', 420))
            run('gzip -t ' + BACKUP + '/study_center.sql.gz', 180)
            run('tar -czf ' + BACKUP + '/web-before.tar.gz --exclude=.user.ini -C ' + WEB + ' .', 180)
            for relative in FILES:
                if run('test -f ' + APP + '/' + relative + ' && echo yes || true').strip(): original.append(relative)
            run('tar -czf ' + BACKUP + '/api-before.tar.gz -C ' + APP + ' ' + ' '.join(shlex.quote(file) for file in original))
            sftp.putfo(io.BytesIO(json.dumps(original).encode()), BACKUP + '/original-files.json')
            report('backup_verified', files=backup['files'])
            run('cp -a ' + APP + '/.env ' + STAGE + '/shadow/.env; ln -s ' + APP + '/node_modules ' + STAGE + '/shadow/node_modules; chmod 700 ' + STAGE)
        for relative in ['config', 'middleware', 'routes', 'services', 'utils']:
            run('mkdir -p ' + STAGE + '/shadow/' + relative + '; cp -a ' + APP + '/' + relative + '/. ' + STAGE + '/shadow/' + relative + '/')
        run('cp -a ' + APP + '/app.js ' + STAGE + '/shadow/')
        for relative in FILES:
            remote = STAGE + '/shadow/' + relative
            sftp.put(str(ROOT / 'apps/server' / relative), remote)
            if run('sha256sum ' + remote).split()[0] != hashlib.sha256((ROOT / 'apps/server' / relative).read_bytes()).hexdigest(): raise RuntimeError('Upload mismatch')
            run('node --check ' + remote)
        for name in CHECKS:
            sftp.put(str(ROOT / 'ops/scripts' / name), STAGE + '/' + name)
        if run("ss -lnt | grep ':9899 ' || true").strip(): raise RuntimeError('Shadow port is occupied')
        run("bash -c 'cd " + STAGE + "/shadow; PORT=9899 LISTEN_HOST=127.0.0.1 nohup node app.js > " + STAGE + "/shadow.log 2>&1 < /dev/null & echo $! > " + STAGE + "/shadow.pid'")
        for _ in range(30):
            if run("curl -s --max-time 3 -o /dev/null -w '%{http_code}' -H 'X-Forwarded-Proto: https' http://127.0.0.1:9899/api/public/stats || true").strip() == '200': break
            time.sleep(1)
        else: raise RuntimeError('Isolated API failed to start: ' + run('tail -15 ' + STAGE + '/shadow.log'))
        result = json.loads(run('STUDY_SERVER_ROOT=' + STAGE + '/shadow STUDY_TRANSPORT_REPORT=' + BACKUP + '/shadow-transport.json node ' + STAGE + '/verify-question-transport.cjs', 180))
        report('shadow_passed', checks=len(result['checks']))
        stop_shadow()
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode='w:gz') as archive:
            for file in dist.rglob('*'):
                if file.is_file(): archive.add(file, arcname=file.relative_to(dist).as_posix())
        buffer.seek(0); sftp.putfo(buffer, STAGE + '/web.tar.gz')
        sftp.put(str(apk), STAGE + '/' + APK)
        if run('sha256sum ' + STAGE + '/' + APK).split()[0] != hashlib.sha256(apk.read_bytes()).hexdigest(): raise RuntimeError('APK upload mismatch')
        proxy_existed = bool(run('test -f ' + NGINX_PROXY + ' && echo yes || true').strip())
        if proxy_existed: run('cp -a ' + NGINX_PROXY + ' ' + BACKUP + '/nginx-api-before.conf')
        sftp.putfo(io.BytesIO(PROXY_CONFIG.encode()), STAGE + '/study-api.conf')
        proxy_installed = True
        run('install -m 644 ' + STAGE + '/study-api.conf ' + NGINX_PROXY)
        run('/www/server/nginx/sbin/nginx -t && /www/server/nginx/sbin/nginx -s reload')
        installed = True
        for relative in FILES: run('install -m 644 -o www -g www ' + STAGE + '/shadow/' + relative + ' ' + APP + '/' + relative)
        run('tar -xzf ' + STAGE + '/web.tar.gz -C ' + WEB)
        run('mkdir -p ' + WEB + '/downloads; install -m 644 -o www -g www ' + STAGE + '/' + APK + ' ' + WEB + '/downloads/' + APK)
        run('find ' + WEB + ' -name .user.ini -prune -o -exec chown www:www {} +')
        restart()
        report('api_web_installed')
        for host, label in [('https://studyapi.zhzgo.cn', 'api'), ('https://study.zhzgo.cn', 'web')]:
            result = json.loads(run('STUDY_VERIFY_BASE=' + host + ' STUDY_TRANSPORT_REPORT=' + BACKUP + '/' + label + '-transport.json node ' + STAGE + '/verify-question-transport.cjs', 180))
            report('transport_verified_' + label, checks=len(result['checks']))
        result = json.loads(run('STUDY_EXPORT_REPORT=' + BACKUP + '/export-smoke.json node ' + STAGE + '/verify-export-production.cjs', 180))
        report('export_verified', checks=len(result['checks']))
        result = json.loads(run('STUDY_SMOKE_REPORT=' + BACKUP + '/live-api-smoke.json node ' + STAGE + '/smoke-api.cjs --live', 180))
        report('api_verified', checks=len(result['checks']))
        after = json.loads(run('node ' + STAGE + '/snapshot.cjs'))
        sftp.putfo(io.BytesIO(json.dumps(after, indent=2).encode()), BACKUP + '/data-after.json')
        for table in ['questions', 'exam_paper_questions']:
            if before[table] != after[table]: raise RuntimeError('Question content changed: ' + table)
        # Publish update metadata only after API/Web/download verification. Never replace passwords or learning data.
        publisher = """require('/www/wwwroot/study_center/node_modules/dotenv').config({path:'/www/wwwroot/study_center/.env'});
const db=require('/www/wwwroot/study_center/config/db');(async()=>{
const url='https://study.zhzgo.cn/downloads/app-release-2.0.0-20261023.apk';
const [rows]=await db.query('SELECT id FROM app_versions WHERE version_code=200');
if(rows.length)throw new Error('Version 200 already exists; inspect before publishing');
await db.query('INSERT INTO app_versions (version_name,version_code,download_url,update_log,is_force,status) VALUES (?,?,?,?,?,?)',
['2.0.0',200,url,'升级题库安全通道；服务端判分；作答后查看解析；修复学习统计与答题卡分页。',1,1]);
await db.end();console.log(JSON.stringify({published:true,version:200,url}));})().catch(()=>{console.error('Version publish failed');process.exitCode=1});"""
        sftp.putfo(io.BytesIO(publisher.encode()), STAGE + '/publish-version.cjs')
        run('curl -fsSI https://study.zhzgo.cn/downloads/' + APK + ' > /dev/null')
        published = json.loads(run('node ' + STAGE + '/publish-version.cjs'))
        report('deployed', backup=BACKUP, before=before, after=after, app=published)
    except Exception:
        if proxy_installed:
            if proxy_existed: run('cp -a ' + BACKUP + '/nginx-api-before.conf ' + NGINX_PROXY)
            else: run('rm -f -- ' + NGINX_PROXY)
            run('/www/server/nginx/sbin/nginx -t && /www/server/nginx/sbin/nginx -s reload')
        if installed:
            report('rolling_back_code')
            run('tar -xzf ' + BACKUP + '/api-before.tar.gz -C ' + APP)
            for relative in FILES:
                if relative not in original: run('rm -f -- ' + APP + '/' + relative)
            run('tar -xzf ' + BACKUP + '/web-before.tar.gz -C ' + WEB)
            restart()
            report('code_rolled_back', backup=BACKUP)
        raise
    finally:
        stop_shadow()
        client.close()

if __name__ == '__main__': main()
