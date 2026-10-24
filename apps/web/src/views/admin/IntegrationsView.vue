<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { admin } from '../../api';
import Toast from '../../components/ui/Toast.vue';
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';

const toast = ref(null);
const loading = ref(true);
const saving = ref(false);
const testing = ref(false);
const testResult = ref(null);
const keys = reactive({ web: '', android: '', ai: '' });
const form = reactive({
  geetest_enabled: true,
  geetest_web_id: '',
  geetest_android_id: '',
  ai_provider: 'deepseek',
  ai_base_url: 'https://api.deepseek.com/v1',
  ai_model_explain: 'deepseek-chat',
  ai_model_reasoning: 'deepseek-reasoner',
  ai_model_analysis: 'deepseek-chat',
  ai_timeout_ms: 30000,
});
const masked = reactive({ web: '', android: '', ai: '' });
const providers = [
  { value: 'deepseek', label: 'DeepSeek', base: 'https://api.deepseek.com/v1' },
  { value: 'openai', label: 'OpenAI', base: 'https://api.openai.com/v1' },
  { value: 'custom', label: 'OpenAI 兼容接口', base: '' },
];
const modelHint = computed(() => providers.find(p => p.value === form.ai_provider)?.label || '自定义');

const applyProvider = () => {
  const provider = providers.find(p => p.value === form.ai_provider);
  if (provider?.base) form.ai_base_url = provider.base;
};

const load = async () => {
  loading.value = true;
  try {
    const { data } = await admin.getIntegrationSettings();
    Object.assign(form, {
      geetest_enabled: data.geetest_enabled !== false,
      geetest_web_id: data.geetest_web_id || '',
      geetest_android_id: data.geetest_android_id || '',
      ai_provider: data.ai_provider || 'deepseek',
      ai_base_url: data.ai_base_url || 'https://api.deepseek.com/v1',
      ai_model_explain: data.ai_model_explain || 'deepseek-chat',
      ai_model_reasoning: data.ai_model_reasoning || 'deepseek-reasoner',
      ai_model_analysis: data.ai_model_analysis || 'deepseek-chat',
      ai_timeout_ms: data.ai_timeout_ms || 30000,
    });
    masked.web = data.geetest_web_key_masked || '';
    masked.android = data.geetest_android_key_masked || '';
    masked.ai = data.ai_api_key_masked || '';
  } catch (error) {
    toast.value?.add(error.response?.data?.error || '加载配置失败', 'error');
  } finally {
    loading.value = false;
  }
};

const save = async () => {
  saving.value = true;
  testResult.value = null;
  try {
    const payload = { ...form };
    if (keys.web.trim()) payload.geetest_web_key = keys.web.trim();
    if (keys.android.trim()) payload.geetest_android_key = keys.android.trim();
    if (keys.ai.trim()) payload.ai_api_key = keys.ai.trim();
    const { data } = await admin.saveIntegrationSettings(payload);
    masked.web = data.geetest_web_key_masked || '';
    masked.android = data.geetest_android_key_masked || '';
    masked.ai = data.ai_api_key_masked || '';
    keys.web = ''; keys.android = ''; keys.ai = '';
    toast.value?.add('第三方服务配置已保存', 'success');
  } catch (error) {
    toast.value?.add(error.response?.data?.error || '保存失败', 'error');
  } finally {
    saving.value = false;
  }
};

const testAi = async () => {
  testing.value = true;
  testResult.value = null;
  try {
    const { data } = await admin.testAiIntegration();
    testResult.value = { ok: true, text: `${data.model} 连接正常 · ${data.latency_ms}ms` };
  } catch (error) {
    testResult.value = { ok: false, text: error.response?.data?.error || '连接失败' };
  } finally {
    testing.value = false;
  }
};

onMounted(load);
</script>

<template>
  <div class="admin-workspace ui-page admin-stack">
    <header class="integration-heading">
      <div>
        <AdminPageHeading title="第三方服务" description="配置极验人机验证与 AI 模型接口。密钥只保存在服务端，接口不会返回明文。" />
      </div>
      <button class="ui-btn ui-btn-primary" :disabled="saving || loading" @click="save">{{ saving ? '保存中…' : '保存配置' }}</button>
    </header>

    <div v-if="loading" class="ui-loading"><div class="ui-spinner" style="margin:0 auto 10px"></div>正在读取配置…</div>

    <div v-else class="integration-grid">
      <section class="ui-card ui-card-pad">
        <div class="ui-row-between">
          <div>
            <h2 class="ui-section-title">极验 Geetest</h2>
            <p class="ui-small ui-muted">登录、注册和找回密码的人机验证。</p>
          </div>
          <label class="integration-switch">
            <input v-model="form.geetest_enabled" type="checkbox" />
            <span>{{ form.geetest_enabled ? '已启用' : '已关闭' }}</span>
          </label>
        </div>

        <div class="integration-fields">
          <label class="auth-field">
            <span>Web Captcha ID</span>
            <input v-model="form.geetest_web_id" class="ui-input" placeholder="极验控制台中的 Web 验证 ID" />
          </label>
          <label class="auth-field">
            <span>Web Key</span>
            <input v-model="keys.web" class="ui-input" type="password" autocomplete="new-password" :placeholder="masked.web ? `已配置 ${masked.web}，留空保持不变` : '填写 Web 验证 Key'" />
          </label>
          <label class="auth-field">
            <span>Android Captcha ID</span>
            <input v-model="form.geetest_android_id" class="ui-input" placeholder="Android 端验证 ID" />
          </label>
          <label class="auth-field">
            <span>Android Key</span>
            <input v-model="keys.android" class="ui-input" type="password" autocomplete="new-password" :placeholder="masked.android ? `已配置 ${masked.android}，留空保持不变` : '填写 Android 验证 Key'" />
          </label>
        </div>
        <p class="integration-note">Web 端会自动读取这里的 Captcha ID。Android 端的 Captcha ID 内置在安装包中，修改后需要发布新的 APK 才能生效；服务端 Key 可立即更新。</p>
      </section>

      <section class="ui-card ui-card-pad">
        <div class="ui-row-between">
          <div>
            <h2 class="ui-section-title">AI 模型</h2>
            <p class="ui-small ui-muted">支持 DeepSeek、OpenAI 及兼容 Chat Completions 的服务。</p>
          </div>
          <span class="ui-badge ui-badge-blue">{{ modelHint }}</span>
        </div>

        <div class="integration-fields">
          <label class="auth-field">
            <span>服务类型</span>
            <select v-model="form.ai_provider" class="ui-select" @change="applyProvider">
              <option v-for="p in providers" :key="p.value" :value="p.value">{{ p.label }}</option>
            </select>
          </label>
          <label class="auth-field">
            <span>接口地址</span>
            <input v-model="form.ai_base_url" class="ui-input" placeholder="https://api.deepseek.com/v1" />
          </label>
          <label class="auth-field">
            <span>API Key</span>
            <input v-model="keys.ai" class="ui-input" type="password" autocomplete="new-password" :placeholder="masked.ai ? `已配置 ${masked.ai}，留空保持不变` : '填写接口密钥'" />
          </label>
          <div class="integration-models">
            <label class="auth-field"><span>题目讲解模型</span><input v-model="form.ai_model_explain" class="ui-input" placeholder="deepseek-chat" /></label>
            <label class="auth-field"><span>深度讲解模型</span><input v-model="form.ai_model_reasoning" class="ui-input" placeholder="deepseek-reasoner" /></label>
            <label class="auth-field"><span>学情分析模型</span><input v-model="form.ai_model_analysis" class="ui-input" placeholder="deepseek-chat" /></label>
            <label class="auth-field"><span>超时（毫秒）</span><input v-model.number="form.ai_timeout_ms" class="ui-input" type="number" min="5000" max="120000" step="1000" /></label>
          </div>
        </div>

        <div class="integration-test">
          <button class="ui-btn ui-btn-outline ui-btn-sm" :disabled="testing" @click="testAi">{{ testing ? '正在测试…' : '测试连接' }}</button>
          <span v-if="testResult" :class="testResult.ok ? 'is-ok' : 'is-error'">{{ testResult.text }}</span>
        </div>
        <p class="integration-note">修改模型名称会立即作用于新的 AI 请求。接口必须使用公网 HTTPS 地址。</p>
      </section>
    </div>
    <Toast ref="toast" />
  </div>
</template>

<style scoped>
.integration-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; padding-bottom:18px; margin-bottom:16px; border-bottom:1px solid var(--border); }
.integration-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; align-items: start; }
.integration-fields { display: grid; gap: 13px; margin-top: 18px; }
.integration-models { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 13px; }
.integration-switch { display: inline-flex; align-items: center; gap: 7px; font-size: 11px; color: var(--text-sub); }
.integration-switch input { width: 15px; height: 15px; accent-color: var(--primary); }
.integration-note { margin-top: 14px; font-size: 11px; line-height: 1.7; color: var(--text-quote); }
.integration-test { display: flex; align-items: center; gap: 10px; margin-top: 14px; font-size: 11px; }
.integration-test .is-ok { color: var(--success); }
.integration-test .is-error { color: var(--danger); }
@media (max-width: 900px) { .integration-grid { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .integration-heading { align-items:flex-start; flex-direction:column; } }
@media (max-width: 560px) { .integration-models { grid-template-columns: 1fr; } }
</style>
