<script setup>
import { computed, onMounted, ref } from 'vue';
import { data as api } from '../api';
import Toast from '../components/ui/Toast.vue';

const toast = ref(null);
const majors = ref([]);
const subjects = ref([]);
const majorId = ref('');
const subjectId = ref('');
const password = ref('');
const showPassword = ref(false);
const loading = ref(false);
const error = ref('');
const lastScope = ref('');

const user = computed(() => {
  try { return JSON.parse(localStorage.getItem('user') || '{}') || {}; }
  catch { return {}; }
});
const isPlus = computed(() => {
  const end = user.value.subscription_end_date;
  return user.value.role === 'admin' || (end && new Date(end) > new Date());
});
const selectedSubject = computed(() => subjects.value.find(s => Number(s.id) === Number(subjectId.value)) || null);
const canSubmit = computed(() => Boolean(subjectId.value && password.value.trim() && !loading.value));

const loadMajors = async () => {
  try { majors.value = (await api.getMajors()).data || []; }
  catch { error.value = '专业列表加载失败，请稍后重试'; }
};

const loadSubjects = async () => {
  subjectId.value = '';
  subjects.value = [];
  if (!majorId.value) return;
  loading.value = true;
  try { subjects.value = (await api.getSubjects(majorId.value)).data || []; }
  catch { error.value = '科目列表加载失败，请稍后重试'; }
  finally { loading.value = false; }
};

const readError = async (requestError) => {
  const payload = requestError?.response?.data;
  if (payload instanceof Blob) {
    try { return JSON.parse(await payload.text()).error || '导出失败'; } catch {}
  }
  return requestError?.response?.data?.error || requestError?.message || '导出失败，请稍后重试';
};

const download = async () => {
  error.value = '';
  lastScope.value = '';
  if (!canSubmit.value) { error.value = '请选择科目并输入管理员提供的导出密码'; return; }
  loading.value = true;
  try {
    const response = await api.exportQuestions({ subject_id: Number(subjectId.value), password: password.value.trim() });
    const access = response.headers?.['x-question-access'];
    const blob = response.data instanceof Blob ? response.data : new Blob([response.data], { type: 'text/csv;charset=utf-8' });
    const disposition = response.headers?.['content-disposition'] || '';
    const match = /filename="?([^";]+)"?/i.exec(disposition);
    const filename = match?.[1] || `shuxue-${subjectId.value}-${new Date().toISOString().slice(0,10)}.csv`;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    lastScope.value = access === 'preview' ? '本次导出按免费预览范围生成，每科最多前 20 题。' : '导出完成，文件仅包含当前账号有权限访问的题目。';
    password.value = '';
    toast.value?.add('题库导出已开始下载', 'success');
  } catch (requestError) {
    error.value = await readError(requestError);
  } finally { loading.value = false; }
};

onMounted(loadMajors);
</script>

<template>
  <div class="ui-page export-page">
    <header class="ui-page-head">
      <div><p class="ui-eyebrow">授权导出</p><h1 class="ui-title">题库导出</h1><p class="ui-subtitle">使用管理员生成的账户专属随机密码，导出当前权限范围内的题目。</p></div>
      <span class="ui-badge" :class="isPlus ? 'ui-badge-blue' : ''">{{ isPlus ? 'Plus 权限' : '免费预览权限' }}</span>
    </header>

    <div class="export-layout">
      <section class="ui-card export-form-card">
        <h2 class="ui-section-title">选择导出范围</h2>
        <p class="ui-small ui-muted">每次导出一个科目，最多 12000 道题；完整权限仍由服务端判断。</p>
        <div class="export-fields">
          <div class="auth-field"><label for="export-major">专业</label><select id="export-major" v-model="majorId" class="ui-select" @change="loadSubjects"><option value="">选择专业</option><option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option></select></div>
          <div class="auth-field"><label for="export-subject">科目</label><select id="export-subject" v-model="subjectId" class="ui-select" :disabled="!majorId || loading"><option value="">选择科目</option><option v-for="s in subjects" :key="s.id" :value="s.id">{{ s.name }}</option></select></div>
          <div v-if="selectedSubject" class="export-status">{{ selectedSubject.name }} · {{ selectedSubject.question_count || 0 }} 题记录</div>
          <div class="auth-field"><label for="export-password">导出密码</label><div class="export-password-wrap"><input id="export-password" v-model="password" class="ui-input" :type="showPassword ? 'text' : 'password'" autocomplete="one-time-code" placeholder="输入管理员生成的随机密码" @keyup.enter="download" /><button type="button" class="ui-btn ui-btn-ghost ui-btn-sm export-password-toggle" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button></div></div>
        </div>
        <div v-if="error" class="export-error" role="alert">{{ error }}</div>
        <div v-if="lastScope" class="export-status" role="status">{{ lastScope }}</div>
        <button class="ui-btn ui-btn-primary ui-btn-lg export-submit" :disabled="!canSubmit" @click="download">{{ loading ? '正在生成 CSV…' : '下载 CSV' }}</button>
      </section>

      <aside class="ui-card export-note">
        <span class="ui-eyebrow">导出规则</span>
        <h2 class="ui-section-title">密码只用于这次授权</h2>
        <ul><li>管理员为每个账户单独生成随机密码，服务端只保存哈希。</li><li>密码过期或被重置后，旧密码立即失效。</li><li>免费账号只导出每科前 20 道有效题；Plus 会员导出完整可访问范围。</li><li>导出会记录账号、科目、题目数量和请求 IP，不记录密码内容。</li><li>每次最多 12000 题，覆盖当前最大科目。</li></ul>
        <div class="ui-divider my-4"></div>
        <p class="ui-small ui-muted">如果还没有导出密码，请联系管理员在「管理后台 → 导出授权」生成。导出文件包含答案和解析，请妥善保管。</p>
      </aside>
    </div>
    <Toast ref="toast" />
  </div>
</template>
