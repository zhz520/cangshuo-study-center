const express = require('express');
const pool = require('../config/db');
const { verifyToken } = require('../middleware/authMiddleware');
const { pageOptions, questionFilter, assertQuestion, questionHeaders } = require('../services/questionAccess');
const { rateLimit, chargeQuestions, sendError } = require('../services/securityLimits');
const { keywordFilter } = require('../services/questionStorage');
const { normalizeSearch } = require('../utils/questionCrypto');
const { scoreAnswer } = require('../utils/scoreAnswer');
const router = express.Router();
const columns = 'q.id, q.subject_id, q.type, q.difficulty, q.tags, q.content, q.options, q.answer, q.analysis, s.name AS subject_name';
const joins = 'JOIN subjects s ON s.id = q.subject_id';
const types = new Set(['single', 'multiple', 'judge', 'fill', 'material', 'sort']);

function integer(value, name) {
    if (!Number.isSafeInteger(Number(value)) || Number(value) <= 0) {
        const error = new Error(`${name}无效`); error.status = 400; throw error;
    }
    return Number(value);
}

const protectedRead = [verifyToken, rateLimit('question-read-user', 60), rateLimit('question-read-ip', 180)];

async function sendRows(req, res, rows, limit) {
    await chargeQuestions(req, rows.length);
    questionHeaders(req, res);
    res.set('X-Question-Has-More', String(rows.length === limit));
    return res.json(rows);
}

function get(path, handler) {
    router.get(path, ...protectedRead, async (req, res) => {
        try { await handler(req, res); } catch (error) { sendError(res, error); }
    });
}

get('/questions', async (req, res) => {
    const subject = integer(req.query.subject_id, '科目编号');
    const { limit, offset } = pageOptions(req.query);
    const filter = questionFilter(req);
    let where = `${filter.sql} AND q.subject_id = ?`;
    const params = [...filter.params, subject];
    if (req.query.type) {
        if (!types.has(req.query.type)) throw Object.assign(new Error('题型无效'), { status: 400 });
        where += ' AND q.type = ?'; params.push(req.query.type);
    }
    const [rows] = await pool.query(`SELECT ${columns} FROM questions q ${joins} WHERE ${where} ORDER BY q.id LIMIT ? OFFSET ?`, [...params, limit, offset]);
    await sendRows(req, res, rows, limit);
});

get('/questions/search', async (req, res) => {
    const { limit, offset } = pageOptions(req.query);
    const filter = questionFilter(req);
    let where = filter.sql;
    const params = [...filter.params];
    const keyword = String(req.query.keyword || '').trim();
    if (keyword.length > 80) throw Object.assign(new Error('搜索词不能超过80个字符'), { status: 400 });
    if (!keyword && !req.query.type && !req.query.subjectId) throw Object.assign(new Error('请填写搜索条件'), { status: 400 });
    if (keyword) {
        const search = keywordFilter(keyword);
        where += ` AND ${search.sql}`; params.push(...search.params);
    }
    if (req.query.type && req.query.type !== 'all') {
        if (!types.has(req.query.type)) throw Object.assign(new Error('题型无效'), { status: 400 });
        where += ' AND q.type = ?'; params.push(req.query.type);
    }
    if (req.query.subjectId && req.query.subjectId !== '0') {
        where += ' AND q.subject_id = ?'; params.push(integer(req.query.subjectId, '科目编号'));
    }
    const [rows] = await pool.query(`SELECT ${columns} FROM questions q ${joins} WHERE ${where} ORDER BY q.id DESC LIMIT ? OFFSET ?`, [...params, limit, offset]);
    // Verify blind-index candidates after decryption; never return a false match.
    await sendRows(req, res, keyword ? rows.filter(q => normalizeSearch(q.content).includes(normalizeSearch(keyword))) : rows, limit);
});

get('/questions/:id', async (req, res) => {
    const filter = questionFilter(req);
    const [rows] = await pool.query(`SELECT ${columns} FROM questions q ${joins} WHERE q.id = ? AND ${filter.sql}`, [integer(req.params.id, '题目编号'), ...filter.params]);
    if (!rows.length) return res.status(403).json({ error: '题目不可访问，请检查题库权限或会员状态', code: 'QUESTION_ACCESS_DENIED' });
    await chargeQuestions(req, 1);
    questionHeaders(req, res);
    res.json(rows[0]);
});

for (const [path, join, order] of [
    ['/mistakes', 'JOIN user_progress up ON up.question_id = q.id AND up.user_id = ? AND up.is_correct = 0 AND up.is_deleted = FALSE', 'q.id DESC'],
    ['/favorites/practice', 'JOIN user_favorites f ON f.question_id = q.id AND f.user_id = ?', 'f.created_at DESC, q.id DESC'],
    ['/favorites', 'JOIN user_favorites f ON f.question_id = q.id AND f.user_id = ?', 'f.created_at DESC, q.id DESC'],
]) {
    get(path, async (req, res) => {
        const { limit, offset } = pageOptions(req.query);
        const filter = questionFilter(req);
        const subjectSql = req.query.subject_id ? ' AND q.subject_id = ?' : '';
        const subjectParams = req.query.subject_id ? [integer(req.query.subject_id, '科目编号')] : [];
        const [rows] = await pool.query(`SELECT ${columns}${path === '/favorites' ? ', f.id AS favorite_id, f.created_at AS favorite_time' : ''} FROM questions q ${joins} ${join} WHERE ${filter.sql}${subjectSql} ORDER BY ${order} LIMIT ? OFFSET ?`, [req.userId, ...filter.params, ...subjectParams, limit, offset]);
        const payload = path === '/favorites' ? rows.map(q => ({ id: q.favorite_id, question_id: q.id, title: q.content, type: q.type, favorite_time: q.favorite_time })) : rows;
        await sendRows(req, res, payload, limit);
    });
}

get('/favorites/ids', async (req, res) => {
    const filter = questionFilter(req);
    const [rows] = await pool.query(`SELECT q.id FROM user_favorites f JOIN questions q ON q.id = f.question_id WHERE f.user_id = ? AND ${filter.sql}`, [req.userId, ...filter.params]);
    questionHeaders(req, res);
    res.json(rows.map(row => row.id));
});

get('/recommendation', async (req, res) => {
    const { limit } = pageOptions(req.query);
    const filter = questionFilter(req);
    const [rows] = await pool.query(`SELECT ${columns} FROM questions q ${joins}
        LEFT JOIN user_progress up ON up.question_id = q.id AND up.user_id = ? AND up.is_deleted = FALSE
        WHERE q.subject_id = ? AND ${filter.sql}
        ORDER BY CASE WHEN up.is_correct = 0 THEN 0 WHEN up.question_id IS NULL THEN 1 ELSE 2 END, RAND() LIMIT ?`,
        [req.userId, integer(req.query.subject_id, '科目编号'), ...filter.params, limit]);
    await sendRows(req, res, rows, limit);
});

get('/progress', async (req, res) => {
    const filter = questionFilter(req);
    const [rows] = await pool.query(`SELECT up.question_id, up.is_correct, up.answer, q.subject_id FROM user_progress up
        JOIN questions q ON up.question_id = q.id WHERE up.user_id = ? AND up.is_deleted = FALSE AND ${filter.sql}`, [req.userId, ...filter.params]);
    questionHeaders(req, res);
    res.json(rows);
});

router.post('/favorites', ...protectedRead, async (req, res) => {
    try {
        await assertQuestion(pool, req, req.body.question_id);
        await pool.query('INSERT IGNORE INTO user_favorites (user_id, question_id) VALUES (?, ?)', [req.userId, req.body.question_id]);
        res.json({ message: '收藏成功' });
    } catch (error) { sendError(res, error); }
});

router.post('/progress', ...protectedRead, async (req, res) => {
    try {
        await assertQuestion(pool, req, req.body.question_id);
        const [rows] = await pool.query('SELECT id, type, content, options, answer, analysis FROM questions WHERE id = ?', [req.body.question_id]);
        const answer = req.body.answer;
        if (Buffer.byteLength(JSON.stringify(answer ?? null)) > 20000) throw Object.assign(new Error('作答内容过长'), { status: 400 });
        const correct = scoreAnswer(rows[0], answer);
        await pool.query(`INSERT INTO user_progress (user_id, question_id, is_correct, answer, is_deleted) VALUES (?, ?, ?, ?, FALSE)
            ON DUPLICATE KEY UPDATE is_correct = VALUES(is_correct), answer = VALUES(answer), is_deleted = FALSE`,
            [req.userId, rows[0].id, correct, typeof answer === 'string' ? answer : JSON.stringify(answer)]);
        res.json({ message: 'Progress saved', is_correct: correct });
    } catch (error) { sendError(res, error); }
});

router.post(['/sync', '/progress/batch'], ...protectedRead, async (req, res) => {
    let connection;
    try {
        const { items, subject_id } = req.body;
        if (!Array.isArray(items) || !items.length || items.length > 200) throw Object.assign(new Error('每批需包含1到200条记录'), { status: 400 });
        const subject = integer(subject_id, '科目编号');
        const ids = items.map(item => integer(item.original_id, '原始题目编号'));
        if (new Set(ids).size !== ids.length) throw Object.assign(new Error('批次包含重复题目'), { status: 400 });
        const filter = questionFilter(req);
        connection = await pool.getConnection();
        await connection.beginTransaction();
        const [rows] = await connection.query(`SELECT q.id, q.original_id, q.type, q.content, q.options, q.answer, q.analysis FROM questions q
            WHERE q.subject_id = ? AND q.original_id IN (?) AND ${filter.sql}`, [subject, ids, ...filter.params]);
        if (rows.length !== items.length) throw Object.assign(new Error('批次包含无权访问或不存在的题目'), { status: 403 });
        for (const item of items) {
            const question = rows.find(q => Number(q.original_id) === Number(item.original_id));
            const answer = typeof item.answer === 'string' ? item.answer : JSON.stringify(item.answer ?? null);
            if (Buffer.byteLength(answer) > 20000) throw Object.assign(new Error('作答内容过长'), { status: 400 });
            await connection.query(`INSERT INTO user_progress (user_id, question_id, is_correct, answer, is_deleted) VALUES (?, ?, ?, ?, FALSE)
                ON DUPLICATE KEY UPDATE is_correct = VALUES(is_correct), answer = VALUES(answer), is_deleted = FALSE`,
                [req.userId, question.id, scoreAnswer(question, item.answer), answer]);
        }
        await connection.commit();
        res.json({ message: 'Sync completed', count: items.length });
    } catch (error) {
        if (connection) await connection.rollback();
        sendError(res, error);
    } finally { if (connection) connection.release(); }
});

module.exports = router;
