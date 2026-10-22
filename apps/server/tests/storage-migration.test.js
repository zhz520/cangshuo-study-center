const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { createStorageProtection } = require('../scripts/protect_question_storage');
const { PREFIX, openQuestion } = require('../utils/questionCrypto');

process.env.QUESTION_ENCRYPTION_KEY = crypto.randomBytes(32).toString('base64');
const quiet = { log() {} };
const originals = [
    { id: 1, content: '中文题干 $x^2$', options: { A: '甲', B: '乙' }, answer: 'A', analysis: '**解析**' },
    { id: 2, content: '另一道题', options: '[{"label":"A","content":"选项"}]', answer: '["A"]', analysis: '' },
];

function database({ failIndex = false } = {}) {
    const state = { rows: structuredClone(originals), index: [], statements: [] };
    let snapshot;
    const db = {
        async query(sql, params = []) {
            state.statements.push(sql);
            if (sql.includes('information_schema.tables')) return [params[0] === 'questions' ? [{ present: 1 }] : []];
            if (sql.startsWith('SELECT COUNT(*)')) return [[{ total: state.rows.length, encrypted: state.rows.filter(row => row.content.startsWith(PREFIX)).length }]];
            if (sql.includes('WHERE content LIKE ?')) return [structuredClone(state.rows.filter(row => row.content.startsWith(PREFIX)).slice(0, 1))];
            if (sql.startsWith('SELECT id, content')) return [structuredClone(state.rows.filter(row => row.id > params[0]))];
            if (sql.startsWith('UPDATE questions SET content')) {
                const row = state.rows.find(row => row.id === params[1]);
                Object.assign(row, { content: params[0], options: '[]', answer: '', analysis: '' });
                return [{ affectedRows: 1 }];
            }
            if (sql.startsWith('DELETE FROM question_search_tokens')) {
                state.index = state.index.filter(entry => entry[0] !== params[0]); return [{ affectedRows: 1 }];
            }
            if (sql.startsWith('INSERT INTO question_search_tokens')) {
                if (failIndex) throw new Error('Simulated index failure');
                state.index.push(...params[0]); return [{ affectedRows: params[0].length }];
            }
            throw new Error('Unexpected migration test statement');
        },
        async getConnection() { return this; },
        async beginTransaction() { snapshot = structuredClone({ rows: state.rows, index: state.index }); },
        async commit() { snapshot = null; },
        async rollback() { state.rows = snapshot.rows; state.index = snapshot.index; snapshot = null; },
        release() {},
    };
    return { db, state };
}

test('default migration inspection performs only reads and does not change the question data', async () => {
    const { db, state } = database();
    await createStorageProtection(db, quiet).run();
    assert.deepEqual(state.rows, originals);
    assert.ok(state.statements.every(sql => sql.startsWith('SELECT')));
});

test('migration is lossless and safe to rerun without encrypting ciphertext again', async () => {
    const { db, state } = database();
    const migration = createStorageProtection(db, quiet);
    await migration.migrate('questions');
    await migration.verify('questions');
    const envelopes = state.rows.map(row => row.content);
    for (let i = 0; i < state.rows.length; i++) {
        const restored = openQuestion(state.rows[i].content, 'questions', state.rows[i].id);
        const { id, ...expected } = originals[i];
        assert.deepEqual(restored, expected);
        assert.equal(state.rows[i].answer, '');
        assert.equal(state.rows[i].analysis, '');
        assert.equal(state.rows[i].options, '[]');
    }
    assert.ok(state.index.length > 0);
    const indexSize = state.index.length;
    await migration.migrate('questions');
    assert.deepEqual(state.rows.map(row => row.content), envelopes);
    assert.equal(state.index.length, indexSize);
});

test('an indexing failure rolls back every question in the current migration batch', async () => {
    const { db, state } = database({ failIndex: true });
    await assert.rejects(createStorageProtection(db, quiet).migrate('questions'), /Simulated index failure/);
    assert.deepEqual(state.rows, originals);
    assert.equal(state.index.length, 0);
});

test('a resumed migration rejects the wrong key before any schema or data write', async () => {
    const { db, state } = database();
    const migration = createStorageProtection(db, quiet);
    await migration.migrate('questions');
    const key = process.env.QUESTION_ENCRYPTION_KEY;
    process.env.QUESTION_ENCRYPTION_KEY = crypto.randomBytes(32).toString('base64');
    state.statements = [];
    try {
        await assert.rejects(migration.run({ apply: true }));
        assert.ok(state.statements.every(sql => sql.startsWith('SELECT')));
    } finally { process.env.QUESTION_ENCRYPTION_KEY = key; }
});
