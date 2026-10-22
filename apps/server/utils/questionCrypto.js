const crypto = require('node:crypto');
const { encryptionKey } = require('../config/security');

const PREFIX = 'zhzgo:gcm:v1:';
const TABLES = new Set(['questions', 'exam_paper_questions']);
const FIELDS = ['content', 'options', 'answer', 'analysis'];

function identity(table, id) {
  if (!TABLES.has(table) || !Number.isSafeInteger(Number(id)) || Number(id) <= 0) {
    throw new Error('Invalid encrypted question identity');
  }
  return `${table}:${Number(id)}`;
}

function sealQuestion(record, table, id, key = encryptionKey()) {
  const owner = identity(table, id);
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  cipher.setAAD(Buffer.from(`${PREFIX}${owner}`));
  const payload = Object.fromEntries(FIELDS.map(field => [field, record[field] ?? null]));
  const data = Buffer.concat([cipher.update(JSON.stringify(payload), 'utf8'), cipher.final()]);
  return `${PREFIX}${owner}:${iv.toString('base64')}:${cipher.getAuthTag().toString('base64')}:${data.toString('base64')}`;
}

function openQuestion(value, table, expectedId, key = encryptionKey()) {
  if (typeof value !== 'string' || !value.startsWith(PREFIX)) {
    throw new Error('Question has not been encrypted; run the question migration first');
  }
  const parts = value.slice(PREFIX.length).split(':');
  if (parts.length !== 5) throw new Error('Invalid encrypted question envelope');
  const [storedTable, id, ivText, tagText, dataText] = parts;
  const owner = identity(storedTable, id);
  if (storedTable !== table || (expectedId != null && Number(expectedId) !== Number(id))) {
    throw new Error('Encrypted question identity mismatch');
  }
  const iv = Buffer.from(ivText, 'base64');
  const tag = Buffer.from(tagText, 'base64');
  if (iv.length !== 12 || tag.length !== 16) throw new Error('Invalid encrypted question parameters');
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAAD(Buffer.from(`${PREFIX}${owner}`));
  decipher.setAuthTag(tag);
  const plain = Buffer.concat([decipher.update(Buffer.from(dataText, 'base64')), decipher.final()]);
  const payload = JSON.parse(plain.toString('utf8'));
  if (!FIELDS.every(field => Object.hasOwn(payload, field))) throw new Error('Invalid question payload');
  return payload;
}

function normalizeSearch(value) {
  return String(value ?? '').normalize('NFKC').toLowerCase();
}

function gramToken(text, key = encryptionKey()) {
  const indexKey = crypto.createHmac('sha256', key).update('zhzgo-question-search-v1').digest();
  return crypto.createHmac('sha256', indexKey).update(text).digest('hex');
}

function searchEntries(text, key = encryptionKey()) {
  const chars = [...normalizeSearch(text)];
  const indexKey = crypto.createHmac('sha256', key).update('zhzgo-question-search-v1').digest();
  const tokens = new Map();
  const entries = [];
  for (const length of [1, 2, 3]) {
    for (let position = 0; position + length <= chars.length; position++) {
      const gram = chars.slice(position, position + length).join('');
      if (!tokens.has(gram)) tokens.set(gram, crypto.createHmac('sha256', indexKey).update(gram).digest('hex'));
      entries.push({ token: tokens.get(gram), position });
    }
  }
  return entries;
}

// A keyed blind index permits substring candidates without storing the question text.
// It leaks equality/frequency of grams, so use a separate key and keep DB access restricted.
function searchTokens(text, key = encryptionKey(), query = false) {
  const chars = [...normalizeSearch(text)];
  const indexKey = crypto.createHmac('sha256', key).update('zhzgo-question-search-v1').digest();
  const lengths = query ? [Math.min(3, chars.length)] : [1, 2, 3];
  const tokens = new Set();
  for (const length of lengths) {
    if (!length) continue;
    for (let i = 0; i + length <= chars.length; i++) {
      tokens.add(crypto.createHmac('sha256', indexKey).update(chars.slice(i, i + length).join('')).digest('hex'));
    }
  }
  return [...tokens];
}

// mysql2 field metadata identifies the original table/column even for aliases and joins.
// This also covers report titles, favorites and server-side exam grading.
function decryptResult(result) {
  const [rows, fields] = result;
  if (!Array.isArray(rows) || !Array.isArray(fields)) return result;
  for (const table of TABLES) {
    const protectedFields = fields.filter(f => f.orgTable === table && FIELDS.includes(f.orgName));
    if (!protectedFields.length) continue;
    const content = protectedFields.find(f => f.orgName === 'content');
    if (!content) throw new Error('Protected question queries must include the content column');
    const idField = fields.find(f => f.orgTable === table && f.orgName === 'id');
    for (const row of rows) {
      if (row[content.name] == null) continue; // Unmatched LEFT JOIN.
      const payload = openQuestion(row[content.name], table, idField ? row[idField.name] : undefined);
      for (const field of protectedFields) row[field.name] = payload[field.orgName];
    }
  }
  return result;
}

module.exports = { PREFIX, FIELDS, TABLES, sealQuestion, openQuestion, searchTokens, searchEntries, gramToken, normalizeSearch, decryptResult };
