const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const express = require('express');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { PassThrough } = require('node:stream');
const { IncomingMessage, ServerResponse } = require('node:http');

process.env.JWT_SECRET = 'test-access-secret-export-routes-'.repeat(2);
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-export-routes-'.repeat(2);
process.env.QUESTION_ENCRYPTION_KEY = crypto.randomBytes(32).toString('base64');

const users = [
  { id: 1, username: 'alice', nickname: 'Alice', email: 'alice@example.com', role: 'user', has_subscription: 0 },
  { id: 2, username: 'demo', nickname: 'Demo', email: 'demo@example.com', role: 'demo', has_subscription: 0 },
  { id: 3, username: 'plus', nickname: 'Plus', email: 'plus@example.com', role: 'user', has_subscription: 1 },
  { id: 4, username: 'root', nickname: 'Admin', email: 'root@example.com', role: 'admin', has_subscription: 1 },
];
const credentials = new Map();
const logs = [];
const counters = new Map();
let deniedSubject = null;
let lastAdminListSql = '';

const questions = [
  ...Array.from({ length: 30 }, (_, index) => ({
    id: index + 1, subject_id: 10, type: 'single', difficulty: 1, tags: ['基础'],
    content: `科目十第 ${index + 1} 题`, options: { A: '甲', B: '乙' }, answer: 'A', analysis: '解析',
  })),
  ...Array.from({ length: 8 }, (_, index) => ({
    id: 101 + index, subject_id: 20, type: 'multiple', difficulty: 2, tags: [],
    content: `科目二十第 ${index + 1} 题`, options: { A: '甲' }, answer: 'A', analysis: '',
  })),
];

function visibleRows(sql, params) {
  const subjectId = Number(params.at(-1));
  if (subjectId === deniedSubject) return [];
  let rows = questions.filter(question => question.subject_id === subjectId);
  if (sql.includes('preview.preview_rank <= 20')) rows = rows.slice(0, 20);
  return rows;
}

const db = {
  async query(sql, params = []) {
    if (sql.startsWith('DELETE FROM security_rate_limits')) return [{ affectedRows: 0 }];
    if (sql.startsWith('INSERT IGNORE INTO security_rate_limits')) {
      if (!counters.has(params[0])) counters.set(params[0], 0);
      return [{ affectedRows: 1 }];
    }
    if (sql.startsWith('UPDATE security_rate_limits SET used')) {
      const [amount, key, , limit] = params;
      if (counters.get(key) + amount > limit) return [{ affectedRows: 0 }];
      counters.set(key, counters.get(key) + amount);
      return [{ affectedRows: 1 }];
    }
    if (sql.includes('(subscription_end_date > NOW()) AS has_subscription')) {
      const user = users.find(item => item.id === Number(params[0]));
      return [user ? [{ ...user, subscription_end_date: null }] : []];
    }
    if (sql.startsWith('SELECT password_hash, expires_at FROM question_export_credentials')) {
      const credential = credentials.get(Number(params[0]));
      if (!credential || credential.expiresAt <= new Date()) return [[]];
      return [[{ password_hash: credential.hash, expires_at: credential.expiresAt }]];
    }
    if (sql.startsWith('UPDATE question_export_credentials SET last_used_at')) {
      const credential = credentials.get(Number(params[0]));
      if (credential) credential.lastUsedAt = new Date();
      return [{ affectedRows: 1 }];
    }
    if (sql.startsWith('INSERT INTO question_export_logs')) {
      logs.push({ userId: params[0], subjectId: params[1], count: params[2], ip: params[3] });
      return [{ affectedRows: 1 }];
    }
    if (sql.startsWith('SELECT COUNT(*) AS total FROM questions q')) {
      return [[{ total: visibleRows(sql, params).length }]];
    }
    if (sql.startsWith('SELECT q.id, q.type')) {
      return [visibleRows(sql, params).map(row => ({ ...row, subject_name: row.subject_id === 10 ? '计算机基础' : '操作系统' }))];
    }
    if (sql.includes('FROM users u LEFT JOIN question_export_credentials c')) {
      lastAdminListSql = sql;
      const limit = Number(params.at(-2));
      const offset = Number(params.at(-1));
      const rows = users.slice(offset, offset + limit).map(user => {
        const credential = credentials.get(user.id);
        const active = Boolean(credential && credential.expiresAt > new Date());
        return {
          id: user.id, username: user.username, nickname: user.nickname, email: user.email, role: user.role,
          subscription_end_date: null, expires_at: credential?.expiresAt || null,
          updated_at: credential?.updatedAt || null, last_used_at: credential?.lastUsedAt || null,
          export_active: active ? 1 : 0,
        };
      });
      return [rows];
    }
    if (sql.startsWith('SELECT COUNT(*) AS total FROM users u')) return [[{ total: users.length }]];
    if (sql.startsWith('SELECT id, username, nickname FROM users WHERE id = ?')) {
      const user = users.find(item => item.id === Number(params[0]));
      return [user ? [{ id: user.id, username: user.username, nickname: user.nickname }] : []];
    }
    if (sql.startsWith('INSERT INTO question_export_credentials')) {
      credentials.set(Number(params[0]), {
        hash: params[1], expiresAt: new Date(params[2]), lastUsedAt: null, updatedAt: new Date(),
      });
      return [{ affectedRows: 1 }];
    }
    if (sql.startsWith('DELETE FROM question_export_credentials')) {
      credentials.delete(Number(params[0]));
      return [{ affectedRows: 1 }];
    }
    throw new Error(`Unexpected test database statement: ${sql.slice(0, 60)}`);
  },
};
require.cache[require.resolve('../config/db')] = {
  id: require.resolve('../config/db'), filename: require.resolve('../config/db'), loaded: true, exports: db,
};

const { verifyToken, isAdmin } = require('../middleware/authMiddleware');
const app = express();
app.use(express.json());
app.use('/api/data', require('../routes/exports'));
const adminRouter = express.Router();
adminRouter.use(verifyToken, isAdmin);
adminRouter.use('/exports', require('../routes/admin_new/export_credentials'));
app.use('/api/admin', adminRouter);

function tokenFor(id, role) {
  return jwt.sign({ id, role }, process.env.JWT_SECRET, { expiresIn: '15m' });
}
const adminToken = tokenFor(4, 'admin');
const userToken = tokenFor(1, 'user');
const demoToken = tokenFor(2, 'demo');

async function request(path, options = {}) {
  const token = options.token === null ? null : options.token || userToken;
  return new Promise((resolve, reject) => {
    const socket = new PassThrough();
    socket.remoteAddress = '127.0.0.1';
    const req = new IncomingMessage(socket);
    req.url = path;
    req.method = options.method || 'GET';
    req.headers = Object.fromEntries(Object.entries({
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    }).map(([key, value]) => [key.toLowerCase(), value]));
    req._body = true;
    req.body = options.body || {};
    const res = new ServerResponse(req);
    res.end = (data) => {
      resolve({ status: res.statusCode, headers: res.getHeaders(), body: data == null ? '' : String(data) });
      return res;
    };
    app.handle(req, res, error => reject(error || new Error('Unmatched test route')));
  });
}

async function issue(userId = 1) {
  const issued = await request(`/api/admin/exports/${userId}`, { method: 'POST', token: adminToken });
  assert.equal(issued.status, 201);
  return JSON.parse(issued.body).password;
}

test('demo accounts are refused both reading and generating export credentials', async () => {
  assert.equal((await request('/api/admin/exports', { token: demoToken })).status, 403);
  assert.equal((await request('/api/admin/exports/1', { method: 'POST', token: demoToken })).status, 403);
  assert.equal((await request('/api/admin/exports/1', { method: 'DELETE', token: demoToken })).status, 403);
  assert.equal((await request('/api/admin/exports', { token: null })).status, 401);
});

test('the administrator issues a random per-account password and regeneration revokes it', async () => {
  credentials.clear();
  const first = await issue(1);
  assert.match(first, /^[A-Z2-9]{4}(?:-[A-Z2-9]{4}){3}$/);
  const stored = credentials.get(1);
  assert.ok(!JSON.stringify([...credentials.values()]).includes(first));
  assert.equal(await bcrypt.compare(first, stored.hash), true);
  const second = await issue(1);
  assert.notEqual(first, second);
  assert.equal(await bcrypt.compare(first, credentials.get(1).hash), false);
  assert.equal(await bcrypt.compare(second, credentials.get(1).hash), true);
});

test('exports require a login, a valid password and a real subject', async () => {
  counters.clear();
  credentials.clear();
  const password = await issue(1);
  assert.equal((await request('/api/data/export/questions', { method: 'POST', token: null, body: { subject_id: 10, password } })).status, 401);
  assert.equal((await request('/api/data/export/questions', { method: 'POST', body: { subject_id: 10, password: 'AAAA-BBBB-CCCC-DDDD' } })).status, 403);
  assert.equal((await request('/api/data/export/questions', { method: 'POST', body: { subject_id: 'x', password } })).status, 400);
  const missing = await request('/api/data/export/questions', { method: 'POST', body: { subject_id: 999, password } });
  assert.equal(missing.status, 404);
});

test('a valid password exports only the account preview and writes an audit trail', async () => {
  counters.clear();
  credentials.clear();
  logs.length = 0;
  const password = await issue(1);
  const result = await request('/api/data/export/questions', { method: 'POST', body: { subject_id: 10, password } });
  assert.equal(result.status, 200);
  assert.match(result.headers['content-type'], /text\/csv/);
  assert.match(result.headers['cache-control'], /no-store/);
  assert.ok(result.body.startsWith('\uFEFF'));
  assert.equal(result.body.trim().split('\r\n').length, 21);
  assert.equal(logs.length, 1);
  assert.deepEqual({ subject: logs[0].subjectId, count: logs[0].count }, { subject: 10, count: 20 });
  assert.ok(credentials.get(1).lastUsedAt instanceof Date);
});

test('expired credentials stop working and are reported as inactive to the administrator', async () => {
  counters.clear();
  credentials.clear();
  const password = await issue(1);
  credentials.get(1).expiresAt = new Date(Date.now() - 60000);
  const denied = await request('/api/data/export/questions', { method: 'POST', body: { subject_id: 10, password } });
  assert.equal(denied.status, 403);
  const listed = await request('/api/admin/exports', { token: adminToken });
  assert.equal(listed.status, 200);
  const alice = JSON.parse(listed.body).users.find(user => user.id === 1);
  assert.equal(alice.has_export_password, false);
  assert.match(lastAdminListSql, /export_active/);
});

test('an account outside the subject ACL receives nothing to export', async () => {
  counters.clear();
  credentials.clear();
  const password = await issue(1);
  deniedSubject = 20;
  try {
    const result = await request('/api/data/export/questions', { method: 'POST', body: { subject_id: 20, password } });
    assert.equal(result.status, 404);
    assert.equal(logs.filter(log => log.subjectId === 20).length, 0);
  } finally {
    deniedSubject = null;
  }
});
