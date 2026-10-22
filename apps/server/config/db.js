const mysql = require('mysql2/promise');
require('dotenv').config();

const pool = mysql.createPool({
    host: process.env.DB_HOST || 'localhost',
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'study_center',
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
});

const { decryptResult } = require('../utils/questionCrypto');
const protectedConnection = Symbol('protectedQuestionConnection');
function protect(db) {
    if (db[protectedConnection]) return db;
    db[protectedConnection] = true;
    for (const method of ['query', 'execute']) {
        const original = db[method].bind(db);
        db[method] = async (...args) => {
            const sql = typeof args[0] === 'string' ? args[0] : args[0]?.sql || '';
            if (/(?:INSERT(?:\s+IGNORE)?\s+INTO|REPLACE\s+INTO|UPDATE)\s+`?(questions|exam_paper_questions)`?\b/i.test(sql)
                && !sql.startsWith('/* encrypted-question-write */')) {
                throw new Error('Direct question writes are disabled; use questionStorage to encrypt and index records');
            }
            return decryptResult(await original(...args));
        };
    }
    return db;
}
protect(pool);
const getConnection = pool.getConnection.bind(pool);
pool.getConnection = async () => protect(await getConnection());

module.exports = pool;
