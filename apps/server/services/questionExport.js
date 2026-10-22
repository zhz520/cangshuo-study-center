const crypto = require('node:crypto');
const bcrypt = require('bcryptjs');

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const MAX_EXPORT = 12000;

function generateExportPassword() {
  const bytes = crypto.randomBytes(16);
  const chars = [...bytes].map(byte => ALPHABET[byte % ALPHABET.length]).join('');
  return chars.match(/.{1,4}/g).join('-');
}

async function issueExportPassword(db, userId, adminId, days = 30) {
  const password = generateExportPassword();
  const hash = await bcrypt.hash(password, 12);
  const expiresAt = new Date(Date.now() + days * 24 * 60 * 60 * 1000);
  await db.query(`INSERT INTO question_export_credentials (user_id, password_hash, expires_at, generated_by)
    VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), expires_at = VALUES(expires_at),
    generated_by = VALUES(generated_by), last_used_at = NULL`, [userId, hash, expiresAt, adminId]);
  return { password, expiresAt };
}

async function verifyExportPassword(db, userId, password) {
  if (typeof password !== 'string' || !/^[A-Z2-9-]{16,24}$/i.test(password)) return null;
  const [rows] = await db.query('SELECT password_hash, expires_at FROM question_export_credentials WHERE user_id = ? AND expires_at > NOW()', [userId]);
  if (!rows.length) return null;
  return await bcrypt.compare(password.toUpperCase(), rows[0].password_hash) ? rows[0] : null;
}

function csvCell(value) {
  let text = value == null ? '' : String(value);
  if (/^[=+\-@]/.test(text)) text = `'${text}`;
  return `"${text.replace(/"/g, '""')}"`;
}

function questionsCsv(rows) {
  const header = ['题目ID', '科目', '题型', '难度', '题干', '选项', '答案', '解析', '标签'];
  const lines = [header.map(csvCell).join(',')];
  for (const row of rows) {
    const options = typeof row.options === 'string' ? row.options : JSON.stringify(row.options ?? {});
    const tags = Array.isArray(row.tags) ? row.tags.join('、') : String(row.tags ?? '');
    lines.push([row.id, row.subject_name, row.type, row.difficulty, row.content, options, row.answer, row.analysis, tags].map(csvCell).join(','));
  }
  return '\uFEFF' + lines.join('\r\n');
}

module.exports = { ALPHABET, MAX_EXPORT, generateExportPassword, issueExportPassword, verifyExportPassword, csvCell, questionsCsv };
