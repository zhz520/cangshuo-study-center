const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const { IncomingMessage, ServerResponse } = require('node:http');
const { PassThrough } = require('node:stream');

let state;
const connection = {
  async query(sql, params) {
    state.queries.push({ sql, params });
    if (sql.includes('FROM users WHERE id')) return [state.userMissing ? [] : [{ id: params[0] }]];
    if (sql.includes('FROM majors WHERE id IN')) return [params[0].map(id => ({ id })).slice(0, state.validMajorCount)];
    if (sql.startsWith('DELETE') || sql.startsWith('INSERT')) return [{ affectedRows: 1 }];
    throw new Error('Unexpected fixture query: ' + sql);
  },
  async beginTransaction() { state.begin += 1; },
  async commit() { state.commit += 1; },
  async rollback() { state.rollback += 1; },
  release() { state.released += 1; },
};
const db = { getConnection: async () => { state.connections += 1; return connection; } };
const id = require.resolve('../config/db');
require.cache[id] = { id, filename: id, loaded: true, exports: db };
const router = require('../routes/admin_new/access_control');
const app = express();
app.use(express.json());
app.use('/access-control', router);

async function postBatch(body) {
  const payload = JSON.stringify(body);
  return new Promise((resolve, reject) => {
    const stream = new PassThrough();
    const req = new IncomingMessage(stream);
    req.url = '/access-control/batch';
    req.method = 'POST';
    req.headers = { 'content-type': 'application/json', 'content-length': Buffer.byteLength(payload) };
    const res = new ServerResponse(req);
    res.end = data => {
      try {
        resolve({ status: res.statusCode, body: JSON.parse(String(data)) });
      } catch (error) {
        reject(error);
      }
      return res;
    };
    app.handle(req, res, error => reject(error || new Error('Unmatched route')));
    req.push(payload);
    req.push(null);
  });
}

test.beforeEach(() => {
  state = { queries: [], connections: 0, begin: 0, commit: 0, rollback: 0, released: 0, userMissing: false, validMajorCount: 2 };
});

test('batch access updates validate input before touching the database', async () => {
  const result = await postBatch({ user_id: 'not-a-number', major_ids: [1] });
  assert.equal(result.status, 400);
  assert.equal(state.connections, 0);
});

test('batch access updates deduplicate majors and commit in one transaction', async () => {
  const result = await postBatch({ user_id: 7, major_ids: [2, 1, 2] });
  assert.equal(result.status, 200);
  assert.deepEqual(result.body.major_ids, [2, 1]);
  assert.equal(state.begin, 1);
  assert.equal(state.commit, 1);
  assert.equal(state.rollback, 0);
  assert.equal(state.released, 1);
  const insert = state.queries.find(item => item.sql.startsWith('INSERT'));
  assert.deepEqual(insert.params[0], [[7, 2], [7, 1]]);
});

test('invalid user or major IDs roll back and never commit', async () => {
  state.userMissing = true;
  let result = await postBatch({ user_id: 999, major_ids: [] });
  assert.equal(result.status, 404);

  state = { queries: [], connections: 0, begin: 0, commit: 0, rollback: 0, released: 0, userMissing: false, validMajorCount: 1 };
  result = await postBatch({ user_id: 7, major_ids: [1, 2] });
  assert.equal(result.status, 400);
  assert.equal(state.commit, 0);
  assert.equal(state.rollback, 1);
  assert.equal(state.released, 1);
});

test('an empty major list intentionally clears restrictions', async () => {
  const result = await postBatch({ user_id: 7, major_ids: [] });
  assert.equal(result.status, 200);
  assert.deepEqual(result.body.major_ids, []);
  assert.equal(state.queries.filter(item => item.sql.startsWith('INSERT')).length, 0);
  assert.equal(state.commit, 1);
});
