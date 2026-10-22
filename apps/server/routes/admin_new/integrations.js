const express = require('express');
const axios = require('axios');
const { adminView, save, aiRequest } = require('../../services/integrationSettings');

const router = express.Router();

router.get('/', async (req, res) => {
  try { res.json(await adminView()); }
  catch { res.status(500).json({ error: '读取第三方服务配置失败' }); }
});

router.post('/', async (req, res) => {
  try { res.json(await save(req.body || {})); }
  catch (error) { res.status(error.status || 500).json({ error: error.status ? error.message : '保存第三方服务配置失败' }); }
});

router.post('/test-ai', async (req, res) => {
  try {
    const ai = await aiRequest('explain');
    const started = Date.now();
    const response = await axios.post(ai.url, {
      model: ai.model,
      messages: [{ role: 'user', content: '只回复“连接正常”四个字。' }],
      temperature: 0,
      max_tokens: 16,
    }, { headers: ai.headers, timeout: Math.min(ai.timeout, 20000) });
    const content = response.data?.choices?.[0]?.message?.content || '';
    res.json({ ok: true, model: ai.model, latency_ms: Date.now() - started, reply: String(content).slice(0, 80) });
  } catch (error) {
    const status = error.response?.status;
    res.status(502).json({ error: status ? `上游返回 ${status}，请检查模型名称、地址或密钥` : (error.status === 503 ? error.message : '无法连接 AI 接口，请检查地址和网络') });
  }
});

module.exports = router;
