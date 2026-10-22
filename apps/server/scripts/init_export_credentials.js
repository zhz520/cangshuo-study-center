const fs = require('node:fs');
const path = require('node:path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const pool = require('../config/db');

(async () => {
  const sql = fs.readFileSync(path.join(__dirname, 'question_export_migration.sql'), 'utf8');
  for (const statement of sql.split(';').map(s => s.trim()).filter(Boolean)) await pool.query(statement);
  console.log('Question export tables are ready.');
  await pool.end();
})().catch(async error => {
  console.error('Unable to prepare question export tables:', error.code || error.message);
  try { await pool.end(); } catch {}
  process.exitCode = 1;
});
