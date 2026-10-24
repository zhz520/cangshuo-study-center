// Shared atomic budgets survive reconnects and multiple Node workers.
const pool = require('../config/db');
let nextCleanupAt = 0;

function cleanExpiredBudgets() {
    const now = Date.now();
    if (now < nextCleanupAt) return;
    nextCleanupAt = now + 60000;
    // Bounded housekeeping does not participate in the authorization decision.
    pool.query('DELETE FROM security_rate_limits WHERE expires_at < NOW() LIMIT 1000').catch(() => {});
}

async function reserve(scope, subject, amount, limit, seconds, db = pool, now = Date.now()) {
    const window = Math.floor(now / (seconds * 1000));
    const key = require('node:crypto').createHash('sha256').update(`${scope}:${subject}:${window}`).digest('hex');
    await db.query('INSERT IGNORE INTO security_rate_limits (bucket_key, used, expires_at) VALUES (?, 0, ?)',
        [key, new Date((window + 2) * seconds * 1000)]);
    const [result] = await db.query(
        'UPDATE security_rate_limits SET used = used + ? WHERE bucket_key = ? AND used + ? <= ?',
        [amount, key, amount, limit],
    );
    if (result.affectedRows !== 1) {
        const error = new Error('操作过于频繁，请稍后重试');
        error.status = 429; error.code = 'RATE_LIMITED';
        error.retryAfter = Math.max(1, Math.ceil(((window + 1) * seconds * 1000 - now) / 1000));
        throw error;
    }
    return { key };
}

function rateLimit(scope, limit, seconds = 60) {
    return async (req, res, next) => {
        try {
            await reserve(scope, scope.endsWith('-ip') ? req.ip : (req.userId || req.ip), 1, limit, seconds);
            cleanExpiredBudgets();
            next();
        } catch (error) { sendError(res, error); }
    };
}

async function chargeQuestions(req, count, db = pool, now = Date.now()) {
    if (!count || req.account?.role === 'admin') return;
    await reserve('question-minute-v2', req.rateSubject || req.userId, count, 100, 60, db, now);
    await reserve('question-hour-v2', req.rateSubject || req.userId, count, 300, 3600, db, now);
    await reserve('question-day-v2', req.rateSubject || req.userId, count, 1500, 86400, db, now);
    await reserve('question-ip-hour-v2', req.ip, count, 3000, 3600, db, now);
}

// Question exports are authorized by an admin-issued password and limited separately
// from reading, so a permitted export is not blocked by the 5000/day reading budget.
async function chargeExport(req, count, db = pool, now = Date.now()) {
    if (!count) return;
    await reserve('question-export-day', req.userId, count, 20000, 86400, db, now);
}

function sendError(res, error) {
    if (error.retryAfter) res.set('Retry-After', String(error.retryAfter));
    return res.status(error.status || 503).json({
        error: error.status ? error.message : '安全校验暂不可用，请稍后重试',
        code: error.code || 'SECURITY_CHECK_UNAVAILABLE',
    });
}

async function reserveAi(req) {
    const key = req.account.has_subscription || req.account.role === 'admin' ? 'ai_day_limit_plus' : 'ai_day_limit_free';
    const [rows] = await pool.query('SELECT setting_value FROM settings WHERE setting_key = ?', [key]);
    const configured = Number(rows[0]?.setting_value ?? (key.endsWith('plus') ? 50 : 1));
    const limit = Number.isSafeInteger(configured) && configured >= 0 ? configured : 0;
    let reservation;
    try {
        reservation = await reserve('ai-daily', req.userId, 1, limit, 86400);
    } catch (error) {
        if (error.status === 429) error.message = '今日 AI 次数已用完，请明天再试或升级会员';
        throw error;
    }
    const [usage] = await pool.query('SELECT used FROM security_rate_limits WHERE bucket_key = ?', [reservation.key]);
    return { limit, remaining: Math.max(0, limit - Number(usage[0].used)) };
}

module.exports = { reserve, rateLimit, chargeQuestions, chargeExport, sendError, reserveAi };
