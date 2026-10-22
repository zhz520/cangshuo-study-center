const { PREFIX } = require('../utils/questionCrypto');

async function protectedStorageReady(pool) {
    await pool.query('SELECT bucket_key, used, expires_at FROM security_rate_limits LIMIT 0');
    await pool.query('SELECT question_id, token, position FROM question_search_tokens LIMIT 0');
    for (const table of ['questions', 'exam_paper_questions']) {
        if (table === 'exam_paper_questions') {
            const [present] = await pool.query('SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?', [table]);
            if (!present.length) continue;
        }
        const [plaintext] = await pool.query(`SELECT id FROM ${table} WHERE content IS NULL OR content NOT LIKE ? LIMIT 1`, [`${PREFIX}%`]);
        if (plaintext.length) throw new Error('Question storage migration is required');
        // The protected DB adapter verifies both the key and authenticated row identity.
        await pool.query(`SELECT id, content FROM ${table} ORDER BY id LIMIT 1`);
    }
}

module.exports = { protectedStorageReady };
