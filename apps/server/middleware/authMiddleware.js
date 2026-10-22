const jwt = require('jsonwebtoken');
const crypto = require('node:crypto');
const pool = require('../config/db');
const { jwtConfig } = require('../config/security');
const { access } = jwtConfig();

async function verifyToken(req, res, next) {
    const match = /^Bearer ([^\s]+)$/.exec(req.headers.authorization || '');
    if (!match) return res.status(401).json({ message: '请先登录', code: 'AUTH_REQUIRED' });
    let decoded;
    try {
        decoded = jwt.verify(match[1], access, { algorithms: ['HS256'] });
        if (!Number.isSafeInteger(Number(decoded.id)) || Number(decoded.id) <= 0 || !decoded.exp || decoded.type === 'refresh') throw new Error('Invalid token');
    } catch {
        return res.status(401).json({ message: '登录已过期，请重新登录', code: 'TOKEN_INVALID' });
    }
    try {
        const [rows] = await pool.query(
            'SELECT id, role, subscription_end_date, (subscription_end_date > NOW()) AS has_subscription FROM users WHERE id = ?',
            [decoded.id],
        );
        if (!rows.length) return res.status(401).json({ message: '账号不存在', code: 'TOKEN_INVALID' });
        req.account = { ...rows[0], has_subscription: Number(rows[0].has_subscription) === 1 };
        req.userId = rows[0].id;
        req.userRole = rows[0].role;
        next();
    } catch {
        res.status(503).json({ message: '暂时无法验证账号权限，请稍后再试' });
    }
}

function isAdmin(req, res, next) {
    const role = req.account?.role;
    if (role !== 'admin' && role !== 'demo') return res.status(403).json({ message: '需要管理员权限' });
    if (role === 'demo') {
        if (!['GET', 'OPTIONS', 'HEAD'].includes(req.method) || /^\/(questions|exams|reports|settings|users|cards|exports)(\/|$)/i.test(req.path)) {
            return res.status(403).json({ message: '演示账号无权执行此操作', code: 'DEMO_ACCESS_DENIED' });
        }
    }
    next();
}

function verifyBot(req, res, next) {
    const configured = process.env.BOT_API_SECRET;
    if (!configured || Buffer.byteLength(configured) < 32) return res.status(503).json({ error: '机器人接口尚未配置服务认证' });
    const supplied = req.headers['x-bot-api-key'];
    if (typeof supplied !== 'string' || !crypto.timingSafeEqual(
        crypto.createHash('sha256').update(supplied).digest(),
        crypto.createHash('sha256').update(configured).digest(),
    )) return res.status(401).json({ error: '机器人服务认证失败' });
    res.set('Cache-Control', 'private, no-store');
    next();
}

module.exports = { verifyToken, isAdmin, verifyBot };
