const { test } = require('node:test');
const assert = require('node:assert/strict');

process.env.GEETEST_WEB_ID = 'env-web-id';
process.env.GEETEST_WEB_KEY = 'env-web-key-secret';
process.env.GEETEST_ANDROID_ID = 'env-android-id';
process.env.GEETEST_ANDROID_KEY = 'env-android-key-secret';
process.env.DEEPSEEK_API_KEY = 'env-ai-key-secret';

const settings = new Map();
const db = {
  async query(sql, params = []) {
    if (sql.startsWith('SELECT setting_key, setting_value FROM settings')) {
      return [[...settings].map(([setting_key, setting_value]) => ({ setting_key, setting_value }))];
    }
    if (sql.startsWith('INSERT INTO settings')) {
      settings.set(params[0], params[1]);
      return [{ affectedRows: 1 }];
    }
    throw new Error('Unexpected settings statement');
  },
};
require.cache[require.resolve('../config/db')] = {
  id: require.resolve('../config/db'), filename: require.resolve('../config/db'), loaded: true, exports: db,
};

const { publicAuthConfig, adminView, save, aiRequest, geetestConfig } = require('../services/integrationSettings');

test('public auth config exposes the captcha id but never a secret', async () => {
  const config = await publicAuthConfig();
  assert.deepEqual(config, { geetest: { enabled: true, webId: 'env-web-id' } });
  assert.ok(!JSON.stringify(config).includes('secret'));
});

test('admin view masks stored keys and prefers database values', async () => {
  const view = await adminView();
  assert.equal(view.geetest_web_key_masked.includes('env-web-key-secret'), false);
  assert.match(view.geetest_web_key_masked, /^env\*\*\*\*ret$/);
  assert.equal(view.ai_api_key_masked.includes('env-ai-key-secret'), false);
  await save({ geetest_web_id: 'db-web-id', ai_model_explain: 'gpt-4o-mini', ai_base_url: 'https://api.openai.com/v1' });
  const updated = await adminView();
  assert.equal(updated.geetest_web_id, 'db-web-id');
  assert.equal(updated.ai_model_explain, 'gpt-4o-mini');
  assert.equal((await publicAuthConfig()).geetest.webId, 'db-web-id');
});

test('secrets are only written when supplied and are masked on return', async () => {
  const before = await adminView();
  const after = await save({ geetest_enabled: true });
  assert.equal(after.geetest_web_key_masked, before.geetest_web_key_masked);
  const saved = await save({ ai_api_key: 'new-ai-key-123456' });
  assert.ok(!JSON.stringify(saved).includes('new-ai-key-123456'));
  assert.match(saved.ai_api_key_masked, /^new\*\*\*\*456$/);
  const request = await aiRequest('reasoning');
  assert.equal(request.url, 'https://api.openai.com/v1/chat/completions');
  assert.equal(request.model, 'deepseek-reasoner');
  assert.equal(request.headers.Authorization, 'Bearer new-ai-key-123456');
});

test('captcha can be disabled and unsafe AI endpoints are rejected', async () => {
  await save({ geetest_enabled: false });
  assert.deepEqual(await geetestConfig('web'), { enabled: false });
  assert.equal((await publicAuthConfig()).geetest.enabled, false);
  await assert.rejects(save({ ai_base_url: 'http://127.0.0.1:11434/v1' }), { status: 400 });
  await assert.rejects(save({ ai_base_url: 'https://localhost/v1' }), { status: 400 });
  await assert.rejects(save({ ai_timeout_ms: 1000 }), { status: 400 });
});
