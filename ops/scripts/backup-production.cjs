// Run on the authorized Linux host. Never prints credentials or row contents.
const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const crypto = require('node:crypto');
const root = '/www/wwwroot/study_center';
require(path.join(root, 'node_modules/dotenv')).config({ path: path.join(root, '.env') });
const backup = process.env.STUDY_BACKUP_DIR || '/www/backup/codex/study-center-20261022';
if (!/^\/www\/backup\/codex\/[a-zA-Z0-9_-]+$/.test(backup)) throw new Error('Invalid backup directory');
fs.mkdirSync(backup, { recursive: true, mode: 0o700 });
fs.chmodSync(backup, 0o700);
const cnf = path.join(backup, '.mysql-client.cnf');
const quote = value => '"' + String(value || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n') + '"';
try {
  fs.writeFileSync(cnf, '[client]\nhost=' + quote(process.env.DB_HOST || 'localhost') + '\nuser=' + quote(process.env.DB_USER || 'root') + '\npassword=' + quote(process.env.DB_PASSWORD) + '\n', { mode: 0o600 });
  const sql = path.join(backup, 'study_center.sql');
  if (fs.existsSync(sql) || fs.existsSync(sql + '.gz')) throw new Error('Existing backup must not be overwritten');
  const fd = fs.openSync(sql, 'wx', 0o600);
  const dump = cp.spawnSync('mysqldump', ['--defaults-extra-file=' + cnf, '--single-transaction', '--quick', '--routines', '--triggers', '--events', '--hex-blob', '--no-tablespaces', process.env.DB_NAME || 'study_center'], { stdio: ['ignore', fd, 'pipe'], timeout: 180000 });
  fs.closeSync(fd);
  if (dump.status !== 0) throw new Error('Database backup failed');
  if (cp.spawnSync('gzip', [sql]).status !== 0) throw new Error('Compression failed');
  const archive = path.join(backup, 'application.tar.gz');
  const tar = cp.spawnSync('tar', ['-czf', archive, '--exclude=node_modules', '--exclude=.git', '-C', root, '.'], { timeout: 180000 });
  if (tar.status !== 0) throw new Error('Source/config/upload backup failed');
  fs.chmodSync(archive, 0o600);
  const hashes = {};
  for (const file of ['study_center.sql.gz', 'application.tar.gz']) hashes[file] = { bytes: fs.statSync(path.join(backup, file)).size, sha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(backup, file))).digest('hex') };
  fs.writeFileSync(path.join(backup, 'manifest.json'), JSON.stringify({ time: new Date().toISOString(), files: hashes }, null, 2), { mode: 0o600 });
  console.log(JSON.stringify({ backup, files: hashes }));
} catch (e) { console.error(e.message); process.exitCode = 1; }
finally { if (fs.existsSync(cnf)) fs.unlinkSync(cnf); }
