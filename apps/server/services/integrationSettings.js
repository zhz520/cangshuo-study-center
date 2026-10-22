const pool = require('../config/db');

const KEYS = [
  'geetest_enabled', 'geetest_web_id', 'geetest_web_key', 'geetest_android_id', 'geetest_android_key',
  'ai_provider', 'ai_base_url', 'ai_api_key', 'ai_model_explain', 'ai_model_reasoning', 'ai_model_analysis', 'ai_timeout_ms',
];
const SECRET_KEYS = new Set(['geetest_web_key', 'geetest_android_key', 'ai_api_key']);
const DEFAULTS = {
  geetest_enabled: 'true', ai_provider: 'deepseek', ai_base_url: 'https://api.deepseek.com/v1',
  ai_model_explain: 'deepseek-chat', ai_model_reasoning: 'deepseek-reasoner', ai_model_analysis: 'deepseek-chat', ai_timeout_ms: '30000',
};
let cache = { at: 0, values: null };

async function values(force = false) {
  if (!force && cache.values && Date.now() - cache.at < 30000) return cache.values;
  const [rows] = await pool.query(`SELECT setting_key, setting_value FROM settings WHERE setting_key IN (${KEYS.map(() => '?').join(',')})`, KEYS);
  const result = { ...DEFAULTS };
  for (const row of rows) result[row.setting_key] = row.setting_value;
  cache = { at: Date.now(), values: result };
  return result;
}

function env(name) { return process.env[name] || ''; }

async function geetestConfig(clientType) {
  const config = await values();
  if (String(config.geetest_enabled) === 'false') return { enabled: false };
  const web = clientType === 'android'
    ? { id: config.geetest_android_id || env('GEETEST_ANDROID_ID'), key: config.geetest_android_key || env('GEETEST_ANDROID_KEY') }
    : { id: config.geetest_web_id || env('GEETEST_WEB_ID'), key: config.geetest_web_key || env('GEETEST_WEB_KEY') };
  return { enabled: true, ...web };
}

async function aiConfig() {
  const config = await values();
  return {
    provider: config.ai_provider || 'deepseek',
    baseUrl: (config.ai_base_url || DEFAULTS.ai_base_url).replace(/\/+$/, ''),
    apiKey: config.ai_api_key || env('DEEPSEEK_API_KEY'),
    timeout: Math.min(120000, Math.max(5000, Number(config.ai_timeout_ms) || 30000)),
    models: {
      explain: config.ai_model_explain || DEFAULTS.ai_model_explain,
      reasoning: config.ai_model_reasoning || DEFAULTS.ai_model_reasoning,
      analysis: config.ai_model_analysis || DEFAULTS.ai_model_analysis,
    },
  };
}

async function aiRequest(kind = 'explain') {
  const config = await aiConfig();
  if (!config.apiKey) {
    const error = new Error('AI 服务尚未配置，请在管理后台填写接口密钥');
    error.status = 503; error.code = 'AI_NOT_CONFIGURED'; throw error;
  }
  return {
    url: config.baseUrl + '/chat/completions',
    model: config.models[kind] || config.models.explain,
    timeout: config.timeout,
    headers: { Authorization: `Bearer ${config.apiKey}`, 'Content-Type': 'application/json' },
  };
}

async function publicAuthConfig() {
  const config = await values();
  return {
    geetest: {
      enabled: String(config.geetest_enabled) !== 'false',
      webId: config.geetest_web_id || env('GEETEST_WEB_ID') || '',
    },
  };
}

function mask(value) {
  if (!value) return '';
  return value.length <= 6 ? '******' : value.slice(0, 3) + '****' + value.slice(-3);
}

async function adminView() {
  const config = await values(true);
  return {
    geetest_enabled: String(config.geetest_enabled) !== 'false',
    geetest_web_id: config.geetest_web_id || env('GEETEST_WEB_ID') || '',
    geetest_android_id: config.geetest_android_id || env('GEETEST_ANDROID_ID') || '',
    geetest_web_key_masked: mask(config.geetest_web_key || env('GEETEST_WEB_KEY')),
    geetest_android_key_masked: mask(config.geetest_android_key || env('GEETEST_ANDROID_KEY')),
    ai_provider: config.ai_provider || 'deepseek',
    ai_base_url: config.ai_base_url || DEFAULTS.ai_base_url,
    ai_model_explain: config.ai_model_explain || DEFAULTS.ai_model_explain,
    ai_model_reasoning: config.ai_model_reasoning || DEFAULTS.ai_model_reasoning,
    ai_model_analysis: config.ai_model_analysis || DEFAULTS.ai_model_analysis,
    ai_timeout_ms: Number(config.ai_timeout_ms) || 30000,
    ai_api_key_masked: mask(config.ai_api_key || env('DEEPSEEK_API_KEY')),
  };
}

function safeBaseUrl(value) {
  let url;
  try { url = new URL(String(value).trim()); } catch { throw Object.assign(new Error('AI 接口地址格式不正确'), { status: 400 }); }
  if (url.protocol !== 'https:' || /^(localhost|127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(url.hostname)) {
    throw Object.assign(new Error('AI 接口地址必须是公网 HTTPS 地址'), { status: 400 });
  }
  return url.toString().replace(/\/+$/, '');
}

async function save(input) {
  const updates = {};
  const textFields = ['geetest_web_id', 'geetest_android_id', 'ai_provider', 'ai_model_explain', 'ai_model_reasoning', 'ai_model_analysis'];
  for (const key of textFields) if (input[key] !== undefined) updates[key] = String(input[key]).trim().slice(0, 120);
  if (input.geetest_enabled !== undefined) updates.geetest_enabled = input.geetest_enabled ? 'true' : 'false';
  if (input.ai_base_url !== undefined) updates.ai_base_url = safeBaseUrl(input.ai_base_url);
  if (input.ai_timeout_ms !== undefined) {
    const timeout = Number(input.ai_timeout_ms);
    if (!Number.isSafeInteger(timeout) || timeout < 5000 || timeout > 120000) throw Object.assign(new Error('超时时间需在 5–120 秒之间'), { status: 400 });
    updates.ai_timeout_ms = String(timeout);
  }
  for (const key of SECRET_KEYS) {
    const value = input[key];
    if (typeof value === 'string' && value.trim()) {
      if (value.trim().length < 8) throw Object.assign(new Error('密钥长度过短'), { status: 400 });
      updates[key] = value.trim();
    }
  }
  if (!Object.keys(updates).length) return adminView();
  for (const [key, value] of Object.entries(updates)) {
    await pool.query('INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE setting_value = ?', [key, value, value]);
  }
  cache = { at: 0, values: null };
  return adminView();
}

module.exports = { values, geetestConfig, aiConfig, aiRequest, publicAuthConfig, adminView, save, SECRET_KEYS };
