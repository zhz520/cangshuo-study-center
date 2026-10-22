const express = require('express');
const router = express.Router();
const pool = require('../config/db');
const axios = require('axios');
const { parseVideoWatermark } = require('../utils/videoParser');
const { PUBLIC_SETTINGS, publicSettings } = require('../services/publicSettings');

// Get all public homepage data
router.get('/home-data', async (req, res) => {
    try {
        const connection = await pool.getConnection();
        const data = {};

        try {
            // 1. Settings (e.g., registration status, site announcements if any)
            const [settings] = await connection.query('SELECT setting_key, setting_value FROM settings WHERE setting_key IN (?)', [PUBLIC_SETTINGS]);
            data.settings = publicSettings(settings);

            // 2. Countdowns
            const [countdowns] = await connection.query('SELECT * FROM countdowns ORDER BY sort_order ASC');
            data.countdowns = countdowns;



            // 4. External Links
            const [links] = await connection.query('SELECT * FROM external_links ORDER BY sort_order ASC');
            data.externalLinks = links;

        } finally {
            connection.release();
        }

        res.json(data);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Get real platform statistics
router.get('/stats', async (req, res) => {
    try {
        const [questionRows] = await pool.query('SELECT COUNT(*) as count FROM questions WHERE is_active = 1');
        const [userRows] = await pool.query('SELECT COUNT(*) as count FROM users');

        res.json({
            success: true,
            data: {
                questionCount: questionRows[0].count,
                userCount: userRows[0].count,
                satisfaction: '98%',
                aiUptime: '24/7'
            }
        });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

const { verifyToken } = require('../middleware/authMiddleware');

// API for Short Video Watermark Removal (Requires Plus Subscription)
router.post('/video/parse', verifyToken, async (req, res) => {
    try {
        if (!req.account.has_subscription && req.account.role !== 'admin') return res.status(403).json({ error: '此功能需要 Plus 会员', code: 'SUBSCRIPTION_REQUIRED' });
        const { url } = req.body;
        if (!url) {
            return res.status(400).json({ success: false, error: '请提供包含短视频链接的文本' });
        }
        const result = await parseVideoWatermark(url);
        res.json({ success: true, data: result });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

// 成绩查询接口 (Score Query) - 支持 姓名+考号 或 姓名+学校名(模糊)
router.post('/score-query', async (req, res) => {
    try {
        const { student_name, exam_number, school_name } = req.body;
        if (!student_name) {
            return res.status(400).json({ success: false, error: '请输入考生姓名' });
        }
        if (!exam_number && !school_name) {
            return res.status(400).json({ success: false, error: '请输入准考证号或学校名称进行查询' });
        }

        let queryParams = [student_name];
        let queryStr = 'SELECT * FROM `exam_scores` WHERE `student_name` = ?';

        if (exam_number) {
            queryStr += ' AND `exam_number` = ?';
            queryParams.push(exam_number);
        } else if (school_name) {
            queryStr += ' AND `school_name` LIKE ?';
            queryParams.push(`%${school_name}%`);
        }

        // LIMIT to 5 just in case there are multiple identical names in one school
        queryStr += ' ORDER BY id ASC LIMIT 5';

        const [rows] = await pool.query(queryStr, queryParams);

        if (rows.length === 0) {
            return res.status(404).json({ success: false, error: '未查询到成绩信息，请核对输入条件' });
        }

        res.json({ success: true, data: rows }); // Return array of results
    } catch (error) {
        console.error('Score query error:', error);
        res.status(500).json({ success: false, error: '服务器内部错误' });
    }
});

// 获取所有有成绩的学校列表
router.get('/schools', async (req, res) => {
    try {
        const [rows] = await pool.query('SELECT DISTINCT school_name FROM exam_scores WHERE school_name IS NOT NULL AND school_name != "" ORDER BY school_name ASC');
        const schools = rows.map(r => r.school_name);
        res.json({ success: true, data: schools });
    } catch (error) {
        console.error('Fetch schools error:', error);
        res.status(500).json({ success: false, error: '获取学校列表失败' });
    }
});

// AI 个性化成绩诊断 (调用 DeepSeek)
router.post('/score-analysis', verifyToken, require('../services/securityLimits').rateLimit('ai-request', 10), async (req, res) => {
    const { student_name, school_name, major_category, total_score, total_rank, score_details } = req.body;

    if (!student_name || total_score == null) {
        return res.status(400).json({ success: false, error: '缺少必要的成绩参数' });
    }

    try {
        if (!process.env.DEEPSEEK_API_KEY) return res.status(503).json({ success: false, error: 'AI 服务尚未配置' });
        await require('../services/securityLimits').reserveAi(req);
        // Parse score details
        let details = {};
        try {
            details = typeof score_details === 'string' ? JSON.parse(score_details) : (score_details || {});
        } catch (e) {}

        // Determine professional max score
        const isComputer = major_category && major_category.includes('计算机');
        const profMax = isComputer ? 200 : 350;
        const totalMax = 150 + 150 + 100 + profMax;

        // Build subject lines
        const subjects = [
            { name: '语文', max: 150, data: details.chinese },
            { name: '数学', max: 150, data: details.math },
            { name: '英语', max: 100, data: details.english },
            { name: '专业综合', max: profMax, data: details.professional }
        ];

        const subjectLines = subjects.map(s => {
            const total = s.data?.total || '缺考';
            const obj = s.data?.objective || '-';
            const sub = s.data?.subjective || '-';
            const rank = s.data?.rank_school || '-';
            const pct = s.data?.total ? (parseFloat(s.data.total) / s.max * 100).toFixed(1) : '0';
            return `  - ${s.name}（满分${s.max}）: ${total}分 (得分率${pct}%), 客观${obj} / 主观${sub}, 校内名次#${rank}`;
        }).join('\n');

        const defeatRate = total_rank ? (100 - (total_rank / 4497 * 100)).toFixed(1) : '未知';

        const prompt = `你是一位资深的四川省中等职业学校对口高考升学规划导师。以下是一位中职考生在"2025—2026学年四川省职教高考研究联合体第四次模拟考试"中的详细成绩数据，请为该考生做一份个性化、深入且有温度的学情诊断分析报告。

【考生档案】
- 姓名：${student_name}
- 学校：${school_name || '未知'}
- 专业：${major_category || '未知'}${isComputer ? '（本次计算机专业仅考理论，满分200分）' : ''}
- 总分：${total_score}/${totalMax} (得分率 ${(total_score / totalMax * 100).toFixed(1)}%)
- 联考全省排名：第 ${total_rank || '未知'} 名 / 共4497人 (击败了约${defeatRate}%的竞争者)

【各科成绩明细】
${subjectLines}

请按照以下结构输出分析报告（约600-800字），语言风格要兼具专业性和鼓励性：

1. 🎯 整体画像：用2-3句话概括该考生的总体表现水平，定位其在全省竞争力中的段位
2. ⚡ 核心优势锁定：指出得分率最高的1-2科，分析这些科目做得好的原因，给出保持优势的建议
3. 🔍 薄弱环节深挖：指出得分率最低的科目，从客观题/主观题两个维度分析失分原因，给出2-3条极具针对性的提分建议
4. 📊 偏科诊断：分析各科得分率均衡度，如果存在严重偏科给出矫正策略
5. 🎓 升学冲刺指导：基于当前总分和排名，预测可冲刺的院校层次（本科/优质大专/普通大专），给出最后冲刺阶段的核心时间分配建议
6. 💬 寄语：一段富有深意和力量的个性化鼓励话

纯文本分点输出，可使用 emoji 增加可读性。不要使用 Markdown 标题语法或加粗语法。`;

        const aiRes = await axios.post(
            'https://api.deepseek.com/v1/chat/completions',
            {
                model: 'deepseek-chat',
                messages: [
                    { role: 'system', content: '你是烁学平台的 AI 升学规划导师，专注于四川省中职对口高考（职教高考）领域。你需要基于考生的真实成绩数据，给出深度个性化的分析和建议。语气专业但有温度，引用具体数据，避免泛泛而谈。' },
                    { role: 'user', content: prompt }
                ],
                temperature: 0.75,
                max_tokens: 2000
            },
            {
                headers: {
                    Authorization: `Bearer ${process.env.DEEPSEEK_API_KEY}`,
                    'Content-Type': 'application/json'
                },
                timeout: 60000
            }
        );

        const aiContent = aiRes.data.choices[0]?.message?.content;
        if (aiContent) {
            res.json({ success: true, data: aiContent });
        } else {
            res.json({ success: false, error: 'AI 未返回有效内容' });
        }
    } catch (error) {
        if (error.status) return require('../services/securityLimits').sendError(res, error);
        console.error('AI Score Analysis Error:', error.message);
        res.status(500).json({ success: false, error: 'AI 分析服务暂时不可用，请稍后再试' });
    }
});

module.exports = router;
