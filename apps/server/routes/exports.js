const express = require('express');
const pool = require('../config/db');
const { verifyToken } = require('../middleware/authMiddleware');
const { rateLimit, chargeExport, sendError } = require('../services/securityLimits');
const { questionFilter } = require('../services/questionAccess');
const { verifyExportPassword, questionsCsv, MAX_EXPORT } = require('../services/questionExport');

const router = express.Router();

router.post('/export/questions', verifyToken, rateLimit('question-export-user', 3, 3600), rateLimit('question-export-ip', 10, 3600), async (req, res) => {
  try {
    const subjectId = Number(req.body.subject_id);
    if (!Number.isSafeInteger(subjectId) || subjectId <= 0) return res.status(400).json({ error: '请选择有效的科目' });
    const credential = await verifyExportPassword(pool, req.userId, req.body.password);
    if (!credential) return res.status(403).json({ error: '导出密码无效或已过期，请向管理员重新获取' });

    const filter = questionFilter(req);
    const where = `${filter.sql} AND q.subject_id = ?`;
    const params = [...filter.params, subjectId];
    const [[count]] = await pool.query(`SELECT COUNT(*) AS total FROM questions q ${where}`, params);
    if (Number(count.total) > MAX_EXPORT) return res.status(413).json({ error: `单次最多导出 ${MAX_EXPORT} 题，请按科目分别导出` });
    await chargeExport(req, Number(count.total));
    const [rows] = await pool.query(`SELECT q.id, q.type, q.difficulty, q.tags, q.content, q.options, q.answer, q.analysis,
      s.name AS subject_name FROM questions q JOIN subjects s ON s.id = q.subject_id WHERE ${where} ORDER BY q.id`, params);
    if (!rows.length) return res.status(404).json({ error: '当前科目没有可导出的题目' });

    await pool.query('INSERT INTO question_export_logs (user_id, subject_id, question_count, ip) VALUES (?, ?, ?, ?)',
      [req.userId, subjectId, rows.length, req.ip || '']);
    await pool.query('UPDATE question_export_credentials SET last_used_at = NOW() WHERE user_id = ?', [req.userId]);

    const filename = `shuxue-subject-${subjectId}-${new Date().toISOString().slice(0, 10)}.csv`;
    res.set('Cache-Control', 'private, no-store');
    res.set('Content-Type', 'text/csv; charset=utf-8');
    res.set('Content-Disposition', `attachment; filename="${filename}"; filename*=UTF-8''${encodeURIComponent(filename)}`);
    res.send(questionsCsv(rows));
  } catch (error) {
    sendError(res, error);
  }
});

module.exports = router;
