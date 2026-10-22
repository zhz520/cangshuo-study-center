const { sealQuestion, searchEntries, gramToken, normalizeSearch, TABLES } = require('../utils/questionCrypto');
const { normalizeQuestionRecord } = require('../utils/questionFormat');

async function indexQuestion(db, id, content) {
  await db.query('DELETE FROM question_search_tokens WHERE question_id = ?', [id]);
  const entries = searchEntries(content);
  for (let start = 0; start < entries.length; start += 500) {
    await db.query('INSERT INTO question_search_tokens (question_id, token, position) VALUES ?', [
      entries.slice(start, start + 500).map(entry => [id, Buffer.from(entry.token, 'hex'), entry.position]),
    ]);
  }
}

async function saveQuestion(db, table, record, id) {
  await db.query('SAVEPOINT encrypted_question_write');
  try {
    const result = await persistQuestion(db, table, record, id);
    await db.query('RELEASE SAVEPOINT encrypted_question_write');
    return result;
  } catch (error) {
    await db.query('ROLLBACK TO SAVEPOINT encrypted_question_write');
    await db.query('RELEASE SAVEPOINT encrypted_question_write');
    throw error;
  }
}

async function persistQuestion(db, table, record, id) {
  if (!TABLES.has(table)) throw new Error('Invalid question table');
  const normalized = normalizeQuestionRecord(record);
  if (!normalized.safe || normalized.issues.length) throw Object.assign(new Error('题目格式或内容不完整：' + normalized.issues.join(', ')), { status: 400 });
  record = { ...record, ...normalized.record };
  const allowed = table === 'questions'
    ? ['subject_id', 'type', 'difficulty', 'tags', 'category', 'is_active', 'original_id']
    : ['paper_id', 'type', 'score', 'question_index', 'is_active'];
  const entries = allowed.filter(field => record[field] !== undefined).map(field => [field, record[field]]);
  if (id == null) {
    const columns = [...entries.map(([field]) => field), 'content', 'options', 'answer', 'analysis'];
    const [result] = await db.query(
      `/* encrypted-question-write */ INSERT INTO ${table} (${columns.join(',')}) VALUES (${columns.map(() => '?').join(',')})`,
      [...entries.map(([, value]) => value), '', '[]', '', ''],
    );
    id = result.insertId;
  }
  const encrypted = sealQuestion(record, table, id);
  await db.query(
    `/* encrypted-question-write */ UPDATE ${table} SET ${[...entries.map(([field]) => `${field} = ?`), 'content = ?', "options = '[]'", "answer = ''", "analysis = ''"].join(', ')} WHERE id = ?`,
    [...entries.map(([, value]) => value), encrypted, id],
  );
  if (table === 'questions') await indexQuestion(db, id, record.content);
  return id;
}

// Callers already doing batch transactions pass their connection to saveQuestion.
async function writeQuestion(pool, table, record, id) {
  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();
    if (id != null) {
      if (!TABLES.has(table)) throw new Error('Invalid question table');
      const [rows] = await connection.query(`SELECT * FROM ${table} WHERE id = ? FOR UPDATE`, [id]);
      if (!rows.length) throw Object.assign(new Error('题目不存在'), { status: 404 });
      record = { ...rows[0], ...Object.fromEntries(Object.entries(record).filter(([, value]) => value !== undefined)) };
    }
    const result = await saveQuestion(connection, table, record, id);
    await connection.commit();
    return result;
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    connection.release();
  }
}

function keywordFilter(keyword, alias = 'q') {
  if (!/^[a-z]+$/i.test(alias)) throw new Error('Invalid SQL alias');
  const chars = [...normalizeSearch(keyword)];
  if (!chars.length || chars.length > 80) throw Object.assign(new Error('搜索词需包含1到80个字符'), { status: 400 });
  const positions = [0];
  for (let position = 3; position + 3 <= chars.length; position += 3) positions.push(position);
  if (chars.length > 3 && positions.at(-1) !== chars.length - 3) positions.push(chars.length - 3);
  const tokens = positions.map(position => Buffer.from(gramToken(chars.slice(position, position + Math.min(3, chars.length)).join('')), 'hex'));
  const joins = positions.slice(1).map((position, index) => `JOIN question_search_tokens term${index + 1}
    ON term${index + 1}.question_id = term0.question_id AND term${index + 1}.position = term0.position + ${position}
    AND term${index + 1}.token = ?`).join(' ');
  return { sql: `EXISTS (SELECT 1 FROM question_search_tokens term0 ${joins}
    WHERE term0.question_id = ${alias}.id AND term0.token = ?)`, params: [...tokens.slice(1), tokens[0]] };
}

module.exports = { indexQuestion, saveQuestion, writeQuestion, keywordFilter };
