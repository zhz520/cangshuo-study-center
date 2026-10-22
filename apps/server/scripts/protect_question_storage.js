// No writes without --apply. Run against a backed-up DB during a maintenance window.
const crypto = require('node:crypto');
const { encryptionKey } = require('../config/security');
const { PREFIX, sealQuestion, openQuestion } = require('../utils/questionCrypto');
const { indexQuestion } = require('../services/questionStorage');

function createStorageProtection(pool, logger = console) {
async function exists(table) {
    const [rows] = await pool.query('SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?', [table]);
    return rows.length > 0;
}

async function prepare() {
    await pool.query(`CREATE TABLE IF NOT EXISTS question_search_tokens (
        question_id BIGINT NOT NULL, token BINARY(32) NOT NULL, position INT UNSIGNED NOT NULL,
        PRIMARY KEY (question_id, token, position), INDEX idx_question_token (token, question_id, position)
    ) ENGINE=InnoDB`);
    await pool.query(`CREATE TABLE IF NOT EXISTS security_rate_limits (
        bucket_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
        used INT UNSIGNED NOT NULL DEFAULT 0, expires_at DATETIME NOT NULL,
        INDEX idx_security_expiry (expires_at)
    ) ENGINE=InnoDB`);
    for (const table of ['questions', 'exam_paper_questions']) {
        if (!await exists(table)) continue;
        const [columns] = await pool.query('SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?', [table, 'content']);
        if (!['mediumtext', 'longtext'].includes(columns[0]?.DATA_TYPE)) {
            await pool.query(`ALTER TABLE ${table} MODIFY COLUMN content MEDIUMTEXT`);
        }
    }
    const [indexes] = await pool.query("SHOW INDEX FROM questions WHERE Key_name = 'idx_question_preview'");
    if (!indexes.length) await pool.query('ALTER TABLE questions ADD INDEX idx_question_preview (subject_id, is_active, id)');
    // Preserve today's already consumed AI quota when enabling the shared atomic budget.
    if (await exists('user_daily_usage')) {
        const window = Math.floor(Date.now() / 86400000);
        const [usage] = await pool.query('SELECT user_id, count FROM user_daily_usage WHERE date = ?', [new Date().toISOString().slice(0, 10)]);
        for (const row of usage) {
            const key = crypto.createHash('sha256').update(`ai-daily:${row.user_id}:${window}`).digest('hex');
            await pool.query(`INSERT INTO security_rate_limits (bucket_key, used, expires_at) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE used = GREATEST(used, VALUES(used))`, [key, row.count, new Date((window + 2) * 86400000)]);
        }
    }
}

async function migrate(table) {
    let lastId = 0;
    let processed = 0;
    while (true) {
        const db = await pool.getConnection();
        try {
            await db.beginTransaction();
            const [rows] = await db.query(`SELECT id, content, options, answer, analysis FROM ${table} WHERE id > ? ORDER BY id LIMIT 50 FOR UPDATE`, [lastId]);
            if (!rows.length) { await db.commit(); break; }
            for (const row of rows) {
                let plain;
                if (typeof row.content === 'string' && row.content.startsWith(PREFIX)) {
                    plain = openQuestion(row.content, table, row.id);
                } else {
                    plain = row;
                    const sealed = sealQuestion(row, table, row.id);
                    // Verify authentication and reversibility before clearing any plaintext fields.
                    const restored = openQuestion(sealed, table, row.id);
                    for (const field of ['content', 'options', 'answer', 'analysis']) {
                        if (JSON.stringify(restored[field]) !== JSON.stringify(row[field] ?? null)) throw new Error('Encryption round-trip failed');
                    }
                    await db.query(`UPDATE ${table} SET content = ?, options = '[]', answer = '', analysis = '' WHERE id = ?`, [sealed, row.id]);
                }
                if (table === 'questions') await indexQuestion(db, row.id, plain.content);
                lastId = Number(row.id);
            }
            await db.commit();
            processed += rows.length;
            logger.log(`${table}: verified/migrated ${processed} records`);
        } catch (error) {
            await db.rollback(); throw error;
        } finally { db.release(); }
    }
}

async function verify(table) {
    let lastId = 0;
    let verified = 0;
    while (true) {
        const [rows] = await pool.query(`SELECT id, content, options, answer, analysis FROM ${table} WHERE id > ? ORDER BY id LIMIT 200`, [lastId]);
        if (!rows.length) break;
        for (const row of rows) {
            openQuestion(row.content, table, row.id);
            if ((row.answer ?? '') !== '' || (row.analysis ?? '') !== '' || JSON.stringify(typeof row.options === 'string' ? JSON.parse(row.options) : row.options) !== '[]') {
                throw new Error(`${table} still has plaintext protected columns`);
            }
            lastId = Number(row.id); verified++;
        }
    }
    logger.log(`${table}: authenticated ${verified} encrypted records`);
}

async function scrubExamSnapshots() {
    if (!await exists('exam_records')) return;
    let lastId = 0;
    while (true) {
        const db = await pool.getConnection();
        try {
            await db.beginTransaction();
            const [rows] = await db.query('SELECT id, answers FROM exam_records WHERE id > ? ORDER BY id LIMIT 100 FOR UPDATE', [lastId]);
            if (!rows.length) { await db.commit(); break; }
            for (const row of rows) {
                let answers = row.answers;
                if (typeof answers === 'string') { try { answers = JSON.parse(answers); } catch { throw new Error('Invalid legacy exam snapshot; inspect it before migrating'); } }
                if (answers != null && !Array.isArray(answers)) throw new Error('Unexpected legacy exam snapshot format');
                if (Array.isArray(answers)) {
                    // Remove only duplicated protected question content. Keep scores, indexes and all user fields.
                    const sanitized = answers.map(answer => Object.fromEntries(Object.entries(answer).filter(([key]) =>
                        !['correct_answer', 'analysis', 'content', 'options', 'answer'].includes(key))));
                    await db.query('UPDATE exam_records SET answers = ? WHERE id = ?', [JSON.stringify(sanitized), row.id]);
                }
                lastId = Number(row.id);
            }
            await db.commit();
        } catch (error) { await db.rollback(); throw error; }
        finally { db.release(); }
    }
}

async function run({ apply = false, verifyOnly = false } = {}) {
    const tables = [];
    for (const table of ['questions', 'exam_paper_questions']) {
        if (!await exists(table)) { logger.log(`${table}: absent`); continue; }
        tables.push(table);
        const [[counts]] = await pool.query(`SELECT COUNT(*) AS total, SUM(content LIKE ?) AS encrypted FROM ${table}`, [`${PREFIX}%`]);
        logger.log(`${table}: total=${counts.total}, encrypted=${counts.encrypted || 0}`);
    }
    if (!apply && !verifyOnly) { logger.log('Read-only inspection complete. Use --apply to migrate, --verify to authenticate every row.'); return; }
    encryptionKey();
    // Reject an incorrect key before any DDL or mutation in a resumed migration.
    for (const table of tables) {
        const [rows] = await pool.query(`SELECT id, content FROM ${table} WHERE content LIKE ? LIMIT 1`, [`${PREFIX}%`]);
        if (rows.length) openQuestion(rows[0].content, table, rows[0].id);
    }
    if (apply) {
        await prepare();
        for (const table of tables) await migrate(table);
        await scrubExamSnapshots();
        await pool.query('DELETE FROM security_rate_limits WHERE expires_at < NOW()');
    }
    for (const table of tables) await verify(table);
    logger.log('Storage verification complete. Keep the encryption key backed up separately with restricted access.');
}

return { run, migrate, verify, scrubExamSnapshots };
}

async function main() {
    const args = process.argv.slice(2);
    if (args.includes('--help') || args.includes('-h')) {
        console.log('Usage: node scripts/protect_question_storage.js [--apply | --verify]');
        console.log('Without flags: read-only counts. --apply: encrypt/index in batches. --verify: authenticate every row.');
        return;
    }
    if (args.some(arg => !['--apply', '--verify'].includes(arg))) {
        console.error('Unknown option. Use --help.');
        process.exitCode = 1;
        return;
    }
    require('dotenv').config();
    const pool = require('mysql2/promise').createPool({
        host: process.env.DB_HOST || 'localhost', user: process.env.DB_USER || 'root',
        password: process.env.DB_PASSWORD || '', database: process.env.DB_NAME || 'study_center',
        connectionLimit: 2, multipleStatements: false,
    });
    try {
        await createStorageProtection(pool).run({ apply: process.argv.includes('--apply'), verifyOnly: process.argv.includes('--verify') });
    } catch {
        console.error('Storage protection failed. No question contents or credentials were logged. Check schema, connectivity and encryption key.');
        process.exitCode = 1;
    } finally { await pool.end(); }
}

if (require.main === module) main();
module.exports = { createStorageProtection };
