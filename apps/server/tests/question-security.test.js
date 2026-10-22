const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const express = require('express');
const jwt = require('jsonwebtoken');

// Isolated fixtures: no connection to the configured local/production database.
process.env.JWT_SECRET = 'test-access-secret-material-'.repeat(3);
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-material-'.repeat(3);
process.env.QUESTION_ENCRYPTION_KEY = crypto.randomBytes(32).toString('base64');
process.env.BOT_API_SECRET = 'test-internal-bot-secret-'.repeat(3);

const { sealQuestion, openQuestion, searchTokens, searchEntries, decryptResult } = require('../utils/questionCrypto');
const { jwtConfig, encryptionKey } = require('../config/security');
const { scoreAnswer } = require('../utils/scoreAnswer');
const { pageOptions } = require('../services/questionAccess');
let account = { id: 1, role: 'user', has_subscription: 0 };
let allowedSubjects = null;
let savedProgress;
let selections = [];
const counters = new Map();
const questions = Array.from({ length: 200 }, (_, index) => ({
    id: index + 1, subject_id: index < 100 ? 10 : 20, type: (index + 1) % 2 ? 'single' : 'multiple',
    content: `测试题目 ${index + 1}`, options: { A: '正确选项', B: '错误选项' }, answer: 'A', analysis: '解释',
}));
const db = {
    async query(sql, params = []) {
        if (sql.startsWith('DELETE FROM security_rate_limits')) return [{ affectedRows: 0 }];
        if (sql.startsWith('INSERT IGNORE INTO security_rate_limits')) {
            if (!counters.has(params[0])) counters.set(params[0], 0);
            return [{ affectedRows: 1 }];
        }
        if (sql.startsWith('UPDATE security_rate_limits')) {
            const [amount, key, , limit] = params;
            if (counters.get(key) + amount > limit) return [{ affectedRows: 0 }];
            counters.set(key, counters.get(key) + amount); return [{ affectedRows: 1 }];
        }
        if (sql.includes('FROM users WHERE id = ?')) return [account ? [account] : []];
        if (sql.startsWith('SELECT setting_value FROM settings')) return [[{ setting_value: params[0].endsWith('plus') ? '2' : '1' }]];
        if (sql.startsWith('SELECT used FROM security_rate_limits')) return [[{ used: counters.get(params[0]) }]];
        if (sql.startsWith('SELECT setting_key, setting_value FROM settings')) {
            assert.match(sql, /WHERE setting_key IN/);
            assert.ok(!params[0].includes('smtp_pass'));
            return [[{ setting_key: 'site_title', setting_value: '测试平台' }, { setting_key: 'smtp_pass', setting_value: 'fixture-secret' }, { setting_key: 'future_private_key', setting_value: 'fixture-secret' }]];
        }
        if (sql.includes('FROM countdowns') || sql.includes('FROM external_links')) return [[]];
        if (sql.startsWith('INSERT INTO user_progress')) { savedProgress = params; return [{ affectedRows: 1 }]; }
        if (sql.startsWith('INSERT IGNORE INTO user_favorites')) return [{ affectedRows: 1 }];
        if (sql.includes('FROM questions WHERE id = ?')) return [[questions.find(q => q.id === Number(params[0]))].filter(Boolean)];
        if (sql.includes('FROM questions q') || sql.includes('JOIN questions q')) {
            selections.push(sql);
            let result = [...questions];
            // Evaluate the policy predicates present in the generated SQL, rather than trusting the token role.
            if (sql.includes('FROM major_access_control') && allowedSubjects) result = result.filter(q => allowedSubjects.includes(q.subject_id));
            if (sql.includes('preview.preview_rank <= 20')) result = result.filter(q => q.id <= (q.subject_id === 10 ? 20 : 120));
            if (sql.includes('WHERE q.id = ?')) result = result.filter(q => q.id === Number(params[0]));
            else if (sql.includes('q.subject_id = ?')) result = result.filter(q => q.subject_id === 10);
            if (sql.includes('AND q.type = ?')) result = result.filter(q => q.type === params.find(p => p === 'single' || p === 'multiple'));
            if (sql.includes('LIMIT ? OFFSET ?')) result = result.slice(params.at(-1), params.at(-1) + params.at(-2));
            else if (sql.includes('LIMIT ?')) result = result.slice(0, params.at(-1));
            return [result.map(q => ({ ...q }))];
        }
        throw new Error('Unexpected test database statement');
    },
    async getConnection() { return { query: db.query, release() {} }; },
};
require.cache[require.resolve('../config/db')] = { id: require.resolve('../config/db'), filename: require.resolve('../config/db'), loaded: true, exports: db };
const { reserve, reserveAi } = require('../services/securityLimits');
const { verifyToken, isAdmin, verifyBot } = require('../middleware/authMiddleware');
const { verifyQqIdentity, parseIdentity } = require('../services/qqIdentity');
const { saveQuestion, keywordFilter } = require('../services/questionStorage');
const app = express();
app.use(express.json());
app.use('/api/data', require('../routes/questions'));
require.cache[require.resolve('../utils/videoParser')] = { id: require.resolve('../utils/videoParser'), loaded: true, exports: { parseVideoWatermark: async () => ({}) } };
app.use('/api/public', require('../routes/public'));
const adminRouter = express.Router();
adminRouter.use(verifyToken, isAdmin);
adminRouter.get(['/questions', '/cards'], (_req, res) => res.json({ ok: true }));
app.use('/admin', adminRouter);
app.get('/bot', verifyBot, (_req, res) => res.json({ ok: true }));
async function request(path, options = {}) {
    const token = options.token === null ? null : options.token || jwt.sign({ id: 1, role: 'admin', subscription_end_date: '2099-01-01' }, process.env.JWT_SECRET, { expiresIn: '15m' });
    // Exercise actual Express routing/middleware without opening a network connection.
    return new Promise((resolve, reject) => {
        const socket = new (require('node:stream').PassThrough)();
        socket.remoteAddress = '127.0.0.1';
        const req = new (require('node:http').IncomingMessage)(socket);
        req.url = path; req.method = options.method || 'GET';
        req.headers = Object.fromEntries(Object.entries({
            ...(token ? { Authorization: `Bearer ${token}` } : {}), 'Content-Type': 'application/json', ...(options.headers || {}),
        }).map(([key, value]) => [key.toLowerCase(), value]));
        req._body = true; req.body = options.body || {};
        const res = new (require('node:http').ServerResponse)(req);
        res.end = (data) => {
            try {
                resolve({ status: res.statusCode, headers: new Headers(res.getHeaders()), body: JSON.parse(String(data)) });
            } catch (error) { reject(error); }
            return res;
        };
        app.handle(req, res, error => reject(error || new Error('Unmatched test route')));
    });
}

test('AES-GCM is randomized, reversible and bound to table and row', () => {
    const row = questions[0];
    const one = sealQuestion(row, 'questions', 1);
    const two = sealQuestion(row, 'questions', 1);
    assert.notEqual(one, two);
    assert.ok(!one.includes(row.content));
    assert.equal(openQuestion(one, 'questions', 1).content, row.content);
    assert.throws(() => openQuestion(one, 'questions', 2));
    assert.throws(() => openQuestion(one, 'exam_paper_questions', 1));
    const parts = one.split(':');
    const data = Buffer.from(parts.at(-1), 'base64'); data[0] ^= 1; parts[parts.length - 1] = data.toString('base64');
    assert.throws(() => openQuestion(parts.join(':'), 'questions', 1));
    assert.throws(() => openQuestion(one, 'questions', 1, crypto.randomBytes(32)));
    assert.throws(() => openQuestion('plaintext question', 'questions', 1));
});

test('field metadata decrypts aliases and projected fields without exposing excluded answers', () => {
    const row = { question_id: 1, title: sealQuestion(questions[0], 'questions', 1), options: [] };
    const fields = [
        { orgTable: 'questions', orgName: 'id', name: 'question_id' },
        { orgTable: 'questions', orgName: 'content', name: 'title' },
        { orgTable: 'questions', orgName: 'options', name: 'options' },
    ];
    const result = decryptResult([[row], fields]);
    assert.equal(result[0][0].title, questions[0].content);
    assert.deepEqual(result[0][0].options, questions[0].options);
    assert.equal(result[0][0].answer, undefined);
    assert.throws(() => decryptResult([[{ id: 2, content: sealQuestion(questions[0], 'questions', 1) }], [
        { orgTable: 'questions', orgName: 'id', name: 'id' }, { orgTable: 'questions', orgName: 'content', name: 'content' },
    ]]));
    assert.throws(() => decryptResult([[{ answer: 'A' }], [{ orgTable: 'questions', orgName: 'answer', name: 'answer' }]]));
});

test('blind search tokens support Unicode substrings and depend on the encryption key', () => {
    const indexed = searchTokens('计算机 ABC😀');
    for (const text of ['计', '计算', '计算机', 'abc', 'C😀']) {
        assert.ok(searchTokens(text, undefined, true).every(token => indexed.includes(token)));
    }
    assert.notDeepEqual(indexed, searchTokens('计算机 ABC😀', crypto.randomBytes(32)));
});

test('positional blind search requires consecutive text, including repeated and Unicode grams', () => {
    for (const [needle, content, expected] of [
        ['ababa', 'aba---bab', false], ['ababa', 'xababax', true],
        ['计算机知识', '了解计算机知识', true], ['😀计算机', '😀计算---计算机', false],
        ['a', 'abc', true], ['ABC', 'xxabcxx', true],
    ]) {
        const filter = keywordFilter(needle);
        const positions = [0, ...[...filter.sql.matchAll(/term\d+\.position = term0\.position \+ (\d+)/g)].map(match => Number(match[1]))];
        const tokens = [filter.params.at(-1), ...filter.params.slice(0, -1)].map(token => token.toString('hex'));
        const entries = searchEntries(content);
        const first = entries.filter(entry => entry.token === tokens[0]);
        const matched = first.some(anchor => positions.every((offset, index) => entries.some(entry => entry.position === anchor.position + offset && entry.token === tokens[index])));
        assert.equal(matched, expected, needle);
    }
});

test('public homepage settings never expose SMTP or newly added private configuration', async () => {
    const result = await request('/api/public/home-data', { token: null });
    assert.equal(result.status, 200);
    assert.deepEqual(result.body.settings, { site_title: '测试平台' });
    assert.ok(!JSON.stringify(result.body).includes('fixture-secret'));
});

test('weak/default signing keys and invalid encryption keys fail closed', () => {
    assert.throws(() => jwtConfig({ JWT_SECRET: 'secret', JWT_REFRESH_SECRET: 'x'.repeat(40) }));
    assert.throws(() => jwtConfig({ JWT_SECRET: 'x'.repeat(40), JWT_REFRESH_SECRET: 'x'.repeat(40) }));
    assert.throws(() => encryptionKey({ QUESTION_ENCRYPTION_KEY: 'password' }));
    assert.equal(encryptionKey().length, 32);
});

test('pagination rejects negative, fractional and overflowing values and caps requested size', () => {
    for (const query of [{ page: '-1' }, { page: '1.2' }, { limit: '0' }, { limit: 'NaN' }, { page: '999999999' }]) assert.throws(() => pageOptions(query));
    assert.deepEqual(pageOptions({ page: '2', limit: '99999' }), { page: 2, limit: 50, offset: 50 });
});

test('anonymous and forged JWT requests cannot read a question', async () => {
    assert.equal((await request('/api/data/questions/1', { token: null })).status, 401);
    const forged = jwt.sign({ id: 1, role: 'admin' }, 'attacker-key', { expiresIn: '15m' });
    assert.equal((await request('/api/data/questions/1', { token: forged })).status, 401);
    const noExpiry = jwt.sign({ id: 1 }, process.env.JWT_SECRET);
    assert.equal((await request('/api/data/questions/1', { token: noExpiry })).status, 401);
});

test('forged local member/admin claims cannot expand a free user preview, filters or page size', async () => {
    account = { id: 1, role: 'user', has_subscription: 0 }; allowedSubjects = null; counters.clear();
    const result = await request('/api/data/questions?subject_id=10&limit=99999');
    assert.equal(result.status, 200); assert.equal(result.body.length, 20);
    assert.equal(result.headers.get('x-question-access'), 'preview');
    assert.match(result.headers.get('cache-control'), /no-store/);
    assert.equal((await request('/api/data/questions?subject_id=10&page=2&limit=20')).body.length, 0);
    const filtered = await request('/api/data/questions?subject_id=10&type=multiple');
    assert.ok(filtered.body.every(q => q.id <= 20));
    assert.equal((await request('/api/data/questions/21')).status, 403);
    assert.equal((await request('/admin/questions')).status, 403);
    assert.equal((await request('/admin/QuEsTiOnS')).status, 403);
    assert.equal((await request('/admin/CARDS')).status, 403);
});

test('paid users are paged and still cannot cross the assigned subject access boundary', async () => {
    account = { id: 1, role: 'user', has_subscription: 1 }; allowedSubjects = [10]; counters.clear();
    const result = await request('/api/data/questions?subject_id=10&limit=99999');
    assert.equal(result.body.length, 50); assert.equal(result.headers.get('x-question-access'), 'full');
    assert.equal((await request('/api/data/questions/105')).status, 403);
    assert.equal((await request('/api/data/questions/25')).status, 200);
    account.has_subscription = 0;
    assert.equal((await request('/api/data/questions/25')).status, 403);
});

test('search, recommendations, favorites and mistakes all enforce the same preview and ACL', async () => {
    account = { id: 1, role: 'user', has_subscription: 0 }; allowedSubjects = [10]; counters.clear(); selections = [];
    for (const path of ['/questions/search?type=single', '/recommendation?subject_id=10', '/favorites/practice', '/mistakes']) {
        const result = await request('/api/data' + path);
        assert.equal(result.status, 200, path);
        assert.ok(result.body.every(q => q.id <= 20 && q.subject_id === 10), path);
    }
    assert.equal(selections.length, 4);
    for (const sql of selections) { assert.match(sql, /major_access_control/); assert.match(sql, /preview_rank <= 20/); assert.match(sql, /LIMIT \?/); }
});

test('progress correctness is computed on the server and inaccessible favorites are rejected', async () => {
    account = { id: 1, role: 'user', has_subscription: 0 }; allowedSubjects = null; counters.clear();
    const response = await request('/api/data/progress', { method: 'POST', body: { question_id: 1, answer: 'B', is_correct: true } });
    assert.equal(response.status, 200); assert.equal(savedProgress[2], false);
    assert.equal((await request('/api/data/favorites', { method: 'POST', body: { question_id: 21 } })).status, 403);
});

test('demo accounts cannot export admin question data and deleted accounts lose access', async () => {
    account = { id: 1, role: 'demo', has_subscription: 0 }; counters.clear();
    assert.equal((await request('/admin/questions')).status, 403);
    account = null;
    assert.equal((await request('/api/data/questions/1')).status, 401);
    account = { id: 1, role: 'user', has_subscription: 0 };
});

test('atomic shared budgets reject concurrent overuse and survive new requests', async () => {
    counters.clear();
    const results = await Promise.allSettled(Array.from({ length: 100 }, () => reserve('test-concurrent', 1, 1, 5, 60, db, 100000)));
    assert.equal(results.filter(result => result.status === 'fulfilled').length, 5);
    assert.ok(results.filter(result => result.status === 'rejected').every(result => result.reason.status === 429));
    await assert.rejects(reserve('test-concurrent', 1, 1, 5, 60, db, 100000), { status: 429 });
});

test('question delivery budgets return 429 and Retry-After without serving extra rows', async () => {
    account = { id: 1, role: 'user', has_subscription: 0 }; allowedSubjects = null; counters.clear();
    for (let i = 0; i < 50; i++) assert.equal((await request('/api/data/questions?subject_id=10')).status, 200);
    const denied = await request('/api/data/questions?subject_id=10');
    assert.equal(denied.status, 429);
    assert.ok(Number(denied.headers.get('retry-after')) > 0);
    assert.ok(!Array.isArray(denied.body));
});

test('database adapter rejects accidental plaintext writes and decrypts actual driver metadata', async () => {
    const code = require('node:fs').readFileSync(require.resolve('../config/db'), 'utf8');
    let calls = 0;
    const native = {
        async query(sql) {
            calls++;
            if (typeof sql === 'string' && sql.startsWith('SELECT')) return [[{ id: 1, content: sealQuestion(questions[0], 'questions', 1) }], [
                { orgTable: 'questions', orgName: 'id', name: 'id' }, { orgTable: 'questions', orgName: 'content', name: 'content' },
            ]];
            return [{ affectedRows: 1 }, undefined];
        },
        async execute(...args) { return this.query(...args); },
        async getConnection() { return this; },
    };
    const context = { process, Symbol, module: { exports: {} }, require(name) {
        if (name === 'mysql2/promise') return { createPool: () => native };
        if (name === 'dotenv') return { config() {} };
        if (name === '../utils/questionCrypto') return require('../utils/questionCrypto');
        throw new Error('Unexpected database adapter dependency');
    } };
    require('node:vm').runInNewContext(code, context);
    const adapter = context.module.exports;
    await assert.rejects(adapter.query('UPDATE questions SET content = ?', ['plaintext']), /Direct question writes/);
    await assert.rejects(adapter.query({ sql: 'INSERT INTO `exam_paper_questions` (content) VALUES (?)' }, ['plaintext']), /Direct question writes/);
    assert.equal(calls, 0);
    assert.equal((await adapter.query('SELECT id, content FROM questions'))[0][0].content, questions[0].content);
});

test('startup refuses an un-migrated database and requires security schemas', async () => {
    const { protectedStorageReady } = require('../services/protectedStorageReady');
    const statements = [];
    const readyDb = { async query(sql) { statements.push(sql); return [[]]; } };
    await protectedStorageReady(readyDb);
    assert.ok(statements.some(sql => sql.includes('security_rate_limits')));
    assert.ok(statements.some(sql => sql.includes('question_search_tokens')));
    await assert.rejects(protectedStorageReady({ async query(sql) {
        return [sql.includes('content IS NULL') ? [{ id: 1 }] : []];
    } }), /migration is required/);
});

test('all AI features share an atomic tier budget under concurrent requests', async () => {
    counters.clear();
    const req = { userId: 10, account: { role: 'user', has_subscription: true } };
    const results = await Promise.allSettled(Array.from({ length: 20 }, () => reserveAi(req)));
    assert.equal(results.filter(result => result.status === 'fulfilled').length, 2);
    await assert.rejects(reserveAi(req), { status: 429 });
    counters.clear();
    const free = { userId: 10, account: { role: 'user', has_subscription: false } };
    await reserveAi(free);
    await assert.rejects(reserveAi(free), { status: 429 });
});

test('bot endpoints require the private service credential', async () => {
    assert.equal((await request('/bot')).status, 401);
    assert.equal((await request('/bot', { headers: { 'X-Bot-API-Key': 'guessed' } })).status, 401);
    assert.equal((await request('/bot', { headers: { 'X-Bot-API-Key': process.env.BOT_API_SECRET } })).status, 200);
});

test('QQ login verifies both OpenID ownership and the configured application', async () => {
    const openid = 'A'.repeat(32);
    const http = { async get() { return { data: `callback( {"client_id":"123","openid":"${openid}"} );` }; } };
    assert.equal(parseIdentity('{"openid":"ok"}').openid, 'ok');
    await verifyQqIdentity(openid, 't'.repeat(32), http, '123');
    await assert.rejects(verifyQqIdentity('B'.repeat(32), 't'.repeat(32), http, '123'), { status: 401 });
    await assert.rejects(verifyQqIdentity(openid, 't'.repeat(32), http, 'different-app'), { status: 401 });
});

test('encrypted writes roll back an entire imported item if indexing fails', async () => {
    const calls = [];
    const transaction = { async query(sql, params) {
        calls.push({ sql, params });
        if (sql.includes('INSERT INTO questions')) return [{ insertId: 9 }];
        if (sql.startsWith('INSERT INTO question_search_tokens')) throw new Error('index unavailable');
        return [{ affectedRows: 1 }];
    } };
    await assert.rejects(saveQuestion(transaction, 'questions', questions[0]), /index unavailable/);
    assert.ok(calls.some(call => call.sql === 'ROLLBACK TO SAVEPOINT encrypted_question_write'));
    const update = calls.find(call => call.sql.includes('UPDATE questions'));
    assert.ok(update.params.some(value => typeof value === 'string' && value.startsWith('zhzgo:gcm:v1:')));
    assert.ok(!update.params.includes(questions[0].content));
});

test('server grading normalizes legacy answers while preserving ordering and text', () => {
    assert.ok(scoreAnswer({ type: 'multiple', answer: 'A,C' }, '["C","A"]'));
    assert.ok(scoreAnswer({ type: 'judge', answer: '["A"]' }, '正确'));
    assert.ok(!scoreAnswer({ type: 'sort', answer: '["A","B"]' }, '["B","A"]'));
    assert.ok(scoreAnswer({ type: 'fill', answer: '南京' }, ' 南京 '));
});
