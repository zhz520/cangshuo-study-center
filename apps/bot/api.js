const axios = require('axios');
require('dotenv').config();

const API_BASE = process.env.BACKEND_API_BASE || 'http://localhost:3000/api/bot';
// To use AI explain, we still need the main API auth or just an open route
const AI_EXPLAIN_API = process.env.AI_EXPLAIN_API || 'http://localhost:3000/api/data/ai-explain';

const api = axios.create({
    baseURL: API_BASE,
    headers: { "X-Bot-API-Key": process.env.BOT_API_SECRET || "" },
    timeout: 30000
});

module.exports = {
    // 1. 获取科目列表
    async getSubjects() {
        try {
            const res = await api.get('/subjects');
            return res.data;
        } catch (error) {
            console.error('getSubjects error:', error.message);
            return [];
        }
    },

    // 2. 随机抽取一道题（考虑用户答题历史，优先错题）
    async getRandomQuestion(subjectId, qqOpenid) {
        try {
            const res = await api.get('/random-question', {
                params: { subject_id: subjectId, qq_openid: qqOpenid }
            });
            return res.data;
        } catch (error) {
            console.error('getRandomQuestion error:', error.message);
            return null;
        }
    },

    // 3. 提交做题记录
    async submitProgress(qqOpenid, nickname, avatarIcon, questionId, isCorrect, answer) {
        try {
            const res = await api.post('/progress', {
                qq_openid: qqOpenid,
                nickname: nickname,
                avatar_icon: avatarIcon,
                question_id: questionId,
                is_correct: isCorrect,
                answer: answer
            });
            return res.data;
        } catch (error) {
            console.error('submitProgress error:', error.message);
            return null;
        }
    },

    // 4. 获取用户统计数据
    async getStats(qqOpenid) {
        try {
            const res = await api.get('/stats', {
                params: { qq_openid: qqOpenid }
            });
            return res.data;
        } catch (error) {
            console.error('getStats error:', error.message);
            return null;
        }
    },
    
    // 5. 获取排行榜
    async getLeaderboard() {
        try {
            const res = await api.get('/leaderboard');
            return res.data;
        } catch (error) {
            console.error('getLeaderboard error:', error.message);
            return [];
        }
    },

    // 6. 调用 AI 解析服务
    // 因为这需要扣除配额，原接口需要 token，这里我们暂时写死或者在原有接口加个特例
    // 为了简单且不破坏原有，这里直接在机器人层调用之前写在 data.js 里的同款请求，
    // 或者在后端提供一个 bot 专属的 AI 接口。我现在在机器人端直接拼接 prompt 请 deepseek 解析，
    // 这样不影响主站的用户每日额度限制，机器人也可以有自己的额度管理。
    async getAIExplain(question, options, userAnswer, correctAnswer) {
        try {
            const DEEPSEEK_KEY = process.env.DEEPSEEK_API_KEY; // 从机器人.env读
            if (!DEEPSEEK_KEY) return '机器人未配置 AI API Key，暂无法提供解析。';

            let optionsStr = "";
            if (options) {
                if (typeof options === 'object') {
                    optionsStr = Object.entries(options).map(([k, v]) => `${k}. ${v}`).join('\n');
                } else if (typeof options === 'string') {
                    optionsStr = options;
                }
            }
        
            const prompt = `
你是一位耐心的私人家教。请为学生讲解这道题。
【题目】
${question}

【选项】
${optionsStr}

【正确答案】
${correctAnswer}

【学生的答案】
${userAnswer}

请生成一段通俗易懂的解析：
1. 先直接指出为什么学生的答案是错的（如果学生答错）。
2. 详细解释为什么正确答案是对的。
3. 如果可能，给出一个类比或记忆口诀帮助理解。
4. 语气要亲切自然，适合在QQ群里阅读。
            `;

            const aiRes = await axios.post(
                "https://api.deepseek.com/v1/chat/completions",
                {
                    model: "deepseek-chat",
                    messages: [
                        { role: "system", content: "You are a helpful tutor." },
                        { role: "user", content: prompt },
                    ],
                    temperature: 0.7,
                },
                {
                    headers: {
                        Authorization: `Bearer ${DEEPSEEK_KEY}`,
                        "Content-Type": "application/json",
                    },
                    timeout: 20000, 
                }
            );

            return aiRes.data.choices[0]?.message?.content || "AI 暂时无法生成解析，请稍后再试。";
        } catch (error) {
            console.error('getAIExplain error:', error.message);
            return "获取解析失败，网络好像出错了。";
        }
    }
};
