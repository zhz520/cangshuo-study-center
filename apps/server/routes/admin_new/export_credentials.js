const express = require('express');
const pool = require('../../config/db');
const { pageOptions } = require('../../services/questionAccess');
const { issueExportPassword } = require('../../services/questionExport');

const router = express.Router();

router.get('/', async (req, res) => {
  try {
    const { limit, offset } = pageOptions(req.query);
    const search = String(req.query.search || '').trim();
    const like = `%${search}%`;
    const where = search ? 'WHERE u.username LIKE ? OR u.nickname LIKE ? OR u.email LIKE ?' : '';
    const params = search ? [like, like, like] : [];
    const [rows] = await pool.query(`SELECT u.id, u.username, u.nickname, u.email, u.role, u.subscription_end_date,
      c.expires_at, c.updated_at, c.last_used_at, (c.expires_at > NOW()) AS export_active
      FROM users u LEFT JOIN question_export_credentials c ON c.user_id = u.id
      ${where} ORDER BY u.id DESC LIMIT ? OFFSET ?`, [...params, limit, offset]);
    const [[count]] = await pool.query(`SELECT COUNT(*) AS total FROM users u ${where}`, params);
    res.json({
      users: rows.map(row => ({ ...row, has_export_password: Number(row.export_active) === 1 })),
      total: count.total,
    });
  } catch (error) {
    res.status(error.code === 'ER_NO_SUCH_TABLE' ? 503 : 500).json({ error: error.code === 'ER_NO_SUCH_TABLE' ? '题库导出表尚未初始化' : '读取导出授权失败' });
  }
});

router.post('/:userId', async (req, res) => {
  try {
    const userId = Number(req.params.userId);
    if (!Number.isSafeInteger(userId) || userId <= 0) return res.status(400).json({ error: '用户编号无效' });
    const [users] = await pool.query('SELECT id, username, nickname FROM users WHERE id = ?', [userId]);
    if (!users.length) return res.status(404).json({ error: '用户不存在' });
    const credential = await issueExportPassword(pool, userId, req.userId, 30);
    res.status(201).json({ user: users[0], password: credential.password, expires_at: credential.expiresAt });
  } catch (error) {
    res.status(error.code === 'ER_NO_SUCH_TABLE' ? 503 : 500).json({ error: error.code === 'ER_NO_SUCH_TABLE' ? '题库导出表尚未初始化' : '生成导出密码失败' });
  }
});

router.delete('/:userId', async (req, res) => {
  try {
    const userId = Number(req.params.userId);
    if (!Number.isSafeInteger(userId) || userId <= 0) return res.status(400).json({ error: '用户编号无效' });
    await pool.query('DELETE FROM question_export_credentials WHERE user_id = ?', [userId]);
    res.json({ message: '已撤销导出授权' });
  } catch (error) {
    res.status(500).json({ error: '撤销导出授权失败' });
  }
});

module.exports = router;
