// Real HTTP integration test. Creates and removes only its own temporary account.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const crypto = require('node:crypto');
const root = process.env.STUDY_SERVER_ROOT || '/www/wwwroot/study_center';
require(root + '/node_modules/dotenv').config({ path: root + '/.env' });
const mysql = require(root + '/node_modules/mysql2/promise');
const jwt = require(root + '/node_modules/jsonwebtoken');
const { prepare, protectedPath } = require('./protected-api-client.cjs');
const base = process.env.STUDY_VERIFY_BASE || 'http://127.0.0.1:9899';
const checks = [];
const created = [];
let db;
async function request(name, path, { id, method = 'GET', body, expected = 200, secure = true } = {}) {
    const headers = { 'Content-Type': 'application/json', 'X-Forwarded-Proto': 'https' };
    if (id) headers.Authorization = 'Bearer ' + jwt.sign({ id }, process.env.JWT_SECRET, { expiresIn: '3m' });
    const transport = id && secure && protectedPath(path) ? prepare(method, path, headers.Authorization) : null;
    if (transport) Object.assign(headers, transport.headers);
    const res = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(30000) });
    const wire = await res.json();
    assert.equal(res.status, expected, name);
    const data = res.ok && transport ? transport.open(wire) : wire;
    checks.push({ name, status: res.status, encrypted: !!(transport && res.ok) });
    return { data, wire, headers: res.headers };
}
(async () => {
    db = await mysql.createConnection({ host: process.env.DB_HOST || 'localhost', user: process.env.DB_USER, password: process.env.DB_PASSWORD, database: process.env.DB_NAME });
    try {
        const username = 'transport_audit_' + crypto.randomBytes(8).toString('hex');
        const [inserted] = await db.query("INSERT INTO users (username,password,role,subscription_end_date) VALUES (?,?,'user',DATE_ADD(NOW(),INTERVAL 1 DAY))", [username, '$2a$10$disabled.audit.account.has.no.known.password']);
        const id = inserted.insertId; created.push(id);
        const [[subject]] = await db.query('SELECT subject_id id FROM questions WHERE is_active=1 GROUP BY subject_id HAVING COUNT(*)>50 LIMIT 1');
        const [[admin]] = await db.query("SELECT id FROM users WHERE role='admin' ORDER BY id LIMIT 1");
        const path = '/api/data/questions?subject_id=' + subject.id + '&limit=2';
        await request('anonymous rejected', path, { expected: 401 });
        await request('plaintext downgrade rejected', path, { id, secure: false, expected: 426 });
        const page = await request('encrypted practice list', path, { id });
        assert.equal(page.data.length, 2);
        assert.ok(!JSON.stringify(page.wire).includes(page.data[0].content));
        assert.ok(page.data.every(q => q.answer === null && q.analysis === null && q.answer_token));
        assert.match(page.headers.get('cache-control'), /no-store/);
        const question = page.data[0];
        await request('answer withheld before submission', '/api/data/questions/' + question.id + '/review?answer_token=' + question.answer_token, { id, expected: 403 });
        await request('cannot grade without reading', '/api/data/progress', { id, method: 'POST', body: { question_id: question.id, answer: 'A' }, expected: 403 });
        await request('cannot reuse ticket for different question', '/api/data/progress', { id, method: 'POST', body: { question_id: page.data[1].id, answer: 'A', answer_token: question.answer_token }, expected: 403 });
        const grade = await request('server grading and single-question reveal', '/api/data/progress', { id, method: 'POST', body: { question_id: question.id, answer: 'A', is_correct: true, answer_token: question.answer_token } });
        assert.ok(typeof grade.data.is_correct === 'boolean');
        assert.equal(grade.data.is_correct, require(root + '/utils/scoreAnswer').scoreAnswer(grade.data.question, 'A'));
        const review = await request('restore own explanation securely', '/api/data/questions/' + question.id + '/review?answer_token=' + question.answer_token, { id });
        assert.equal(review.data.answer, grade.data.question.answer);
        await request('history still requires read ticket', '/api/data/questions/' + question.id + '/review', { id, expected: 403 });
        for (const route of ['/api/data/questions/' + question.id, '/api/data/questions/search?type=single&limit=1', '/api/data/mistakes?limit=1', '/api/data/favorites/practice?limit=1', '/api/data/favorites?limit=1', '/api/data/recommendation?subject_id=' + subject.id + '&limit=1', '/api/data/progress', '/api/data/exams/records']) {
            await request('secured entry ' + route.split('?')[0], route, { id });
        }
        await request('admin encrypted questions', '/api/admin/questions?limit=1', { id: admin.id });
        await request('admin cannot downgrade', '/api/admin/questions?limit=1', { id: admin.id, secure: false, expected: 426 });
        await request('non-admin cannot open management', '/api/admin/questions?limit=1', { id, expected: 403 });
        await db.query('UPDATE users SET subscription_end_date=NULL WHERE id=?', [id]);
        const [[beyond]] = await db.query('SELECT id FROM questions WHERE subject_id=? AND is_active=1 ORDER BY id LIMIT 1 OFFSET 25', [subject.id]);
        await request('expired member cannot read beyond preview', '/api/data/questions/' + beyond.id, { id, expected: 403 });
        await request('public directory remains compatible', '/api/data/subjects', { id });
    } finally {
        for (const id of created) {
            await db.query('DELETE FROM user_progress WHERE user_id=?', [id]);
            await db.query('DELETE FROM users WHERE id=?', [id]);
        }
        await db.end();
    }
    const report = { ok: true, checks };
    if (process.env.STUDY_TRANSPORT_REPORT) fs.writeFileSync(process.env.STUDY_TRANSPORT_REPORT, JSON.stringify(report, null, 2), { mode: 0o600 });
    console.log(JSON.stringify(report));
})().catch(error => { console.error(JSON.stringify({ ok: false, error: error.message, checks })); process.exitCode = 1; });
