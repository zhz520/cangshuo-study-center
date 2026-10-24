const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const { IncomingMessage, ServerResponse } = require('node:http');
const { PassThrough } = require('node:stream');
const path = require('node:path');
const XLSX = require('xlsx');

const { parseExcelRow } = require('../routes/admin_new/import');

test('Excel rows normalize English and Chinese formats', () => {
  const single = parseExcelRow({ type: 'single', content: '题干', options: 'A:甲\nB:乙', answer: 'a', difficulty: '中', tags: '基础, 示例' });
  assert.equal(single.type, 'single');
  assert.deepEqual(single.options, { A: '甲', B: '乙' });
  assert.deepEqual(single.answer, ['A']);
  assert.equal(single.difficulty, 2);
  assert.deepEqual(single.tags, ['基础', '示例']);

  const multiple = parseExcelRow({ 类型: '多选题', 题干: '多选', 选项: 'A:甲\nB:乙\nC:丙', 答案: 'CA' });
  assert.deepEqual(multiple.answer, ['A', 'C']);

  const judge = parseExcelRow({ 题目类型: '判断题', 题干: '判断', 正确答案: '正确' });
  assert.deepEqual(judge.options, { A: '正确', B: '错误' });
  assert.deepEqual(judge.answer, ['A']);

  const fill = parseExcelRow({ type: 'fill', content: '填空', answer: '答案' });
  assert.equal(fill.answer, '答案');
  assert.throws(() => parseExcelRow({ type: 'single', options: 'A:x' }), /题干不能为空/);
});

test('the downloadable workbook is a valid template and every example row parses', () => {
  const file = path.join(__dirname, '../../web/public/question-import-template.xlsx');
  const workbook = XLSX.readFile(file);
  const rows = XLSX.utils.sheet_to_json(workbook.Sheets[workbook.SheetNames[0]]);
  assert.equal(rows.length, 4);
  for (const row of rows) assert.doesNotThrow(() => parseExcelRow(row));
});

let fixture;
const connection = {
  async query(sql, params) {
    fixture.queries.push({ sql, params });
    if (sql.includes('FROM subjects WHERE id')) return [fixture.subjectMissing ? [] : [{ id: params[0] }]];
    if (sql.includes('SAVEPOINT') || sql.includes('RELEASE SAVEPOINT')) return [{}];
    throw new Error('Unexpected fixture query: ' + sql);
  },
  async beginTransaction() { fixture.begin += 1; },
  async commit() { fixture.commit += 1; },
  async rollback() { fixture.rollback += 1; },
  release() { fixture.released += 1; },
};
const db = { query: connection.query, getConnection: async () => connection };
const storage = {
  async saveQuestion(_db, _table, record) { fixture.saved.push(record); return fixture.saved.length; },
  async writeQuestion(_pool, _table, record) { fixture.written.push(record); return 99; },
  keywordFilter: () => ({ sql: '1=1', params: [] }),
};
for (const [modulePath, exports] of [['../config/db', db], ['../services/questionStorage', storage]]) {
  const id = require.resolve(modulePath);
  require.cache[id] = { id, filename: id, loaded: true, exports };
}
const router = require('../routes/admin_new/questions');
const app = express();
app.use(express.json());
app.use('/questions', router);

async function postJson(url, body) {
  const payload = JSON.stringify(body);
  return new Promise((resolve, reject) => {
    const req = new IncomingMessage(new PassThrough());
    req.url = url; req.method = 'POST';
    req.headers = { 'content-type': 'application/json', 'content-length': Buffer.byteLength(payload) };
    const res = new ServerResponse(req);
    res.end = data => {
      try { resolve({ status: res.statusCode, body: JSON.parse(String(data)) }); }
      catch (error) { reject(error); }
      return res;
    };
    app.handle(req, res, error => reject(error || new Error('Unmatched route')));
    req.push(payload); req.push(null);
  });
}

test.beforeEach(() => {
  fixture = { queries: [], saved: [], written: [], begin: 0, commit: 0, rollback: 0, released: 1, subjectMissing: false };
});

test('JSON import rejects a subject that does not belong to the selected major', async () => {
  fixture.subjectMissing = true;
  const response = await postJson('/questions/import', { subject_id: 8, major_id: 3, questions: [{ type: 'single', stem: 'x' }] });
  assert.equal(response.status, 400);
  assert.equal(fixture.begin, 0);
  assert.equal(fixture.saved.length, 0);
});

test('JSON import saves valid rows and reports invalid rows without writing them', async () => {
  const response = await postJson('/questions/import', {
    subject_id: 8,
    major_id: 3,
    questions: [
      { type: 'single', stem: '有效题', options: { A: '甲', B: '乙' }, answer: ['A'] },
      { type: 'single', stem: '', options: {} },
    ],
  });
  assert.equal(response.status, 200);
  assert.equal(response.body.successCount, 1);
  assert.equal(response.body.totalCount, 2);
  assert.equal(response.body.errors.length, 1);
  assert.equal(fixture.saved.length, 1);
  assert.equal(fixture.saved[0].subject_id, 8);
  assert.equal(fixture.saved[0].content, '有效题');
  assert.equal(fixture.commit, 1);
  assert.equal(fixture.rollback, 0);
});

test('single question creation forwards the admin form payload to the encrypted writer', async () => {
  const response = await postJson('/questions', {
    subject_id: 8,
    type: 'single',
    difficulty: 2,
    content: '新增题目',
    options: { A: '甲', B: '乙' },
    answer: 'A',
    analysis: '解析',
    tags: ['基础'],
  });
  assert.equal(response.status, 201);
  assert.equal(fixture.written.length, 1);
  assert.equal(fixture.written[0].content, '新增题目');
  assert.equal(fixture.written[0].type, 'single');
  assert.equal(fixture.written[0].subject_id, 8);
});
