const express = require("express");
const router = express.Router();
const pool = require("../config/db");
const jwt = require("jsonwebtoken");
const axios = require("axios");
const SECRET_KEY = require("../config/security").jwtConfig().access;

const { verifyToken } = require("../middleware/authMiddleware");
const { rateLimit, reserveAi, sendError } = require("../services/securityLimits");
const { assertQuestion } = require("../services/questionAccess");
const { sendPushNotification } = require("../services/notificationService");
const emailService = require("../services/emailService");
const nodemailer = require("nodemailer"); // Keep for existing direct usage if any, but will refactor

// Ensure article_comments table exists
async function initCommentsTable() {
  try {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS article_comments (
        id INT AUTO_INCREMENT PRIMARY KEY,
        article_id INT NOT NULL,
        user_id INT NOT NULL,
        content TEXT NOT NULL,
        status ENUM('pending', 'approved', 'rejected') DEFAULT 'pending',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_article_id (article_id),
        FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      )
    `);
  } catch (err) {
    console.error("Error creating article_comments table:", err);
  }
}
initCommentsTable();

// Get Majors with Access Control
router.get("/majors", async (req, res) => {
  let userId = null;
  let userRole = null;
  const authHeader = req.headers["authorization"];
  if (authHeader && authHeader.startsWith("Bearer ")) {
    const token = authHeader.split(" ")[1];
    try {
      const decoded = jwt.verify(token, SECRET_KEY);
      userId = decoded.id;
      userRole = decoded.role;
    } catch (err) {}
  }

  try {
    let query = "SELECT * FROM majors";
    let params = [];

    // If logged in and not an admin/demo, check access control
    if (userId && userRole !== 'admin' && userRole !== 'demo') {
        const [accessRows] = await pool.query(
            "SELECT major_id FROM major_access_control WHERE user_id = ?",
            [userId]
        );

        if (accessRows.length > 0) {
            const allowedIds = accessRows.map(r => r.major_id);
            query += " WHERE id IN (?)";
            params.push(allowedIds);
        }
    }

    query += " ORDER BY id";
    const [rows] = await pool.query(query, params);
    res.json(rows);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Get Subjects by Major
router.get("/subjects", async (req, res) => {
  const { major_id, filter_empty = "true" } = req.query;

  // Try to get userId/role if token is present (optional authentication)
  let userId = null;
  let userRole = null;
  const authHeader = req.headers["authorization"];
  if (authHeader && authHeader.startsWith("Bearer ")) {
    const token = authHeader.split(" ")[1];
    try {
      const decoded = jwt.verify(token, SECRET_KEY);
      userId = decoded.id;
      userRole = decoded.role;
    } catch (err) {
      // Ignore invalid token, just treat as guest
    }
  }

  try {
    // If logged in and not an admin/demo, check access control
    if (userId && major_id && userRole !== 'admin' && userRole !== 'demo') {
        const [accessRows] = await pool.query(
            "SELECT 1 FROM major_access_control WHERE user_id = ? AND major_id = ?",
            [userId, major_id]
        );
        if (accessRows.length === 0) {
            // Check if user has ANY specific permissions. If they have none, they have access to all.
            const [anyRows] = await pool.query("SELECT 1 FROM major_access_control WHERE user_id = ?", [userId]);
            if (anyRows.length > 0) {
                return res.status(403).json({ error: "您无权访问该专业分类的题库" });
            }
        }
    }

    let query = `
      SELECT s.*,
        (SELECT COUNT(*) FROM questions q WHERE q.subject_id = s.id AND q.is_active = 1) as question_count,
        (SELECT SUM(CASE
            WHEN q.type = 'single' THEN 3
            WHEN q.type = 'multiple' THEN 4
            WHEN q.type = 'judge' THEN 2
            ELSE 0 END)
         FROM questions q WHERE q.subject_id = s.id AND q.is_active = 1) as max_score,
        (SELECT SUM(CASE
            WHEN q.type = 'single' THEN 3
            WHEN q.type = 'multiple' THEN 4
            WHEN q.type = 'judge' THEN 2
            ELSE 0 END)
         FROM user_progress up
         JOIN questions q ON up.question_id = q.id
         WHERE up.user_id = ? AND q.subject_id = s.id AND up.is_correct = 1 AND up.is_deleted = FALSE AND q.is_active = 1) as current_score
      FROM subjects s
    `;
    const params = [userId];
    const conditions = [];

    if (major_id) {
      conditions.push("s.major_id = ?");
      params.push(major_id);
    }

    if (filter_empty === "true") {
      conditions.push(
        "(SELECT COUNT(*) FROM questions q WHERE q.subject_id = s.id) > 0",
      );
    }

    if (conditions.length > 0) {
      query += " WHERE " + conditions.join(" AND ");
    }

    query += " ORDER BY s.id";

    console.log(`[Subjects] Executing query for userId: ${userId}, majorId: ${major_id}`);
    const [rows] = await pool.query(query, params);
    console.log(`[Subjects] Query completed, found ${rows.length} rows`);

    // Ensure scores are numbers, default to 0
    const results = rows.map((row) => ({
      ...row,
      max_score: parseInt(row.max_score) || 0,
      current_score: parseInt(row.current_score) || 0,
    }));

    res.json(results);
  } catch (error) {
    console.error("[Subjects Error]:", error);
    res.status(500).json({ error: error.message });
  }
});

// All learning content routes share server-side authorization and paging.
router.use(require("./questions"));

// Redeem Subscription Card
router.post("/subscribe", verifyToken, rateLimit("subscription-redeem", 5), async (req, res) => {
  const code = typeof req.body.code === "string" ? req.body.code.trim().toUpperCase() : "";
  if (!code || code.length > 64) return res.status(400).json({ error: "请输入兑换码" });

  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    // Check if card is valid and unused
    const [cardRows] = await connection.query(
      "SELECT * FROM activation_cards WHERE code = ? FOR UPDATE",
      [code],
    );
    if (cardRows.length === 0) {
      await connection.rollback();
      return res.status(400).json({ error: "无效的兑换码" });
    }

    const card = cardRows[0];
    if (!Number.isSafeInteger(Number(card.duration_days)) || Number(card.duration_days) <= 0 || Number(card.duration_days) > 36500) {
      await connection.rollback();
      return res.status(400).json({ error: "兑换码时长无效，请联系管理员" });
    }
    if (card.is_used) {
      await connection.rollback();
      return res.status(400).json({ error: "该兑换码已被使用" });
    }

    // Get user's current subscription end date
    const [userRows] = await connection.query(
      "SELECT subscription_end_date FROM users WHERE id = ? FOR UPDATE",
      [req.userId],
    );
    const currentEndDate = userRows[0]?.subscription_end_date;
    const now = new Date();

    let newEndDate = new Date();
    if (currentEndDate && new Date(currentEndDate) > now) {
      // Add to existing end date
      newEndDate = new Date(currentEndDate);
    }
    // Add duration days
    newEndDate.setDate(newEndDate.getDate() + Number(card.duration_days));

    // Update card status
    await connection.query(
      "UPDATE activation_cards SET is_used = TRUE, used_by = ?, used_at = ? WHERE id = ?",
      [req.userId, now, card.id],
    );

    // Update user's subscription
    await connection.query(
      "UPDATE users SET subscription_end_date = ? WHERE id = ?",
      [`${newEndDate.getFullYear()}-${String(newEndDate.getMonth() + 1).padStart(2, "0")}-${String(newEndDate.getDate()).padStart(2, "0")} ${String(newEndDate.getHours()).padStart(2, "0")}:${String(newEndDate.getMinutes()).padStart(2, "0")}:${String(newEndDate.getSeconds()).padStart(2, "0")}`, req.userId],
    );

    await connection.commit();
    res.json({ message: "兑换成功", subscription_end_date: newEndDate });
  } catch (error) {
    await connection.rollback();
    console.error("Subscription error:", error);
    res.status(500).json({ error: "兑换失败，请稍后再试" });
  } finally {
    connection.release();
  }
});

// Get User Stats (active days, total questions, accuracy)
router.get("/user-stats", verifyToken, async (req, res) => {
  try {
    const [rows] = await pool.query(
      `SELECT
        COUNT(*) as total,
        SUM(is_correct) as correct,
        COUNT(DISTINCT DATE(updated_at)) as active_days
      FROM user_progress up
      JOIN questions q ON up.question_id = q.id
      WHERE up.user_id = ? AND q.is_active = 1`,
      [req.userId],
    );
    const stats = rows[0] || { total: 0, correct: 0, active_days: 0 };
    const accuracy =
      stats.total > 0 ? Math.round((stats.correct / stats.total) * 100) : 0;
    res.json({
      active_days: stats.active_days || 0,
      total_questions: stats.total || 0,
      accuracy: accuracy,
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Get Leaderboard (by Total Questions or Accuracy)
router.get("/leaderboard", verifyToken, async (req, res) => {
  const { type, subject_id, page = 1 } = req.query; // 'total' or 'accuracy'
  const limit = parseInt(req.query.limit) || 50;
  const offset = (parseInt(page) - 1) * limit;

  try {
    let query = "";
    let params = [];

    // Build subject filter and question join clause if subject_id is specified
    let subjectJoin = "JOIN questions q ON up.question_id = q.id";
    let subjectFilter = "AND q.is_active = 1";
    if (subject_id && subject_id !== "all") {
      subjectFilter += " AND q.subject_id = ?";
      params.push(subject_id);
    }

    if (type === "accuracy") {
      // For accuracy, we might want to lower the minimum requirement if filtering by subject
      const minQuestions = subject_id && subject_id !== "all" ? 5 : 20;
      query = `
        SELECT
          up.user_id,
          MAX(u.username) as username,
          MAX(u.nickname) as nickname,
          MAX(u.avatar_icon) as avatar_icon,
          COUNT(*) as total_questions,
          SUM(up.is_correct) as correct_questions,
          MAX(u.subscription_end_date) as subscription_end_date,
          ROUND((SUM(up.is_correct) / COUNT(*)) * 100) as score
        FROM user_progress up
        JOIN users u ON up.user_id = u.id
        ${subjectJoin}
        WHERE 1=1 ${subjectFilter}
        GROUP BY up.user_id
        HAVING total_questions >= ${minQuestions}
        ORDER BY score DESC, total_questions DESC
        LIMIT ? OFFSET ?
      `;
    } else if (type === "score") {
      // New: Total Points Leaderboard
      query = `
        SELECT
          up.user_id,
          MAX(u.username) as username,
          MAX(u.nickname) as nickname,
          MAX(u.avatar_icon) as avatar_icon,
          MAX(u.subscription_end_date) as subscription_end_date,
          SUM(
            CASE
              WHEN q.type = 'single' AND up.is_correct = 1 THEN 3
              WHEN q.type = 'multiple' AND up.is_correct = 1 THEN 4
              WHEN q.type = 'judge' AND up.is_correct = 1 THEN 2
              ELSE 0
            END
          ) as score
        FROM user_progress up
        JOIN users u ON up.user_id = u.id
        JOIN questions q ON up.question_id = q.id
        WHERE q.is_active = 1 ${subjectFilter.replace("AND q.is_active = 1", "")}
        GROUP BY up.user_id
        ORDER BY score DESC
        LIMIT ? OFFSET ?
      `;
    } else {
      // Default: Total Questions Leaderboard
      query = `
        SELECT
          up.user_id,
          MAX(u.username) as username,
          MAX(u.nickname) as nickname,
          MAX(u.avatar_icon) as avatar_icon,
          MAX(u.subscription_end_date) as subscription_end_date,
          COUNT(*) as score
        FROM user_progress up
        JOIN users u ON up.user_id = u.id
        ${subjectJoin}
        WHERE 1=1 ${subjectFilter}
        GROUP BY up.user_id
        ORDER BY score DESC
        LIMIT ? OFFSET ?
      `;
    }

    // Add pagination params
    params.push(limit, offset);

    const [rows] = await pool.query(query, params);

    // Format response to ensure safe naming and structure for Android Model
    const leaderboard = rows.map((row, index) => ({
      rank: offset + index + 1,
      user_id: row.user_id,
      username: row.username,
      nickname: row.nickname,
      avatar_icon: row.avatar_icon,
      subscription_end_date: row.subscription_end_date,
      score: parseInt(row.score) || 0,
    }));

    res.json(leaderboard);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Get Detailed Learning Analytics
router.get("/detailed-stats", verifyToken, async (req, res) => {
  try {
    // 1. Per-subject breakdown
    const [subjectRows] = await pool.query(
      `SELECT s.id, s.name as subject_name,
         COUNT(up.id) as total,
         SUM(up.is_correct) as correct
       FROM user_progress up
       JOIN questions q ON up.question_id = q.id
       JOIN subjects s ON q.subject_id = s.id
       WHERE up.user_id = ? AND q.is_active = 1
       GROUP BY s.id, s.name
       ORDER BY total DESC
       LIMIT 10`,
      [req.userId],
    );

    // 2. Question-type breakdown
    const [typeRows] = await pool.query(
      `SELECT q.type,
         COUNT(up.id) as total,
         SUM(up.is_correct) as correct
       FROM user_progress up
       JOIN questions q ON up.question_id = q.id
       WHERE up.user_id = ? AND q.is_active = 1
       GROUP BY q.type`,
      [req.userId],
    );

    // 3. Last 7 days activity heatmap
    const [weekRows] = await pool.query(
      `SELECT DATE(updated_at) as day, COUNT(*) as count
       FROM user_progress up
       JOIN questions q ON up.question_id = q.id
       WHERE up.user_id = ? AND up.updated_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) AND q.is_active = 1
       GROUP BY DATE(updated_at)
       ORDER BY day ASC`,
      [req.userId],
    );

    // 4. Streak (consecutive active days ending today)
    const [streakRows] = await pool.query(
      `SELECT DISTINCT DATE(updated_at) as day
       FROM user_progress up
       JOIN questions q ON up.question_id = q.id
       WHERE up.user_id = ? AND q.is_active = 1
       ORDER BY day DESC
       LIMIT 60`,
      [req.userId],
    );
    let streak = 0;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    for (let i = 0; i < streakRows.length; i++) {
      const d = new Date(streakRows[i].day);
      d.setHours(0, 0, 0, 0);
      const expected = new Date(today);
      expected.setDate(today.getDate() - i);
      if (d.getTime() === expected.getTime()) {
        streak++;
      } else {
        break;
      }
    }

    const subjectStats = subjectRows.map((r) => ({
      subject_id: r.id,
      subject_name: r.subject_name,
      total: Number(r.total),
      correct: Number(r.correct),
      accuracy: r.total > 0 ? Math.round((r.correct / r.total) * 100) : 0,
    }));

    const typeStats = typeRows.map((r) => ({
      type: r.type,
      total: Number(r.total),
      correct: Number(r.correct),
      accuracy: r.total > 0 ? Math.round((r.correct / r.total) * 100) : 0,
    }));

    const weekHeatmap = weekRows.map((r) => ({
      day: r.day,
      count: Number(r.count),
    }));

    res.json({ subjectStats, typeStats, weekHeatmap, streak });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Get User Progress

// Get ALL Mistakes (Questions)

// Update Avatar
router.post("/update-avatar", verifyToken, async (req, res) => {
  const { avatar_icon } = req.body;
  if (!avatar_icon)
    return res.status(400).json({ message: "Avatar icon is required" });

  try {
    // 1. Ensure the avatar_icon column exists
    try {
      await pool.query(
        "ALTER TABLE users ADD COLUMN avatar_icon VARCHAR(255) DEFAULT 'https://api.dicebear.com/9.x/fun-emoji/png?seed=Felix&size=120'",
      );
    } catch (e) {
      // Ignore error if column already exists (ER_DUP_FIELDNAME)
    }

    // 2. Update the user's avatar
    await pool.query("UPDATE users SET avatar_icon = ? WHERE id = ?", [
      avatar_icon,
      req.userId,
    ]);
    res.json({ message: "Avatar updated successfully", avatar_icon });
  } catch (error) {
    console.error("Update avatar error:", error.message);
    res.status(500).json({ error: "Failed to update avatar" });
  }
});

// Update Single Progress (By DB ID)

// Batch Sync Progress (By Subject ID + Original ID)

// Reset Progress (Delete by Subject)
router.delete("/progress", verifyToken, async (req, res) => {
  const { subject_id } = req.query;
  if (!subject_id)
    return res.status(400).json({ message: "Subject ID is required" });

  try {
    if (subject_id === "all") {
      await pool.query("UPDATE user_progress SET is_deleted = TRUE WHERE user_id = ?", [
        req.userId,
      ]);
    } else {
      // Delete progress for questions in the specific subject
      await pool.query(
        `UPDATE user_progress SET is_deleted = TRUE
                 WHERE user_id = ?
                 AND question_id IN (SELECT id FROM questions WHERE subject_id = ? AND is_active = 1)`,
        [req.userId, subject_id],
      );
    }

    res.json({ message: "Progress reset successful" });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Get Random Daily Content (One for each type)
router.get("/daily-content/random", async (req, res) => {
  try {
    const types = ["quote", "proverb", "poetry"];
    const result = {};

    for (const type of types) {
      // Efficient random selection for small datasets
      const [rows] = await pool.query(
        "SELECT content, translation as source FROM daily_contents WHERE type = ? ORDER BY RAND() LIMIT 1",
        [type],
      );
      if (rows.length > 0) {
        if (type === "proverb") {
          // Adapt to frontend structure { en, zh }
          result[type] = { en: rows[0].content, zh: rows[0].source };
        } else if (type === "poetry") {
          // Adapt to frontend structure { line, src }
          result[type] = { line: rows[0].content, src: rows[0].source };
        } else {
          // Quote just string
          result[type] = rows[0].content;
        }
      }
    }
    res.json(result);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Get Intelligent Recommendations

// Get Weakness Analysis
router.get("/analysis", verifyToken, async (req, res) => {
  const { subject_id } = req.query;

  try {
    let queryParams = [req.userId];

    // Base Query: Get progress + question metadata
    let query = `
            SELECT up.is_correct, q.difficulty, q.tags, q.type
            FROM user_progress up
            JOIN questions q ON up.question_id = q.id
            WHERE up.user_id = ? AND q.is_active = 1
        `;

    // Optional Subject Filter
    if (subject_id) {
      query += " AND q.subject_id = ?";
      queryParams.push(subject_id);
    }

    const [rows] = await pool.query(query, queryParams);

    if (rows.length === 0) {
      return res.json({
        totalQuestions: 0,
        overallAccuracy: 0,
        radarData: [],
        weakTags: [],
      });
    }

    // Aggregation Logic
    const tagStats = {}; // { tagName: { total: 0, correct: 0 } }
    let totalCorrect = 0;

    rows.forEach((row) => {
      if (row.is_correct) totalCorrect++;

      // Parse Tags
      let tags = [];
      if (row.tags) {
        if (typeof row.tags === "string") {
          // Try JSON parse, else split
          try {
            tags = JSON.parse(row.tags);
          } catch (e) {
            tags = row.tags.split(/[,，]/);
          }
        } else if (Array.isArray(row.tags)) {
          tags = row.tags;
        }
      }

      // If no tags, maybe categorize by Type? Or just skip
      if (tags.length === 0) tags = ["未分类"];

      tags.forEach((tag) => {
        const t = tag.trim();
        if (!t) return;
        if (!tagStats[t]) tagStats[t] = { total: 0, correct: 0 };
        tagStats[t].total++;
        if (row.is_correct) tagStats[t].correct++;
      });
    });

    // Format for ECharts Radar
    // We want maybe top 6-8 tags by frequency (most practiced) to show on radar?
    // Or specific categories. For now, let's take top 6 most frequent tags.
    const sortedTags = Object.keys(tagStats).sort(
      (a, b) => tagStats[b].total - tagStats[a].total,
    );
    const topTags = sortedTags.slice(0, 6);

    const radarIndicator = topTags.map((tag) => ({ name: tag, max: 100 }));
    const radarValues = topTags.map((tag) => {
      const stat = tagStats[tag];
      return Math.round((stat.correct / stat.total) * 100);
    });

    // Weakest Tags (Lowest accuracy, min 3 attempts to be significant)
    const weakTags = Object.entries(tagStats)
      .filter(([_, stat]) => stat.total >= 3)
      .map(([tag, stat]) => ({
        tag,
        accuracy: Math.round((stat.correct / stat.total) * 100),
        total: stat.total,
      }))
      .sort((a, b) => a.accuracy - b.accuracy)
      .slice(0, 10);

    // --- AI Diagnosis Implementation ---
    const totalQuestions = rows.length;
    const overallAccuracy =
      totalQuestions > 0
        ? Math.round((totalCorrect / totalQuestions) * 100)
        : 0;
    let aiDiagnosis = null;

    if (totalQuestions > 5) {
      // Only analyze if user has enough data
      try {
        // Calculate true strong tags by accuracy (min 3 attempts)
        const strongTags = Object.entries(tagStats)
          .filter(([_, stat]) => stat.total >= 3)
          .map(([tag, stat]) => ({
            tag,
            accuracy: Math.round((stat.correct / stat.total) * 100),
          }))
          .sort((a, b) => b.accuracy - a.accuracy)
          .slice(0, 3)
          .map((t) => `${t.tag}(${t.accuracy}%)`)
          .join(", ") || "暂无";

        const prompt = `以下是一位学生的答题数据，请给出简短、个性化的学习诊断（400字以内）。

【数据】
- 总刷题量：${totalQuestions} 道
- 综合正确率：${overallAccuracy}%
- 薄弱知识点：${weakTags.map((w) => `${w.tag}(${w.accuracy}%)`).join(", ") || "暂无明显薄弱点"}
- 优势知识点：${strongTags}

请分两点回答：
1. 现状点评（结合数据判断学生当前水平，指出关键问题）
2. 提分建议（针对薄弱点给出2-3条具体可执行的学习策略）

语气亲切鼓励但专业。纯文本分点输出，禁止使用 Markdown 标题或加粗语法。`;

        const aiRes = await axios.post(
          "https://api.deepseek.com/v1/chat/completions",
          {
            model: "deepseek-chat",
            messages: [
              { role: "system", content: "你是烁学平台的 AI 学情诊断师。用简洁中文分析学生数据，给出个性化诊断。纯文本分点输出，禁止使用 Markdown 标题或加粗。" },
              { role: "user", content: prompt },
            ],
            temperature: 0.7,
          },
          {
            headers: {
              Authorization: `Bearer ${process.env.DEEPSEEK_API_KEY}`,
              "Content-Type": "application/json",
            },
            timeout: 10000, // 10s timeout to not block UI too long
          },
        );
        aiDiagnosis = aiRes.data.choices[0]?.message?.content || null;
      } catch (aiError) {
        console.error("AI Analysis failed:", aiError.message);
        // Fallback or just null
      }
    }

    res.json({
      totalQuestions,
      overallAccuracy,
      radar: {
        indicator: radarIndicator,
        value: radarValues,
      },
      weakTags,
      aiDiagnosis,
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Get Intelligent Recommendations

// AI Tutor Explain
router.post("/ai-explain", verifyToken, rateLimit("ai-request", 10), async (req, res) => {
  const userId = req.userId;
  const { question, options, userAnswer, correctAnswer, type } = req.body;

  try {
    if (!process.env.DEEPSEEK_API_KEY) return res.status(503).json({ error: "AI 服务尚未配置" });
    const quota = await reserveAi(req);
    const dailyLimit = quota.limit;
    const currentUsage = dailyLimit - quota.remaining - 1;
    const today = new Date().toISOString().split("T")[0];

    // Construct Options String
    let optionsStr = "";
    if (options) {
      let parsedOptions = options;
      if (typeof options === "string" && options.trim().startsWith("{")) {
        try {
          parsedOptions = JSON.parse(options);
        } catch (e) {
          optionsStr = options;
        }
      }

      if (optionsStr === "" && parsedOptions) {
        if (Array.isArray(parsedOptions)) {
          optionsStr = parsedOptions
            .map((opt, idx) => {
              const char = String.fromCharCode(65 + idx);
              const val = typeof opt === "object" ? (opt.content || opt.text || JSON.stringify(opt)) : opt;
              return `${char}. ${val}`;
            })
            .join("\n");
        } else if (typeof parsedOptions === "object") {
          optionsStr = Object.entries(parsedOptions)
            .map(([k, v]) => `${k}. ${v}`)
            .join("\n");
        } else {
          optionsStr = parsedOptions.toString();
        }
      }
    }

    // Type name mapping and type-specific hints
    const typeNameMap = {
      single: "单选题",
      multiple: "多选题",
      judge: "判断题",
      fill: "填空题",
      sort: "排序题",
      material: "材料题",
    };
    const typeName = typeNameMap[type] || "题目";

    // Determine if student answered correctly
    const isCorrect = String(userAnswer).trim() === String(correctAnswer).trim();
    const answerFeedback = isCorrect
      ? "学生已答对此题，请进行知识点巩固讲解。"
      : `学生答错了（选了 ${userAnswer}，正确答案是 ${correctAnswer}），请先分析错因。`;

    // Type-specific guidance
    let typeHint = "";
    if (type === "multiple") {
      typeHint = "这是多选题，请逐一分析每个选项为什么选或不选，注意易混淆的干扰项。";
    } else if (type === "judge") {
      typeHint = "这是判断题，请指出题目中的关键判断点，说明为什么该判断正确或错误。";
    } else if (type === "fill") {
      typeHint = "这是填空题，请解释答案的来源和推导过程。";
    } else if (type === "sort") {
      typeHint = "这是排序题，请解释正确的排列顺序及其逻辑依据。";
    }

    const prompt = `请讲解以下${typeName}。

【题目】
${question}
${optionsStr ? `\n【选项】\n${optionsStr}` : ""}

【题库给出的答案】${correctAnswer}
【学生作答】${userAnswer}
【作答情况】${answerFeedback}

请按以下结构讲解：
0. 首先，请你自己独立审题并判断正确答案。如果你认为题库给出的答案有误，请明确指出并给出你认为的正确答案及理由
1. ${isCorrect ? "肯定学生的正确选择，简要说明答对的关键" : "指出学生的错误所在，分析为什么选错"}
2. 解释正确答案的依据和核心知识点
3. ${typeHint || "如有易混淆点，做对比辨析"}
4. 给出一个记忆口诀、类比或总结帮助巩固

如有代码请用 Markdown 代码块包裹。语气亲切自然。`;

    const aiRes = await axios.post(
      "https://api.deepseek.com/v1/chat/completions",
      {
        model: "deepseek-reasoner",
        messages: [
          { role: "user", content: `【系统指令】你是烁学平台的 AI 助教，擅长用通俗易懂的方式讲解各类题目。你必须先独立审题验证答案的正确性，如果题库答案有误要主动指出。支持 Markdown（代码块、列表）。回复控制在 500 字以内，结构清晰。\n\n` + prompt },
        ],
        stream: true,
      },
      {
        headers: {
          Authorization: `Bearer ${process.env.DEEPSEEK_API_KEY}`,
          "Content-Type": "application/json",
        },
        responseType: "stream",
      },
    );

    res.setHeader("Content-Type", "text/event-stream");
    res.setHeader("Cache-Control", "no-cache");
    res.setHeader("Connection", "keep-alive");

    // deepseek-reasoner outputs both reasoning_content and content in SSE chunks.
    // We only forward 'content' delta to the client, filtering out the internal CoT.
    let sseBuffer = "";
    aiRes.data.on("data", (chunk) => {
      sseBuffer += chunk.toString();
      const lines = sseBuffer.split("\n");
      // Keep the last potentially incomplete line in the buffer
      sseBuffer = lines.pop() || "";

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || !trimmed.startsWith("data: ")) continue;
        const payload = trimmed.slice(6);
        if (payload === "[DONE]") {
          res.write("data: [DONE]\n\n");
          continue;
        }
        try {
          const parsed = JSON.parse(payload);
          const delta = parsed.choices?.[0]?.delta;
          if (delta && delta.content) {
            // Re-emit as standard SSE with only content
            const forwarded = {
              ...parsed,
              choices: parsed.choices.map((c) => ({
                ...c,
                delta: { content: c.delta.content },
              })),
            };
            res.write(`data: ${JSON.stringify(forwarded)}\n\n`);
          }
          // Skip chunks that only contain reasoning_content
        } catch (e) {
          // If parse fails, skip
        }
      }
    });

    aiRes.data.on("end", async () => {
      try {
        await pool.query(
          "INSERT INTO user_daily_usage (user_id, date, count) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE count = count + 1",
          [userId, today],
        );
        res.write(`data: ${JSON.stringify({ remaining: dailyLimit - (currentUsage + 1), done: true })}\n\n`);
      } catch (e) {
        console.error("Usage recording error (stream):", e);
      } finally {
        res.end();
      }
    });

    aiRes.data.on("error", (err) => {
      console.error("AI Stream Error:", err);
      res.end();
    });
  } catch (error) {
    if (error.status && !res.headersSent) return sendError(res, error);
    console.error("AI Explain Error:", error.message);
    if (!res.headersSent) {
      res.status(500).json({ error: "获取AI解析失败" });
    } else {
      res.end();
    }
  }
});

// AI Learning Analysis (separate from question explanation)
router.post("/ai-analysis", verifyToken, rateLimit("ai-request", 10), async (req, res) => {
  const userId = req.userId;
  const {
    totalQuestions,
    accuracy,
    activeDays,
    streak,
    subjectStats,
    typeStats,
  } = req.body;

  try {
    if (!process.env.DEEPSEEK_API_KEY) return res.status(503).json({ error: "AI 服务尚未配置" });
    await reserveAi(req);
    // Build subject summary
    const subjectLines =
      (subjectStats || [])
        .map(
          (s) =>
            `  - ${s.subject_name}：完成 ${s.total} 题，正确率 ${s.accuracy}%`,
        )
        .join("\n") || "  暂无科目数据";

    // Build type summary
    const typeMap = {
      single: "单选题",
      multiple: "多选题",
      judge: "判断题",
      fill: "填空题",
      material: "材料题",
      sort: "排序题",
    };
    const typeLines =
      (typeStats || [])
        .map(
          (t) =>
            `  - ${typeMap[t.type] || t.type}：完成 ${t.total} 题，正确率 ${t.accuracy}%`,
        )
        .join("\n") || "  暂无题型数据";

    // Identify weak and strong subjects
    const sorted = [...(subjectStats || [])].sort(
      (a, b) => a.accuracy - b.accuracy,
    );
    const weakest =
      sorted
        .slice(0, 2)
        .map((s) => s.subject_name)
        .join("、") || "无";
    const strongest =
      [...sorted]
        .reverse()
        .slice(0, 2)
        .map((s) => s.subject_name)
        .join("、") || "无";

    // Data sufficiency note
    const dataNote = totalQuestions < 20
      ? "\n注意: 该学生总刷题量较少，分析结论可能不够可靠，请在建议中提示学生多做题后再获取更精准的分析。"
      : "";

    const prompt = `以下是一位学生的真实刷题数据，请认真分析后给出专业建议。

【学习总览】
- 总刷题数：${totalQuestions} 题
- 整体正确率：${accuracy}%
- 累计活跃天数：${activeDays} 天
- 当前连续打卡：${streak} 天

【各科目完成情况】
${subjectLines}

【各题型掌握情况】
${typeLines}

【初步洞察】
- 薄弱科目：${weakest}
- 优势科目：${strongest}${dataNote}

请基于以上数据给出：
1. **数据洞察**（2-3条，每条一句话总结数据背后的学习规律）
2. **薄弱点分析**（针对正确率最低的科目/题型，分析可能的原因）
3. **改进建议**（2-3条具体可执行的学习策略，避免泛泛而谈）
4. **激励语**（一句根据当前数据定制的鼓励话）

用 Markdown 格式输出，控制在 500 字以内。`;

    const aiRes = await axios.post(
      "https://api.deepseek.com/v1/chat/completions",
      {
        model: "deepseek-chat",
        messages: [
          {
            role: "system",
            content:
              "你是烁学平台的 AI 学情分析师，擅长从刷题数据中提炼有价值的洞察和学习建议。回答简洁专业，引用具体数据，避免套话和空泛建议。用 Markdown 格式输出。",
          },
          { role: "user", content: prompt },
        ],
        temperature: 0.7,
      },
      {
        headers: {
          Authorization: `Bearer ${process.env.DEEPSEEK_API_KEY}`,
          "Content-Type": "application/json",
        },
        timeout: 30000,
      },
    );

    const analysis =
      aiRes.data.choices[0]?.message?.content ||
      "AI 暂时无法生成分析，请稍后再试。";

    // Increment usage
    const today = new Date().toISOString().split("T")[0];
    await pool.query(
      "INSERT INTO user_daily_usage (user_id, date, count) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE count = count + 1",
      [userId, today],
    );

    res.json({ analysis });
  } catch (error) {
    if (error.status) return sendError(res, error);
    console.error("AI Analysis Error:", error.message);
    res.status(500).json({ error: "获取AI分析失败: " + error.message });
  }
});

router.post("/submit-rating", verifyToken, async (req, res) => {
  const userId = req.userId;
  const { rating, feedback } = req.body;
  try {
    // Ensure table exists
    await pool.query(`
            CREATE TABLE IF NOT EXISTS app_ratings (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                rating INT NOT NULL,
                feedback TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        `);

    await pool.query(
      "INSERT INTO app_ratings (user_id, rating, feedback) VALUES (?, ?, ?)",
      [userId, rating, feedback || ""],
    );
    res.json({ message: "感谢您的评分！" });
  } catch (error) {
    console.error("Submit rating error:", error.message);
    res.status(500).json({ error: "提交评分失败" });
  }
});

// ==========================================
// Resources & Favorites API
// ==========================================

// 1. Get Resource Categories
router.get("/resources/categories", async (req, res) => {
  try {
    await pool.query(`
            CREATE TABLE IF NOT EXISTS resource_categories (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                icon_res INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        `);
    // Insert mock data if empty
    const [rows] = await pool.query("SELECT * FROM resource_categories");
    if (rows.length === 0) {
      await pool.query(`
                INSERT INTO resource_categories (name, icon_res) VALUES
                ('考研资料', 0), ('四六级', 0), ('期末复习', 0), ('课外拓展', 0)
            `);
    }

    const [result] = await pool.query(
      "SELECT * FROM resource_categories ORDER BY id",
    );
    res.json(result);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 2. Get Published Articles (public)
router.get("/resources/articles", async (req, res) => {
  try {
    const category = req.query.category;
    const page = parseInt(req.query.page) || 1;
    const limit = parseInt(req.query.limit) || 10;
    const offset = (page - 1) * limit;

    let query = `
            SELECT a.id, a.title, a.summary, a.cover_url, a.category, a.is_pinned, a.view_count,
                   DATE_FORMAT(a.created_at, '%Y-%m-%d') as publish_time,
                   u.username as author_name, u.avatar_icon as author_avatar, u.subscription_end_date as author_subscription_end_date
            FROM articles a
            LEFT JOIN users u ON a.author_id = u.id
            WHERE a.is_published = 1
        `;
    const params = [];
    if (category) {
      query += " AND a.category = ?";
      params.push(category);
    }
    query += " ORDER BY a.is_pinned DESC, a.created_at DESC LIMIT ? OFFSET ?";
    params.push(limit, offset);

    const [rows] = await pool.query(query, params);
    res.json(rows);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 2.5. Get Article Detail (public, increments view_count)
router.get("/resources/articles/:id", async (req, res) => {
  try {
    const { id } = req.params;
    await pool.query(
      "UPDATE articles SET view_count = view_count + 1 WHERE id = ? AND is_published = 1",
      [id],
    );
    const [rows] = await pool.query(
      `
            SELECT a.*, u.username as author_name, u.avatar_icon as author_avatar, u.subscription_end_date as author_subscription_end_date
            FROM articles a
            LEFT JOIN users u ON a.author_id = u.id
            WHERE a.id = ? AND a.is_published = 1
        `,
      [id],
    );
    if (rows.length === 0) return res.status(404).json({ error: "文章不存在" });
    res.json(rows[0]);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 2.6. Get Article Comments
router.get("/resources/articles/:id/comments", async (req, res) => {
  try {
    const { id } = req.params;
    const [rows] = await pool.query(
      `SELECT c.*, u.username, u.avatar_icon, u.subscription_end_date
             FROM article_comments c
             JOIN users u ON c.user_id = u.id
             WHERE c.article_id = ? AND c.status = 'approved'
             ORDER BY c.created_at DESC`,
      [id],
    );
    res.json(rows);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 2.6.8. Update Push Token (JPush Registration ID)
router.post("/user/push-token", verifyToken, async (req, res) => {
  try {
    const { token } = req.body;
    if (!token) return res.status(400).json({ error: "Token (Registration ID) is required" });

    await pool.query(
      "INSERT INTO user_push_tokens (user_id, push_token) VALUES (?, ?) ON DUPLICATE KEY UPDATE push_token = VALUES(push_token)",
      [req.userId, token]
    );
    res.json({ message: "Push token updated successfully" });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Backward compatibility for old endpoint
router.post("/user/fcm-token", verifyToken, async (req, res) => {
  try {
    const { token } = req.body;
    await pool.query(
      "INSERT INTO user_push_tokens (user_id, push_token) VALUES (?, ?) ON DUPLICATE KEY UPDATE push_token = VALUES(push_token)",
      [req.userId, token]
    );
    res.json({ message: "Token updated successfully" });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 2.6.9. Get Notifications
router.get("/notifications", verifyToken, async (req, res) => {
  try {
    const [rows] = await pool.query(
      "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50",
      [req.userId]
    );
    res.json(rows);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 2.7. Post Comment (Login required)
router.post(
  "/resources/articles/:id/comments",
  verifyToken,
  async (req, res) => {
    try {
      const { id } = req.params;
      // Check if user has bound email
      const [userRowsCheck] = await pool.query("SELECT email FROM users WHERE id = ?", [req.userId]);
      if (!userRowsCheck[0] || !userRowsCheck[0].email) {
        return res.status(400).json({ error: "评论功能需要先绑定邮箱，请前往“账户与安全”进行绑定" });
      }

      const { content } = req.body;
      if (!content) return res.status(400).json({ error: "评论内容不能为空" });

      await pool.query(
        "INSERT INTO article_comments (article_id, user_id, content) VALUES (?, ?, ?)",
        [id, req.userId, content],
      );

      // --- NEW: Trigger Push Notification ---
      try {
        // 1. Get article author and info
        const [articleRows] = await pool.query(
          "SELECT title, author_id FROM articles WHERE id = ?",
          [id]
        );

        if (articleRows.length > 0) {
          const article = articleRows[0];
          const authorId = article.author_id;

          // 2. Don't notify if author is the one commenting
          if (authorId && authorId !== req.userId) {
            // 3. Get author's push token
            const [tokenRows] = await pool.query(
              "SELECT push_token FROM user_push_tokens WHERE user_id = ?",
              [authorId]
            );

            if (tokenRows.length > 0) {
              const token = tokenRows[0].push_token;
              const [userRows] = await pool.query("SELECT username FROM users WHERE id = ?", [req.userId]);
              const commenterName = userRows.length > 0 ? userRows[0].username : "有人";

              await sendPushNotification(
                token,
                "收到新评论",
                `${commenterName} 评论了您的文章: ${article.title}`,
                { type: "article_comment", articleId: id }
              );

              // 5. Send email notification to author
              const [authorEmailRows] = await pool.query("SELECT email FROM users WHERE id = ?", [authorId]);
              if (authorEmailRows.length > 0 && authorEmailRows[0].email) {
                await emailService.sendCommentNotificationEmail(
                  authorEmailRows[0].email,
                  article.title,
                  commenterName
                );
              }
            }
          }
        }
      } catch (notifyErr) {
        console.error("Failed to process notification:", notifyErr);
      }
      // ---------------------------------------

      res.status(201).json({ message: "评论发表成功，请等待管理员审核" });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },
);

// 2.7.5. Submit Article Contribution (Login required)
router.post("/resources/articles", verifyToken, async (req, res) => {
  try {
    // Check if user has bound email
    const [userRows] = await pool.query("SELECT email FROM users WHERE id = ?", [req.userId]);
    if (!userRows[0] || !userRows[0].email) {
      return res.status(400).json({ error: "投稿功能需要先绑定邮箱，请前往“账户与安全”进行绑定" });
    }

    const {
      title,
      summary,
      content,
      content_markdown,
      content_format,
      cover_url,
      category,
    } = req.body;
    if (!title || (!content && !content_markdown)) {
      return res.status(400).json({ error: "标题和内容不能为空" });
    }

    const [result] = await pool.query(
      "INSERT INTO articles (title, summary, content, content_markdown, content_format, cover_url, category, author_id, is_published) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
      [
        title,
        summary || null,
        content || null,
        content_markdown || null,
        content_format || "html",
        cover_url || null,
        category || "未分类",
        req.userId,
      ],
    );

    res
      .status(201)
      .json({ id: result.insertId, message: "投稿成功，请等待管理员审核" });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 2.9 Get User Profile Data
// We allow unauthenticated access for other users, but use verifyToken (optional) to support "me"
router.get(
  "/user/:id",
  (req, res, next) => {
    // Optional token verification: if header exists, verify it, otherwise proceed
    const authHeader = req.headers["authorization"];
    if (authHeader) {
      return verifyToken(req, res, next);
    }
    next();
  },
  async (req, res) => {
    try {
      const userId = req.params.id === "me" ? req.userId : req.params.id;
      if (!userId) return res.status(401).json({ error: "未登录" });

      // User Basic Info
      const [userRows] = await pool.query(
        "SELECT id, username, avatar_icon, nickname, slogan, role, qq_openid, email, subscription_end_date, created_at FROM users WHERE id = ?",
        [userId],
      );
      if (userRows.length === 0)
        return res.status(404).json({ error: "用户不存在" });
      const user = userRows[0];

      // User's Articles
      const [articles] = await pool.query(
        "SELECT id, title, summary, cover_url, category, view_count, created_at FROM articles WHERE author_id = ? AND is_published = 1 ORDER BY created_at DESC",
        [userId],
      );

      // User's Dynamics (Recent comments)
      const [comments] = await pool.query(
        `SELECT c.*, a.title as article_title
             FROM article_comments c
             JOIN articles a ON c.article_id = a.id
             WHERE c.user_id = ? AND c.status = 'approved'
             ORDER BY c.created_at DESC LIMIT 10`,
        [userId],
      );

      res.json({
        user: {
          id: user.id,
          username: user.username,
          avatar_icon: user.avatar_icon,
          nickname: user.nickname,
          slogan: user.slogan,
          role: user.role,
          ...(Number(req.userId) === Number(user.id) ? { qq_openid: user.qq_openid, email: user.email } : {}),
          subscription_end_date: user.subscription_end_date,
          join_date: user.created_at,
        },
        articles,
        dynamics: comments,
      });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },
);

// 2.8. Delete Comment (Login required, must be author or admin)
router.delete("/comments/:id", verifyToken, async (req, res) => {
  try {
    const { id } = req.params;
    const [rows] = await pool.query(
      "SELECT * FROM article_comments WHERE id = ?",
      [id],
    );
    if (rows.length === 0) return res.status(404).json({ error: "评论不存在" });

    const comment = rows[0];
    // Check permissions: author (req.userId) or admin (we can check req.userRole if decoded by verifyToken)
    // verifyToken in authMiddleware usually attaches userId to req.
    // Let's check permissions briefly.
    if (comment.user_id !== req.userId) {
      // Need to check if user is admin. We need to query user role if not in token.
      const [userRows] = await pool.query(
        "SELECT role FROM users WHERE id = ?",
        [req.userId],
      );
      if (userRows.length === 0 || userRows[0].role !== "admin") {
        return res.status(403).json({ error: "无权删除此评论" });
      }
    }

    await pool.query("DELETE FROM article_comments WHERE id = ?", [id]);
    res.json({ message: "评论已删除" });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 3. Get User Favorites

// 3.5. Get User Favorites (Full Questions for Practice)

// 4. Add Favorite

// 5. Remove Favorite
router.delete("/favorites/:id", verifyToken, async (req, res) => {
  const favoriteId = req.params.id;
  try {
    await pool.query(
      "DELETE FROM user_favorites WHERE id = ? AND user_id = ?",
      [favoriteId, req.userId],
    );
    res.json({ message: "取消收藏成功" });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 6. Remove Favorite by Question ID
router.delete(
  "/favorites/question/:questionId",
  verifyToken,
  async (req, res) => {
    const questionId = req.params.questionId;
    try {
      await pool.query(
        "DELETE FROM user_favorites WHERE question_id = ? AND user_id = ?",
        [questionId, req.userId],
      );
      res.json({ message: "取消收藏成功" });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },
);

// ================= CLASS SCHEDULE =================

router.get("/schedule", verifyToken, async (req, res) => {
  try {
    const [rows] = await pool.query(
      "SELECT * FROM schedules ORDER BY sort_order ASC, day_of_week ASC",
    );
    res.json(rows);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ================= QUESTION REPORTS =================


router.post("/questions/:id/report", verifyToken, async (req, res) => {
  const questionId = req.params.id;
  const { reason, detail } = req.body;

  if (!reason) return res.status(400).json({ error: "报错原因不能为空" });

  try {
    await assertQuestion(pool, req, req.params.id);
    // Check if user has bound email
    const [userRows] = await pool.query("SELECT email, username FROM users WHERE id = ?", [req.userId]);
    if (!userRows[0] || !userRows[0].email) {
      return res.status(400).json({ error: "提交报错需要先绑定邮箱，请前往“账户与安全”进行绑定" });
    }
    const uName = userRows[0].username;
    const userEmail = userRows[0].email;

    await pool.query(
      "INSERT INTO question_reports (user_id, question_id, reason, detail) VALUES (?, ?, ?, ?)",
      [req.userId, questionId, reason, detail || '']
    );

    const [qRows] = await pool.query("SELECT id, content, type FROM questions WHERE id = ?", [questionId]);

    const [settingsRows] = await pool.query("SELECT setting_value FROM settings WHERE setting_key = 'admin_email_for_reports'");
    const adminEmail = settingsRows[0]?.setting_value;

    if (adminEmail) {
      const qContent = qRows[0] ? qRows[0].content : '未知题目';

      let questionHtml = '';
      if (typeof qContent === 'object') {
        questionHtml = `<p>${qContent.question || ''}</p>`;
      } else {
        questionHtml = `<p>${qContent}</p>`;
      }

      await emailService.sendEmail(
        adminEmail,
        `【题目报错】收到来自 ${uName} 的报错`,
        `
          <h3>收到新的题目报错</h3>
          <p><strong>用户:</strong> ${uName} (ID: ${req.userId}, Email: ${userEmail})</p>
          <p><strong>题目ID:</strong> ${questionId}</p>
          <p><strong>报错原因:</strong> ${reason}</p>
          <p><strong>详细描述:</strong> ${detail || '无'}</p>
          <hr/>
          <p><strong>题目内容:</strong></p>
          <div>${questionHtml}</div>
        `
      );
    }

    res.json({ message: "报错提交成功，感谢您的纠错！" });
  } catch (error) {
    console.error("Report Error:", error);
    res.status(500).json({ error: "提交报错失败" });
  }
});

// =====================================================
// 考试系统 (Exam System)
// =====================================================

// GET /api/data/exams — 获取可用试卷列表 (含用户考试记录状态)
router.get("/exams", verifyToken, async (req, res) => {
  try {
    const [papers] = await pool.query(`
      SELECT ep.id, ep.title, ep.description, ep.duration_minutes, ep.total_score, ep.pass_score, ep.instructions,
        (SELECT COUNT(*) FROM exam_paper_questions WHERE paper_id = ep.id) as question_count
      FROM exam_papers ep
      WHERE ep.is_active = 1
      ORDER BY ep.created_at DESC
    `);

    // 获取用户的考试记录
    const [records] = await pool.query(
      `SELECT id, paper_id, score, total_score, status, can_retry, start_time as started_at, end_time as finished_at
       FROM exam_records WHERE user_id = ? ORDER BY start_time DESC`,
      [req.userId]
    );

    // 将考试记录附加到对应的试卷上
    const result = papers.map(paper => {
      const paperRecords = records.filter(r => r.paper_id === paper.id);
      const latestRecord = paperRecords[0] || null;
      const hasCompleted = paperRecords.some(r => r.status === 'completed');
      const hasAbandoned = paperRecords.some(r => r.status === 'abandoned');
      const canRetry = latestRecord ? latestRecord.can_retry === 1 : true;
      const isInProgress = paperRecords.some(r => r.status === 'in_progress');

      return {
        ...paper,
        latest_record: latestRecord,
        has_completed: hasCompleted,
        has_abandoned: hasAbandoned,
        can_take: (!hasCompleted && !hasAbandoned && !isInProgress) || canRetry,
        is_in_progress: isInProgress
      };
    });

    res.json(result);
  } catch (error) {
    console.error("获取试卷列表失败:", error);
    res.status(500).json({ error: "服务器错误" });
  }
});

// GET /api/data/exams/records — 我的考试记录
router.get("/exams/records", verifyToken, async (req, res) => {
  try {
    const [records] = await pool.query(`
      SELECT er.*, er.start_time as started_at, er.end_time as finished_at, ep.title as paper_title, ep.description as paper_description
      FROM exam_records er
      JOIN exam_papers ep ON er.paper_id = ep.id
      WHERE er.user_id = ?
      ORDER BY er.start_time DESC
    `, [req.userId]);
    // Restore explanations only for the owner's completed records; snapshots keep user data only.
    const paperIds = [...new Set(records.filter(r => r.status === 'completed').map(r => r.paper_id))];
    let protectedQuestions = [];
    if (paperIds.length) {
      [protectedQuestions] = await pool.query('SELECT id, paper_id, content, answer, analysis FROM exam_paper_questions WHERE paper_id IN (?)', [paperIds]);
    }
    const restored = records.map(record => {
      if (record.status !== 'completed') return record;
      let answers = record.answers;
      if (typeof answers === 'string') { try { answers = JSON.parse(answers); } catch { return record; } }
      if (!Array.isArray(answers)) return record;
      const details = answers.map(answer => {
        const question = protectedQuestions.find(q => q.paper_id === record.paper_id && q.id === answer.question_id);
        return question ? { ...answer, correct_answer: question.answer, analysis: question.analysis } : answer;
      });
      return { ...record, answers: JSON.stringify(details) };
    });
    res.json(restored);
  } catch (error) {
    console.error("获取考试记录失败:", error);
    res.status(500).json({ error: "服务器错误" });
  }
});

// POST /api/data/exams/:paperId/start — 开始考试
router.post("/exams/:paperId/start", verifyToken, async (req, res) => {
  const paperId = parseInt(req.params.paperId);
  console.log(`[Exam Start] User ${req.userId} starting paper ${paperId}`);

  try {
    // 检查试卷是否存在且可用
    const [papers] = await pool.query(
      'SELECT id, title, duration_minutes, total_score, pass_score, is_active FROM exam_papers WHERE id = ?',
      [paperId]
    );

    if (papers.length === 0) return res.status(404).json({ error: '试卷不存在' });
    if (papers[0].is_active !== 1) return res.status(403).json({ error: '试卷已停用' });

    const paper = papers[0];

    // 检查是否有进行中的考试
    const [inProgress] = await pool.query(
      'SELECT id FROM exam_records WHERE user_id = ? AND paper_id = ? AND status = ?',
      [req.userId, paperId, 'in_progress']
    );
    if (inProgress.length > 0) {
      console.log(`[Exam Start] Found session in progress: ${inProgress[0].id}`);
      return res.status(400).json({ error: '您有正在进行的考试', record_id: inProgress[0].id });
    }

    // 检查是否已考过且不可重考
    const [completed] = await pool.query(
      `SELECT id, can_retry FROM exam_records
       WHERE user_id = ? AND paper_id = ? AND (status = 'completed' OR status = 'abandoned')
       ORDER BY id DESC LIMIT 1`,
      [req.userId, paperId]
    );

    if (completed.length > 0 && completed[0].can_retry !== 1) {
      return res.status(403).json({ error: '您已完成此考试，如需重考请联系管理员' });
    }

    // 如果允许重考，重置 can_retry 标记
    if (completed.length > 0 && completed[0].can_retry === 1) {
      await pool.query('UPDATE exam_records SET can_retry = 0 WHERE id = ?', [completed[0].id]);
    }

    // 创建考试记录 - 使用通用列名
    console.log(`[Exam Start] Creating record for paper ${paperId}`);
    const [result] = await pool.query(
      `INSERT INTO exam_records (user_id, paper_id, total_score, status)
       VALUES (?, ?, ?, 'in_progress')`,
      [req.userId, paperId, paper.total_score]
    );
    const recordId = result.insertId;

    // 获取题目 (仅上线)
    console.log(`[Exam Start] Fetching questions for paper ${paperId}`);
    const [questions] = await pool.query(
      'SELECT id, question_index, type, content, options, score FROM exam_paper_questions WHERE paper_id = ? AND is_active = 1 ORDER BY question_index',
      [paperId]
    );

    const formattedQuestions = questions.map(q => {
      let options = [];
      try {
        options = typeof q.options === 'string' ? JSON.parse(q.options) : (q.options || []);
      } catch (e) {
        console.error(`[Exam Start] Failed to parse options for question ${q.id}:`, q.options);
      }

      return {
        id: q.id,
        index: q.question_index,
        type: q.type,
        content: q.content,
        options: options,
        score: q.score
      };
    });

    console.log(`[Exam Start] Success. Record: ${recordId}, Questions: ${formattedQuestions.length}`);
    res.json({
      record_id: recordId,
      paper: {
        id: paper.id,
        title: paper.title,
        duration_minutes: paper.duration_minutes,
        total_score: paper.total_score,
        pass_score: paper.pass_score
      },
      questions: formattedQuestions
    });
  } catch (error) {
    console.error("[Exam Start Error]:", error);
    res.status(500).json({ error: "服务器错误", details: error.message });
  }
});

// POST /api/data/exams/:recordId/submit — 提交考试 (服务端评分)
router.post("/exams/:recordId/submit", verifyToken, async (req, res) => {
  const recordId = parseInt(req.params.recordId);
  const { answers } = req.body; // { questionId: userAnswer, ... }

  try {
    // 验证考试记录
    const [records] = await pool.query(
      'SELECT * FROM exam_records WHERE id = ? AND user_id = ? AND status = ?',
      [recordId, req.userId, 'in_progress']
    );
    if (records.length === 0) return res.status(404).json({ error: '没有进行中的考试记录' });
    const record = records[0];

    // 获取试卷所有题目 (含答案)
    const [questions] = await pool.query(
      'SELECT id, question_index, type, content, options, answer, analysis, score FROM exam_paper_questions WHERE paper_id = ? ORDER BY question_index',
      [record.paper_id]
    );

    // 评分
    let totalScore = 0;
    let correctCount = 0;
    let incorrectCount = 0;
    let unansweredCount = 0;
    const detailedAnswers = [];

    for (const q of questions) {
      const userAnswer = answers ? answers[String(q.id)] : null;
      let isCorrect = false;

      if (!userAnswer || userAnswer === '') {
        unansweredCount++;
      } else {
        isCorrect = require('../utils/scoreAnswer').scoreAnswer(q, userAnswer);

        if (isCorrect) {
          correctCount++;
          totalScore += q.score;
        } else {
          incorrectCount++;
        }
      }

      detailedAnswers.push({
        question_id: q.id,
        question_index: q.question_index,
        user_answer: userAnswer || '',
        correct_answer: q.answer,
        is_correct: isCorrect,
        score_earned: isCorrect ? q.score : 0,
        analysis: q.analysis
      });
    }

    // 更新考试记录
    const now = new Date();
    const formattedNow = now.getFullYear() + '-' +
      String(now.getMonth() + 1).padStart(2, '0') + '-' +
      String(now.getDate()).padStart(2, '0') + ' ' +
      String(now.getHours()).padStart(2, '0') + ':' +
      String(now.getMinutes()).padStart(2, '0') + ':' +
      String(now.getSeconds()).padStart(2, '0');

    await pool.query(
      `UPDATE exam_records SET
        score = ?, correct_count = ?, incorrect_count = ?, unanswered_count = ?,
        status = 'completed', answers = ?, end_time = ?
       WHERE id = ?`,
      [totalScore, correctCount, incorrectCount, unansweredCount, JSON.stringify(detailedAnswers.map(({ correct_answer, analysis, ...progress }) => progress)), formattedNow, recordId]
    );

    res.json({
      record_id: recordId,
      score: totalScore,
      total_score: record.total_score,
      correct_count: correctCount,
      incorrect_count: incorrectCount,
      unanswered_count: unansweredCount,
      passed: totalScore >= (await pool.query('SELECT pass_score FROM exam_papers WHERE id = ?', [record.paper_id]))[0][0].pass_score,
      details: detailedAnswers
    });
  } catch (error) {
    console.error("提交考试失败:", error);
    res.status(500).json({ error: "服务器错误" });
  }
});

// POST /api/data/exams/:recordId/abandon — 中止考试 (防作弊: 判0分)
router.post("/exams/:recordId/abandon", verifyToken, async (req, res) => {
  const recordId = parseInt(req.params.recordId);

  try {
    const now = new Date();
    const formattedNow = now.getFullYear() + '-' +
      String(now.getMonth() + 1).padStart(2, '0') + '-' +
      String(now.getDate()).padStart(2, '0') + ' ' +
      String(now.getHours()).padStart(2, '0') + ':' +
      String(now.getMinutes()).padStart(2, '0') + ':' +
      String(now.getSeconds()).padStart(2, '0');

    const [result] = await pool.query(
      `UPDATE exam_records SET
        score = 0, status = 'abandoned', end_time = ?
       WHERE id = ? AND user_id = ? AND status = 'in_progress'`,
      [formattedNow, recordId, req.userId]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ error: '没有进行中的考试记录' });
    }

    res.json({ message: '考试已中止，成绩记为0分' });
  } catch (error) {
    console.error("中止考试失败:", error);
    res.status(500).json({ error: "服务器错误" });
  }
});

module.exports = router;
