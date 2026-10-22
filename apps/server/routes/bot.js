const express = require("express");
const router = express.Router();
const pool = require("../config/db");
const { verifyBot } = require('../middleware/authMiddleware');
const { questionFilter } = require('../services/questionAccess');
const { rateLimit, chargeQuestions, sendError } = require('../services/securityLimits');
const { scoreAnswer } = require('../utils/scoreAnswer');
router.use(verifyBot, rateLimit('bot-ip', 180));

async function botAccount(req, openid) {
    if (typeof openid !== 'string' || openid.length < 1 || openid.length > 100) throw Object.assign(new Error('QQ user identifier is invalid'), { status: 400 });
    const [users] = await pool.query('SELECT id, role, (subscription_end_date > NOW()) AS has_subscription FROM users WHERE qq_openid = ?', [openid]);
    req.userId = users[0]?.id || 0;
    req.rateSubject = 'bot:' + require('node:crypto').createHash('sha256').update(openid).digest('hex');
    req.account = { role: 'user', has_subscription: Number(users[0]?.has_subscription) === 1 };
}

// Middleware: 初始化 Bot 表（如果没有）
async function initBotTables() {
    try {
        await pool.query(`
            CREATE TABLE IF NOT EXISTS bot_users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                qq_openid VARCHAR(100) NOT NULL UNIQUE COMMENT 'QQ用户的唯一标识',
                nickname VARCHAR(100) COMMENT 'QQ昵称',
                avatar_icon VARCHAR(255) COMMENT 'QQ头像',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次使用机器人的时间'
            );
        `);
        await pool.query(`
            CREATE TABLE IF NOT EXISTS bot_user_progress (
                id INT AUTO_INCREMENT PRIMARY KEY,
                bot_user_id INT NOT NULL COMMENT '关联 bot_users 的 id',
                question_id INT NOT NULL COMMENT '关联题目表 questions 的 id',
                is_correct TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0: 错误, 1: 正确',
                answer TEXT COMMENT '用户的回答',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_user_question (bot_user_id, question_id),
                FOREIGN KEY (bot_user_id) REFERENCES bot_users(id) ON DELETE CASCADE,
                FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
                UNIQUE KEY unique_user_question (bot_user_id, question_id)
            );
        `);
        console.log("Bot tables verified.");
    } catch (err) {
        console.error("Error creating bot tables:", err);
    }
}
initBotTables();

// ==========================================
// 1. 获取科目列表
// ==========================================
router.get("/subjects", async (req, res) => {
    try {
        const [rows] = await pool.query("SELECT id, name FROM subjects ORDER BY id");
        res.json(rows);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ==========================================
// 2. 为 QQ 用户随机抽题 (优先错题 -> 未做题 -> 随机题)
// ==========================================
router.get("/random-question", async (req, res) => {
    const { subject_id, qq_openid } = req.query;
    if (!subject_id || !qq_openid) {
        return res.status(400).json({ error: "Missing subject_id or qq_openid" });
    }

    try {
        await botAccount(req, qq_openid);
        const filter = questionFilter(req);
        const [rows] = await pool.query(`SELECT q.id, q.type, q.content, q.options, q.answer, q.analysis
            FROM questions q
            LEFT JOIN bot_users bu ON bu.qq_openid = ?
            LEFT JOIN bot_user_progress bup ON bup.bot_user_id = bu.id AND bup.question_id = q.id
            WHERE q.subject_id = ? AND ${filter.sql}
            ORDER BY CASE WHEN bup.is_correct = 0 THEN 0 WHEN bup.question_id IS NULL THEN 1 ELSE 2 END, RAND() LIMIT 1`,
            [qq_openid, subject_id, ...filter.params]);
        if (!rows.length) return res.status(404).json({ error: 'No accessible questions' });
        await chargeQuestions(req, 1);
        res.json(rows[0]);
    } catch (error) { sendError(res, error); }
});

// ==========================================
// 3. 记录做题进度 & 更新用户信息
// ==========================================
router.post("/progress", async (req, res) => {
    const { qq_openid, nickname, avatar_icon, question_id, is_correct, answer } = req.body;
    if (!qq_openid || !question_id) {
        return res.status(400).json({ error: "Missing qq_openid or question_id" });
    }

    try {
        await botAccount(req, qq_openid);
        const filter = questionFilter(req);
        const [questions] = await pool.query(`SELECT q.id, q.type, q.content, q.options, q.answer, q.analysis FROM questions q WHERE q.id = ? AND ${filter.sql}`, [question_id, ...filter.params]);
        if (!questions.length) return res.status(403).json({ error: 'Question not accessible' });
        const verifiedCorrect = scoreAnswer(questions[0], answer);
        // 更新或创建用户，保存昵称和头像
        await pool.query(`
            INSERT INTO bot_users (qq_openid, nickname, avatar_icon)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar_icon = VALUES(avatar_icon)
        `, [qq_openid, nickname, avatar_icon]);

        // 获取用户 ID
        const [userRows] = await pool.query("SELECT id FROM bot_users WHERE qq_openid = ?", [qq_openid]);
        const botUserId = userRows[0].id;

        // 记录进度
        await pool.query(`
            INSERT INTO bot_user_progress (bot_user_id, question_id, is_correct, answer) 
            VALUES (?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE is_correct = VALUES(is_correct), answer = VALUES(answer)
        `, [botUserId, question_id, verifiedCorrect, answer]);

        res.json({ message: "Progress saved" });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ==========================================
// 4. 获取总体统计/成绩单
// ==========================================
router.get("/stats", async (req, res) => {
    const { qq_openid } = req.query;
    if (!qq_openid) return res.status(400).json({ error: "Missing qq_openid" });

    try {
        const [userRows] = await pool.query("SELECT id, nickname FROM bot_users WHERE qq_openid = ?", [qq_openid]);
        if (userRows.length === 0) {
            return res.json({ total: 0, correct: 0, accuracy: 0, nickname: "未命名用户" });
        }
        
        const botUserId = userRows[0].id;
        const nickname = userRows[0].nickname;

        const [stats] = await pool.query(`
            SELECT COUNT(*) as total, SUM(is_correct) as correct
            FROM bot_user_progress
            WHERE bot_user_id = ?
        `, [botUserId]);

        const total = stats[0].total || 0;
        const correct = stats[0].correct || 0;
        const accuracy = total > 0 ? Math.round((correct / total) * 100) : 0;

        res.json({ nickname, total, correct, accuracy });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ==========================================
// 5. 排行榜 - Top 10
// ==========================================
router.get("/leaderboard", async (req, res) => {
    try {
        const [rows] = await pool.query(`
            SELECT 
                bu.nickname,
                bu.qq_openid,
                COUNT(*) as total,
                SUM(bup.is_correct) as correct,
                ROUND(SUM(bup.is_correct) / COUNT(*) * 100) as accuracy
            FROM bot_user_progress bup
            JOIN bot_users bu ON bup.bot_user_id = bu.id
            GROUP BY bup.bot_user_id
            ORDER BY total DESC, accuracy DESC
            LIMIT 10
        `);
        res.json(rows);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
