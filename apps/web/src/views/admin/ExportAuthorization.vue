<script setup>
import { computed, onMounted, ref } from 'vue';
import { admin } from '../../api';
import Toast from '../../components/ui/Toast.vue';

const toast = ref(null);
const users = ref([]);
const total = ref(0);
const page = ref(1);
const limit = 50;
const search = ref('');
const loading = ref(false);
const generatingId = ref(null);
const generated = ref(null);
const user = computed(() => {
  try { return JSON.parse(localStorage.getItem('user') || '{}') || {}; }
  catch { return {}; }
});
const isDemo = computed(() => user.value.role === 'demo');

const load = async () => {
  loading.value = true;
  try {
    const response = await admin.getExportCredentials({ page: page.value, limit, search: search.value.trim() });
    users.value = response.data.users || [];
    total.value = response.data.total || 0;
  } catch (error) {
    toast.value?.add(error.response?.data?.error || '读取导出授权失败', 'error');
  } finally { loading.value = false; }
};

const searchUsers = () => { page.value = 1; load(); };
const nextPage = () => { if ((page.value * limit) < total.value) { page.value++; load(); } };
const prevPage = () => { if (page.value > 1) { page.value--; load(); } };

const generate = async (row) => {
  if (isDemo.value) return toast.value?.add('演示账号不能生成导出密码', 'warning');
  if (row.has_export_password && !window.confirm(`重新生成会立即使 ${row.username} 的旧密码失效，继续吗？`)) return;
  generatingId.value = row.id;
  try {
    const response = await admin.generateExportPassword(row.id);
    generated.value = response.data;
    await load();
  } catch (error) {
    toast.value?.add(error.response?.data?.error || '生成导出密码失败', 'error');
  } finally { generatingId.value = null; }
};

const revoke = async (row) => {
  if (!window.confirm(`撤销 ${row.username} 的导出授权？`)) return;
  try { await admin.revokeExportPassword(row.id); toast.value?.add('导出授权已撤销', 'success'); await load(); }
  catch (error) { toast.value?.add(error.response?.data?.error || '撤销失败', 'error'); }
};

const copyPassword = async () => {
  if (!generated.value?.password) return;
  try { await navigator.clipboard.writeText(generated.value.password); toast.value?.add('密码已复制', 'success'); }
  catch { toast.value?.add('复制失败，请手动选择密码', 'warning'); }
};

const formatDate = (value) => value ? String(value).slice(0, 16).replace('T', ' ') : '—';
const closeGenerated = () => { generated.value = null; };

onMounted(load);
</script>

<template>
  <div class="admin-page">
    <header class="ui-page-head">
      <div><p class="ui-eyebrow">安全管理</p><h1 class="ui-title">题库导出授权</h1><p class="ui-subtitle">为单个账户生成随机导出密码，旧密码会在重新生成后立即失效。</p></div>
    </header>

    <section class="ui-card export-admin-toolbar">
      <div class="ui-search export-admin-search"><svg class="ui-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg><input v-model="search" class="ui-input" placeholder="搜索用户名、昵称或邮箱" @keyup.enter="searchUsers" /><button class="ui-btn ui-btn-primary ui-btn-sm" @click="searchUsers">搜索</button></div>
      <button class="ui-btn ui-btn-outline ui-btn-sm" :disabled="loading" @click="load">刷新</button>
    </section>

    <section class="ui-card export-admin-table-wrap">
      <div v-if="loading" class="ui-loading"><span class="ui-spinner mx-auto"></span><p class="mt-3">正在读取用户</p></div>
      <div v-else-if="!users.length" class="ui-empty">没有匹配的用户</div>
      <table v-else class="export-admin-table">
        <thead><tr><th>账户</th><th>身份</th><th>导出授权</th><th>到期</th><th>最近使用</th><th class="text-right">操作</th></tr></thead>
        <tbody><tr v-for="row in users" :key="row.id">
          <td><strong>{{ row.nickname || row.username }}</strong><small>{{ row.username }}<span v-if="row.email"> · {{ row.email }}</span></small></td>
          <td><span class="ui-badge" :class="row.role === 'admin' ? 'ui-badge-blue' : ''">{{ row.role === 'admin' ? '管理员' : row.role === 'demo' ? '演示' : '用户' }}</span></td>
          <td><span class="ui-badge" :class="row.has_export_password ? 'ui-badge-green' : ''">{{ row.has_export_password ? '已生成' : '未生成' }}</span></td>
          <td>{{ formatDate(row.expires_at) }}</td><td>{{ formatDate(row.last_used_at) }}</td>
          <td class="text-right export-admin-actions"><button class="ui-btn ui-btn-primary ui-btn-sm" :disabled="generatingId === row.id || isDemo" @click="generate(row)">{{ generatingId === row.id ? '生成中…' : row.has_export_password ? '重置密码' : '生成密码' }}</button><button v-if="row.has_export_password" class="ui-btn ui-btn-ghost ui-btn-sm" :disabled="isDemo" @click="revoke(row)">撤销</button></td>
        </tr></tbody>
      </table>
      <div v-if="!loading && total > limit" class="export-admin-pager"><span>共 {{ total }} 个账户</span><div><button class="ui-btn ui-btn-outline ui-btn-sm" :disabled="page <= 1" @click="prevPage">上一页</button><span>{{ page }} / {{ Math.ceil(total / limit) }}</span><button class="ui-btn ui-btn-outline ui-btn-sm" :disabled="page * limit >= total" @click="nextPage">下一页</button></div></div>
    </section>

    <div v-if="generated" class="ui-sheet-backdrop" @click="closeGenerated"></div>
    <div v-if="generated" class="ui-modal export-password-modal" role="dialog" aria-modal="true" aria-label="导出密码">
      <div class="ui-row-between"><div><p class="ui-eyebrow">仅显示一次</p><h3 class="ui-section-title">{{ generated.user.nickname || generated.user.username }} 的导出密码</h3></div><button class="web-icon-btn" @click="closeGenerated">×</button></div>
      <p class="ui-small ui-muted mt-3">关闭后无法再次查看，请立即复制并通过安全渠道发送给该账户。</p>
      <div class="export-password-display">{{ generated.password }}</div>
      <div class="ui-row-between mt-3"><span class="ui-small ui-faint">有效期至 {{ formatDate(generated.expires_at) }}</span><button class="ui-btn ui-btn-primary ui-btn-sm" @click="copyPassword">复制密码</button></div>
      <button class="ui-btn ui-btn-outline w-full mt-4" @click="closeGenerated">我已保存，关闭</button>
    </div>
    <Toast ref="toast" />
  </div>
</template>

<style scoped>
.export-admin-toolbar { display: flex; align-items: center; gap: 12px; padding: 12px; margin-bottom: 14px; }
.export-admin-search { display: flex; align-items: center; gap: 8px; flex: 1; }
.export-admin-search .ui-search-icon { left: 13px; }
.export-admin-search .ui-input { padding-left: 36px; }
.export-admin-table-wrap { overflow-x: auto; }
.export-admin-table { width: 100%; min-width: 800px; }
.export-admin-table th, .export-admin-table td { padding: 13px 14px; border-bottom: 1px solid var(--border); text-align: left; vertical-align: middle; font-size: 12px; }
.export-admin-table th { font-size: 10px; font-weight: 600; letter-spacing: .06em; color: var(--text-quote); }
.export-admin-table tr:last-child td { border-bottom: 0; }
.export-admin-table td strong { display: block; font-size: 13px; color: var(--text-main); }
.export-admin-table td small { display: block; margin-top: 3px; color: var(--text-quote); font-size: 10px; }
.export-admin-actions { white-space: nowrap; }
.export-admin-actions .ui-btn + .ui-btn { margin-left: 4px; }
.export-admin-pager { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; border-top: 1px solid var(--border); color: var(--text-sub); font-size: 11px; }
.export-admin-pager > div { display: flex; align-items: center; gap: 8px; }
.export-password-display { margin-top: 18px; padding: 16px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg-card-highlight); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 20px; letter-spacing: .12em; text-align: center; color: var(--text-main); user-select: all; }
@media (max-width: 600px) { .export-admin-toolbar { align-items: stretch; flex-direction: column; } .export-admin-search { width: 100%; } }
</style>
