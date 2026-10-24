<script setup>
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';
import { computed, onMounted, ref } from 'vue';
import { admin } from '../../api';
import Toast from '../../components/ui/Toast.vue';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';

const users = ref([]);
const majors = ref([]);
const permissions = ref([]);
const loading = ref(true);
const saving = ref(false);
const error = ref('');
const toast = ref(null);
const confirmDialog = ref(null);
const searchQuery = ref('');
const selectedUserId = ref(null);
const mode = ref('all');
const selectedMajorIds = ref([]);

const filteredUsers = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) return users.value;
  return users.value.filter(user => {
    return String(user.username || '').toLowerCase().includes(query)
      || String(user.nickname || '').toLowerCase().includes(query)
      || String(user.id).includes(query);
  });
});

const selectedUser = computed(() => users.value.find(user => user.id === selectedUserId.value) || null);
const restrictedCount = computed(() => users.value.filter(user => permissionIds(user.id).length > 0).length);
const unrestrictedCount = computed(() => users.value.length - restrictedCount.value);
const isDirty = computed(() => {
  if (!selectedUser.value) return false;
  const original = permissionIds(selectedUser.value.id).slice().sort((a, b) => a - b);
  const originalMode = original.length ? 'specified' : 'all';
  if (mode.value !== originalMode) return true;
  if (mode.value === 'all') return false;
  const current = selectedMajorIds.value.slice().sort((a, b) => a - b);
  return current.join(',') !== original.join(',');
});
const canSave = computed(() => Boolean(selectedUser.value) && isDirty.value && !saving.value && (mode.value === 'all' || selectedMajorIds.value.length > 0));

const permissionIds = (userId) => permissions.value.filter(row => row.user_id === userId).map(row => row.major_id);
const accessLabel = (userId) => {
  const count = permissionIds(userId).length;
  if (!count) return '全部专业';
  return count === majors.value.length ? '全部分类（显式）' : String(count) + ' 个分类';
};
const displayName = (user) => user.nickname || user.username || '未命名用户';
const roleLabel = (role) => role === 'admin' ? '管理员' : role === 'demo' ? '演示账号' : '普通用户';

const resetEditor = () => {
  selectedUserId.value = null;
  selectedMajorIds.value = [];
  mode.value = 'all';
};

const openUser = async (user) => {
  if (user.id === selectedUserId.value) return;
  if (isDirty.value) {
    const discard = await confirmDialog.value?.confirm({ title: '放弃未保存修改？', message: '切换到其他账号会丢弃当前权限调整。', confirmText: '继续切换', type: 'warning' });
    if (!discard) return;
  }
  selectedUserId.value = user.id;
  const ids = permissionIds(user.id);
  mode.value = ids.length ? 'specified' : 'all';
  selectedMajorIds.value = ids.slice();
};

const closeEditor = async () => {
  if (isDirty.value) {
    const discard = await confirmDialog.value?.confirm({ title: '关闭权限设置？', message: '当前调整尚未保存。', confirmText: '放弃修改', type: 'warning' });
    if (!discard) return;
  }
  resetEditor();
};

const setMode = (nextMode) => {
  if (nextMode === mode.value) return;
  mode.value = nextMode;
  if (nextMode === 'all') selectedMajorIds.value = [];
  else if (!selectedMajorIds.value.length) selectedMajorIds.value = majors.value.map(major => major.id);
};

const toggleMajor = (majorId) => {
  const selected = new Set(selectedMajorIds.value);
  if (selected.has(majorId)) selected.delete(majorId);
  else selected.add(majorId);
  selectedMajorIds.value = majors.value.filter(major => selected.has(major.id)).map(major => major.id);
};

const selectAllMajors = () => { selectedMajorIds.value = majors.value.map(major => major.id); };
const clearMajorSelection = () => { selectedMajorIds.value = []; };

const fetchData = async () => {
  loading.value = true;
  error.value = '';
  try {
    const [usersRes, majorsRes, permissionsRes] = await Promise.all([
      admin.getUsers(),
      admin.getMajors(),
      admin.getAccessControls(),
    ]);
    users.value = Array.isArray(usersRes.data) ? usersRes.data : [];
    majors.value = Array.isArray(majorsRes.data) ? majorsRes.data : [];
    permissions.value = (permissionsRes.data || []).map(row => ({ user_id: row.user_id, major_id: row.major_id }));
    if (selectedUserId.value && !selectedUser.value) resetEditor();
    else if (selectedUserId.value) {
      const ids = permissionIds(selectedUserId.value);
      selectedMajorIds.value = ids.slice();
      mode.value = ids.length ? 'specified' : 'all';
    }
  } catch (err) {
    error.value = err.response?.data?.error || '无法读取权限配置，请稍后重试';
  } finally {
    loading.value = false;
  }
};

const saveUser = async () => {
  if (!selectedUser.value) return;
  if (mode.value === 'specified' && selectedMajorIds.value.length === 0) {
    toast.value?.add('指定分类模式至少保留一个专业；如需开放全部，请选择“全部专业”', 'warning');
    return;
  }
  const user = selectedUser.value;
  const message = mode.value === 'all'
    ? '恢复为可访问全部专业，包括以后新增的分类。'
    : '限制为 ' + selectedMajorIds.value.length + ' 个专业分类。';
  const confirmed = await confirmDialog.value?.confirm({ title: '保存访问权限', message: displayName(user) + '：' + message, confirmText: '保存', type: 'warning' });
  if (!confirmed) return;
  saving.value = true;
  try {
    const majorIds = mode.value === 'all' ? [] : selectedMajorIds.value.slice();
    await admin.updateAccessControlBatch({ user_id: user.id, major_ids: majorIds });
    permissions.value = permissions.value.filter(row => row.user_id !== user.id);
    if (majorIds.length) permissions.value.push(...majorIds.map(majorId => ({ user_id: user.id, major_id: majorId })));
    toast.value?.add(displayName(user) + ' 的访问权限已保存', 'success');
  } catch (err) {
    toast.value?.add(err.response?.data?.error || '保存失败，原权限未改变', 'error');
  } finally {
    saving.value = false;
  }
};

onMounted(fetchData);
</script>

<template>
  <div class="admin-workspace ui-page access-page admin-stack">
    <header class="access-page-head">
      <AdminPageHeading title="题库访问控制" description="按账号设定可见的专业范围；未设置限制的账号默认可访问全部专业。" />
      <button type="button" class="ui-btn ui-btn-outline ui-btn-sm admin-control" :disabled="loading" @click="fetchData">刷新</button>
    </header>

    <div class="access-metrics">
      <div class="access-metric"><span>账号</span><strong>{{ users.length }}</strong></div>
      <div class="access-metric"><span>全部专业</span><strong>{{ unrestrictedCount }}</strong></div>
      <div class="access-metric"><span>已限制</span><strong>{{ restrictedCount }}</strong></div>
    </div>

    <div v-if="error" class="access-error"><strong>权限数据加载失败</strong><span>{{ error }}</span><button type="button" class="ui-btn ui-btn-outline ui-btn-sm admin-control" @click="fetchData">重试</button></div>
    <div v-else-if="loading" class="access-loading"><span class="ui-spinner"></span><span>正在读取账号与专业目录</span></div>

    <div v-else class="access-layout">
      <section class="access-directory">
        <div class="access-toolbar">
          <div class="ui-search"><svg class="ui-search-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg><input v-model="searchQuery" class="ui-input" placeholder="搜索用户名、昵称或 UID" aria-label="搜索账号" /></div>
          <span>{{ filteredUsers.length }} 个账号</span>
        </div>
        <div v-if="filteredUsers.length" class="access-user-list">
          <button v-for="user in filteredUsers" :key="user.id" type="button" class="access-user-row" :class="{ 'is-active': selectedUserId === user.id }" @click="openUser(user)">
            <span class="access-avatar"><img v-if="user.avatar_icon || user.avatar" :src="user.avatar_icon || user.avatar" alt="" /><span v-else>{{ displayName(user).slice(0, 1) }}</span></span>
            <span class="access-user-copy"><strong>{{ displayName(user) }}</strong><small>@{{ user.username }} · UID {{ user.id }}</small></span>
            <span class="access-mode" :class="{ 'is-restricted': permissionIds(user.id).length > 0 }">{{ accessLabel(user.id) }}</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m9 5 7 7-7 7"/></svg>
          </button>
        </div>
        <div v-else class="settings-empty access-empty"><strong>没有匹配账号</strong><span>换一个用户名或 UID 试试。</span></div>
      </section>

      <aside v-if="selectedUser" class="access-editor">
        <header class="access-editor-head">
          <div><span class="ui-eyebrow">权限设置</span><h2>{{ displayName(selectedUser) }}</h2><p>{{ roleLabel(selectedUser.role) }} · @{{ selectedUser.username }}</p></div>
          <button type="button" class="access-close" aria-label="关闭权限设置" @click="closeEditor">×</button>
        </header>

        <div class="access-mode-tabs" role="group" aria-label="访问模式">
          <button type="button" :class="{ 'is-active': mode === 'all' }" @click="setMode('all')">全部专业</button>
          <button type="button" :class="{ 'is-active': mode === 'specified' }" @click="setMode('specified')">指定分类</button>
        </div>

        <div v-if="mode === 'all'" class="access-all-note"><strong>默认开放</strong><span>当前账号没有分类限制，可访问现有及以后新增的全部专业。</span></div>
        <template v-else>
          <div class="access-editor-tools"><span>已选 {{ selectedMajorIds.length }} / {{ majors.length }}</span><div><button type="button" class="admin-text-action" @click="selectAllMajors">全选</button><button type="button" class="admin-text-action danger" @click="clearMajorSelection">清空</button></div></div>
          <div class="access-major-grid">
            <label v-for="major in majors" :key="major.id" class="access-major" :class="{ 'is-checked': selectedMajorIds.includes(major.id) }">
              <input type="checkbox" :checked="selectedMajorIds.includes(major.id)" @change="toggleMajor(major.id)" />
              <span class="access-check"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg></span>
              <span>{{ major.name }}</span>
            </label>
          </div>
          <p v-if="selectedMajorIds.length === 0" class="access-warning">至少选择一个专业；清空后无法保存，避免误把账号恢复为全部开放。</p>
        </template>

        <footer class="access-save-bar">
          <span>{{ isDirty ? '有未保存修改' : '与当前配置一致' }}</span>
          <div><button type="button" class="ui-btn ui-btn-outline ui-btn-sm admin-control" @click="closeEditor">取消</button><button type="button" class="ui-btn ui-btn-primary ui-btn-sm admin-control" :disabled="!canSave" @click="saveUser">{{ saving ? '保存中…' : '保存权限' }}</button></div>
        </footer>
      </aside>

      <aside v-else class="access-editor access-editor-empty">
        <span class="access-empty-mark">权</span>
        <strong>选择一个账号</strong>
        <p>从左侧账号列表进入权限设置。未设置限制的账号默认可访问全部专业。</p>
      </aside>
    </div>

    <div class="access-note"><strong>权限规则</strong><span>未设置记录表示开放全部专业；保存指定分类后，仅保留被选中的分类。每次保存只影响当前账号。</span></div>
    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialog" />
  </div>
</template>

<style scoped>
.access-page-head { display:flex; align-items:flex-start; justify-content:space-between; gap:20px; }
.access-metrics { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); margin:0; overflow:hidden; border:1px solid var(--border); border-radius:10px; background:var(--bg-card); }
.access-metric { display:flex; flex-direction:column; gap:3px; padding:11px 14px; border-right:1px solid var(--border); }
.access-metric:last-child { border-right:0; }
.access-metric span { color:var(--text-quote); font-size:9px; }
.access-metric strong { color:var(--text-main); font-size:19px; line-height:1.2; font-weight:650; font-variant-numeric:tabular-nums; }
.access-layout { display:grid; grid-template-columns:minmax(0,1.1fr) minmax(330px,.9fr); gap:14px; align-items:start; }
.access-directory,.access-editor { overflow:hidden; border:1px solid var(--border); border-radius:12px; background:var(--bg-card); }
.access-toolbar { display:flex; align-items:center; gap:12px; padding:11px 12px; border-bottom:1px solid var(--border); }
.access-toolbar .ui-search { flex:1; }
.access-toolbar .ui-input { min-height:34px; background:var(--bg-card); }
.access-toolbar > span { flex:0 0 auto; color:var(--text-quote); font-size:10px; }
.access-user-list { max-height:590px; overflow-y:auto; }
.access-user-row { width:100%; min-height:59px; display:flex; align-items:center; gap:10px; padding:9px 14px; border-bottom:1px solid var(--border); color:var(--text-sub); text-align:left; }
.access-user-row:last-child { border-bottom:0; }
.access-user-row:hover { background:var(--bg-card-highlight); }
.access-user-row.is-active { background:var(--primary-soft); box-shadow:inset 2px 0 var(--primary); }
.access-avatar { width:31px; height:31px; flex:0 0 31px; display:grid; place-items:center; overflow:hidden; border-radius:50%; background:var(--bg-card-highlight); color:var(--text-sub); font-size:11px; font-weight:600; }
.access-avatar img { width:100%; height:100%; object-fit:cover; }
.access-user-copy { display:flex; flex:1; min-width:0; flex-direction:column; gap:3px; }
.access-user-copy strong { overflow:hidden; color:var(--text-main); font-size:12px; font-weight:550; text-overflow:ellipsis; white-space:nowrap; }
.access-user-copy small { overflow:hidden; color:var(--text-quote); font-size:9px; text-overflow:ellipsis; white-space:nowrap; }
.access-mode { flex:0 0 auto; padding:3px 6px; border-radius:4px; background:var(--bg-card-highlight); color:var(--text-quote); font-size:9px; }
.access-mode.is-restricted { background:var(--primary-soft); color:var(--primary); }
.access-editor { padding:18px; }
.access-editor-head { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; padding-bottom:14px; border-bottom:1px solid var(--border); }
.access-editor-head h2 { margin:4px 0 0; color:var(--text-main); font-size:18px; font-weight:600; }
.access-editor-head p { margin-top:4px; color:var(--text-quote); font-size:10px; }
.access-close { width:30px; height:30px; border-radius:7px; color:var(--text-sub); font-size:20px; line-height:1; }
.access-close:hover { background:var(--bg-card-highlight); color:var(--text-main); }
.access-mode-tabs { display:flex; gap:2px; padding:3px; margin-top:14px; border-radius:8px; background:var(--bg-card-highlight); }
.access-mode-tabs button { flex:1; min-height:31px; border-radius:6px; color:var(--text-sub); font-size:10px; }
.access-mode-tabs button.is-active { background:var(--bg-card); color:var(--text-main); box-shadow:var(--shadow); font-weight:600; }
.access-all-note { display:flex; flex-direction:column; gap:4px; margin-top:14px; padding:14px; border:1px solid var(--border); border-radius:8px; background:var(--bg-card-highlight); }
.access-all-note strong { color:var(--text-main); font-size:12px; font-weight:550; }
.access-all-note span { color:var(--text-sub); font-size:10px; line-height:1.65; }
.access-editor-tools { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-top:12px; color:var(--text-quote); font-size:10px; }
.access-editor-tools > div { display:flex; }
.access-major-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:5px; max-height:330px; margin-top:8px; overflow-y:auto; }
.access-major { display:flex; align-items:center; gap:7px; min-height:34px; padding:5px 7px; border:1px solid transparent; border-radius:6px; color:var(--text-sub); font-size:10px; cursor:pointer; }
.access-major:hover { background:var(--bg-card-highlight); }
.access-major.is-checked { background:var(--primary-soft); color:var(--text-main); }
.access-major input { position:absolute; opacity:0; pointer-events:none; }
.access-check { width:15px; height:15px; flex:0 0 15px; display:grid; place-items:center; border:1px solid var(--border-strong); border-radius:4px; color:transparent; }
.access-major.is-checked .access-check { border-color:var(--primary); background:var(--primary); color:#fff; }
.access-warning { margin-top:8px; color:var(--warning); font-size:9px; line-height:1.5; }
.access-save-bar { display:flex; align-items:center; justify-content:space-between; gap:10px; margin-top:16px; padding-top:12px; border-top:1px solid var(--border); }
.access-save-bar > span { color:var(--text-quote); font-size:9px; }
.access-save-bar > div { display:flex; gap:6px; }
.access-editor-empty { display:flex; flex-direction:column; align-items:center; justify-content:center; min-height:290px; padding:36px; color:var(--text-sub); text-align:center; }
.access-empty-mark { width:38px; height:38px; display:grid; place-items:center; margin-bottom:14px; border:1px solid var(--border); border-radius:9px; color:var(--text-quote); font-size:12px; }
.access-editor-empty strong { color:var(--text-main); font-size:13px; font-weight:550; }
.access-editor-empty p { max-width:260px; margin-top:6px; color:var(--text-quote); font-size:10px; line-height:1.7; }
.access-loading { display:flex; align-items:center; justify-content:center; gap:10px; min-height:260px; border:1px solid var(--border); border-radius:12px; background:var(--bg-card); color:var(--text-sub); font-size:11px; }
.access-error { display:flex; flex-direction:column; align-items:center; gap:5px; min-height:220px; padding:32px; border:1px solid var(--border); border-radius:12px; background:var(--bg-card); text-align:center; }
.access-error strong { color:var(--danger); font-size:13px; }
.access-error span { color:var(--text-sub); font-size:10px; }
.access-error button { margin-top:8px; }
.access-note { display:flex; gap:10px; margin-top:14px; padding:11px 13px; border:1px solid var(--border); border-radius:8px; background:var(--bg-card-highlight); color:var(--text-sub); font-size:10px; line-height:1.6; }
.access-note strong { flex:0 0 auto; color:var(--text-main); font-weight:550; }
.access-empty { margin:0; border:0; }
@media(max-width:960px) { .access-layout { grid-template-columns:1fr; } .access-user-list { max-height:420px; } }
@media(max-width:600px) {
  .access-page-head { align-items:flex-start; flex-direction:column; }
  .access-metrics { grid-template-columns:repeat(3,minmax(0,1fr)); }
  .access-metric { padding:9px 10px; }
  .access-metric strong { font-size:17px; }
  .access-major-grid { grid-template-columns:1fr; }
  .access-save-bar { align-items:flex-start; flex-direction:column; }
  .access-save-bar > div { width:100%; }
  .access-save-bar .ui-btn { flex:1; }
}
</style>
