// Production smoke test uses temporary accounts only; never resets an existing password.
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const root = '/www/wwwroot/study_center';
require(root + '/node_modules/dotenv').config({ path: root + '/.env' });
const mysql = require(root + '/node_modules/mysql2/promise');
const jwt = require(root + '/node_modules/jsonwebtoken');
const checks = [];
const createdIds = [];
const base = 'http://127.0.0.1:9898';
const testIp = '203.0.113.' + crypto.randomInt(2, 254);

async function request(path, { id, method = 'GET', body, expected = 200 } = {}) {
  const headers = { 'Content-Type': 'application/json', 'X-Forwarded-Proto': 'https', 'X-Forwarded-For': testIp };
  if (id) headers.Authorization = 'Bearer ' + jwt.sign({ id }, process.env.JWT_SECRET, { expiresIn: '3m' });
  const response = await fetch(base + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(30000),
  });
  const bytes = Buffer.from(await response.arrayBuffer());
  const data = response.headers.get('content-type')?.includes('application/json') ? JSON.parse(bytes.toString('utf8')) : null;
  assert.equal(response.status, expected, path + ' returned unexpected HTTP status');
  checks.push({ path, method, status: response.status });
  return { response, bytes, data };
}

(async () => {
  const db = await mysql.createConnection({ host: process.env.DB_HOST || 'localhost', user: process.env.DB_USER, password: process.env.DB_PASSWORD, database: process.env.DB_NAME });
  try {
    const [[admin]] = await db.query("SELECT id FROM users WHERE role='admin' ORDER BY id LIMIT 1");
    const [[subject]] = await db.query(`SELECT q.subject_id id, s.major_id, COUNT(*) total FROM questions q
      JOIN subjects s ON s.id=q.subject_id WHERE q.is_active=1 GROUP BY q.subject_id, s.major_id
      HAVING COUNT(*) BETWEEN 21 AND 100 ORDER BY COUNT(*) DESC LIMIT 1`);
    assert.ok(admin && subject, 'Administrator and small subject are required');
    async function create(role = 'user', paid = false) {
      const name = 'deploy_check_' + crypto.randomBytes(8).toString('hex');
      const [result] = await db.query('INSERT INTO users (username,password,role,subscription_end_date) VALUES (?,?,?,?)',
        [name, crypto.randomBytes(32).toString('hex'), role, paid ? new Date(Date.now() + 3600000) : null]);
      createdIds.push(result.insertId);
      return result.insertId;
    }
    const free = await create();
    const plus = await create('user', true);
    const demo = await create('demo');
    async function issue(id) {
      const result = await request('/api/admin/exports/' + id, { id: admin.id, method: 'POST', expected: 201 });
      assert.match(result.data.password, /^[A-Z2-9]{4}(?:-[A-Z2-9]{4}){3}$/);
      return result.data.password;
    }
    await request('/api/data/export/questions', { method: 'POST', body: { subject_id: subject.id }, expected: 401 });
    await request('/api/admin/exports', { id: free, expected: 403 });
    await request('/api/admin/exports', { id: demo, expected: 403 });
    await request('/api/admin/exports/' + free, { id: demo, method: 'POST', expected: 403 });
    const freePassword = await issue(free);
    const exportedFree = await request('/api/data/export/questions', { id: free, method: 'POST', body: { subject_id: subject.id, password: freePassword } });
    assert.deepEqual([...exportedFree.bytes.subarray(0, 3)], [0xef, 0xbb, 0xbf], 'CSV must have UTF-8 BOM bytes');
    assert.ok(exportedFree.bytes.toString('utf8').includes('题目ID'));
    assert.equal(exportedFree.response.headers.get('x-question-access'), 'preview');
    const [[freeLog]] = await db.query('SELECT question_count FROM question_export_logs WHERE user_id=? ORDER BY id DESC LIMIT 1', [free]);
    assert.equal(Number(freeLog.question_count), 20);
    const plusPassword = await issue(plus);
    const exportedPlus = await request('/api/data/export/questions', { id: plus, method: 'POST', body: { subject_id: subject.id, password: plusPassword } });
    assert.equal(exportedPlus.response.headers.get('x-question-access'), 'full');
    const [[plusLog]] = await db.query('SELECT question_count FROM question_export_logs WHERE user_id=? ORDER BY id DESC LIMIT 1', [plus]);
    assert.equal(Number(plusLog.question_count), Number(subject.total));
    await request('/api/data/export/questions', { id: demo, method: 'POST', body: { subject_id: subject.id, password: plusPassword }, expected: 403 });
    const reset = await issue(free);
    assert.notEqual(reset, freePassword);
    await request('/api/data/export/questions', { id: free, method: 'POST', body: { subject_id: subject.id, password: freePassword }, expected: 403 });
    await request('/api/admin/exports/' + free, { id: admin.id, method: 'DELETE' });
    await request('/api/data/export/questions', { id: free, method: 'POST', body: { subject_id: subject.id, password: reset }, expected: 403 });
    const [majors] = await db.query('SELECT id FROM majors WHERE id<>? LIMIT 1', [subject.major_id]);
    if (majors.length) {
      await db.query('INSERT INTO major_access_control (user_id,major_id) VALUES (?,?)', [plus, majors[0].id]);
      await request('/api/data/export/questions', { id: plus, method: 'POST', body: { subject_id: subject.id, password: plusPassword }, expected: 404 });
    }
    await db.query('UPDATE question_export_credentials SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE user_id=?', [plus]);
    await request('/api/data/export/questions', { id: plus, method: 'POST', body: { subject_id: subject.id, password: plusPassword }, expected: 403 });
    const listed = await request('/api/admin/exports?search=deploy_check_&limit=50', { id: admin.id });
    assert.equal(listed.data.users.find(user => user.id === plus).has_export_password, false);
    assert.ok(listed.data.users.every(user => !('password_hash' in user) && !('password' in user)));
  } finally {
    // Only accounts created by this invocation are removed. Existing account data stays intact.
    for (const id of createdIds) {
      await db.query('DELETE FROM major_access_control WHERE user_id=?', [id]);
      await db.query('DELETE FROM question_export_logs WHERE user_id=?', [id]);
      await db.query('DELETE FROM question_export_credentials WHERE user_id=?', [id]);
      await db.query('DELETE FROM users WHERE id=?', [id]);
    }
    await db.end();
  }
  const report = { ok: true, checks, temporaryAccountsCleaned: createdIds.length };
  if (process.env.STUDY_EXPORT_REPORT) fs.writeFileSync(process.env.STUDY_EXPORT_REPORT, JSON.stringify(report, null, 2), { mode: 0o600 });
  console.log(JSON.stringify(report));
})().catch(error => { console.error('Export verification failed: ' + error.message); process.exitCode = 1; });
