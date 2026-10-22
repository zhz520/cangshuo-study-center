const { test } = require('node:test');
const assert = require('node:assert/strict');
const bcrypt = require('bcryptjs');
const { generateExportPassword, issueExportPassword, verifyExportPassword, csvCell, questionsCsv, MAX_EXPORT } = require('../services/questionExport');

// The export budget is independent from the 5000/day reading budget.
process.env.JWT_SECRET = 'test-access-secret-export-'.repeat(3);
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-export-'.repeat(3);
const budget = { counters: new Map() };
const budgetDb = {
  async query(sql, params = []) {
    if (sql.startsWith('DELETE FROM security_rate_limits')) return [{ affectedRows: 0 }];
    if (sql.startsWith('INSERT IGNORE INTO security_rate_limits')) {
      if (!budget.counters.has(params[0])) budget.counters.set(params[0], 0);
      return [{ affectedRows: 1 }];
    }
    if (sql.startsWith('UPDATE security_rate_limits')) {
      const [amount, key, , limit] = params;
      if (budget.counters.get(key) + amount > limit) return [{ affectedRows: 0 }];
      budget.counters.set(key, budget.counters.get(key) + amount);
      return [{ affectedRows: 1 }];
    }
    throw new Error('Unexpected budget statement');
  },
};
require.cache[require.resolve('../config/db')] = { id: require.resolve('../config/db'), loaded: true, exports: budgetDb };
const { chargeExport, chargeQuestions } = require('../services/securityLimits');

function credentialDb() {
  const state = { hash: null, expires: null };
  return {
    state,
    async query(sql, params = []) {
      if (sql.startsWith('INSERT INTO question_export_credentials')) {
        state.hash = params[1]; state.expires = params[2]; return [{ affectedRows: 1 }];
      }
      if (sql.startsWith('SELECT password_hash')) return [[{ password_hash: state.hash, expires_at: state.expires }]];
      throw new Error('Unexpected statement');
    },
  };
}

test('export passwords are randomized, human-readable and account-scoped', async () => {
  const a = generateExportPassword();
  const b = generateExportPassword();
  assert.match(a, /^[A-Z2-9]{4}(?:-[A-Z2-9]{4}){3}$/);
  assert.notEqual(a, b);
  const db = credentialDb();
  const issued = await issueExportPassword(db, 7, 1);
  assert.equal(await bcrypt.compare(issued.password, db.state.hash), true);
  assert.ok(await verifyExportPassword(db, 7, issued.password.toLowerCase()));
  assert.equal(await verifyExportPassword(db, 7, 'AAAA-BBBB-CCCC-DDDD'), null);
});

test('CSV is BOM-prefixed, quoted and neutralizes spreadsheet formulas', () => {
  assert.equal(csvCell('=1+1'), '"\'=1+1"');
  assert.equal(csvCell('a"b'), '"a""b"');
  const csv = questionsCsv([{ id: 1, subject_name: '计算机', type: 'single', difficulty: 1, content: '=cmd', options: { A: '甲' }, answer: 'A', analysis: '@风险', tags: ['基础'] }]);
  assert.ok(csv.startsWith('\uFEFF'));
  assert.match(csv, /"'=cmd"/);
  assert.match(csv, /"'@风险"/);
  assert.equal(MAX_EXPORT, 12000);
});

test('exports use a dedicated daily budget that does not inherit the 5000/day reading cap', async () => {
  budget.counters.clear();
  const req = { userId: 42, ip: '127.0.0.1', account: { role: 'user', has_subscription: 1 } };
  await chargeExport(req, 12000, budgetDb, 1000);
  await assert.rejects(chargeExport(req, 12000, budgetDb, 2000), { status: 429 });
  budget.counters.clear();
  for (let hour = 0; hour < 5; hour++) await chargeQuestions(req, 900, budgetDb, hour * 3600000);
  await assert.rejects(chargeQuestions(req, 900, budgetDb, 5 * 3600000), { status: 429 });
});
