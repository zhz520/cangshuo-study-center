<script setup>
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';
import { ref, reactive, onMounted } from 'vue';
import { admin } from '../../api';
import Toast from '../../components/ui/Toast.vue';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';

const activeTab = ref('versions'); // 'versions' or 'patches'

// --- State ---
const versions = ref([]);
const patches = ref([]);
const loading = ref(false);
const error = ref('');
const toast = ref(null);
const confirmDialog = ref(null);

// --- Fetch Data ---
const fetchData = async () => {
  loading.value = true;
  error.value = '';
  try {
    if (activeTab.value === 'versions') {
      const res = await admin.getAppVersions();
      versions.value = res.data;
    } else {
      const res = await admin.getAppPatches();
      patches.value = res.data;
    }
  } catch (err) {
    error.value = err.response?.data?.message || '获取数据失败';
  } finally {
    loading.value = false;
  }
};

const switchTab = (tab) => {
    activeTab.value = tab;
    fetchData();
};

// --- Versions Modal ---
const showVersionModal = ref(false);
const editingVersion = ref(null);
const versionForm = ref({
    version_name: '',
    version_code: null,
    download_url: '',
    update_log: '',
    is_force: 0,
    status: 1
});

const openAddVersion = () => {
    editingVersion.value = null;
    versionForm.value = { version_name: '', version_code: null, download_url: '', update_log: '', is_force: 0, status: 1 };
    showVersionModal.value = true;
};

const openEditVersion = (v) => {
    editingVersion.value = v;
    versionForm.value = { ...v };
    showVersionModal.value = true;
};

const handleSaveVersion = async () => {
    if (!versionForm.value.version_name || !versionForm.value.version_code || !versionForm.value.download_url) {
        toast.value?.add('请填写必填项(版本名, 版本号, 下载地址)', 'warning');
        return;
    }

    try {
        if (editingVersion.value) {
            await admin.updateAppVersion(editingVersion.value.id, versionForm.value);
            toast.value?.add('版本更新成功', 'success');
        } else {
            await admin.createAppVersion(versionForm.value);
            toast.value?.add('版本创建成功', 'success');
        }
        showVersionModal.value = false;
        fetchData();
    } catch (err) {
        toast.value?.add(err.response?.data?.message || '保存失败', 'error');
    }
};

const deleteVersion = async (id) => {
    const confirmed = await confirmDialog.value.confirm({
        title: "确认删除版",
        message: '确认删除该安装包记录吗？',
        type: "danger",
        confirmText: "确认删除",
        cancelText: "取消"
    });
    if (!confirmed) return;
    try {
        await admin.deleteAppVersion(id);
        toast.value?.add('删除成功', 'success');
        fetchData();
    } catch (err) {
        toast.value?.add(err.response?.data?.message || '删除失败', 'error');
    }
};

// --- Patches Modal ---
const showPatchModal = ref(false);
const editingPatch = ref(null);
const patchForm = ref({
    target_version_code: null,
    patch_version: null,
    download_url: '',
    md5: '',
    status: 1
});

const openAddPatch = () => {
    editingPatch.value = null;
    patchForm.value = { target_version_code: null, patch_version: null, download_url: '', md5: '', status: 1 };
    showPatchModal.value = true;
};

const openEditPatch = (p) => {
    editingPatch.value = p;
    patchForm.value = { ...p };
    showPatchModal.value = true;
};

const handleSavePatch = async () => {
    if (!patchForm.value.target_version_code || !patchForm.value.patch_version || !patchForm.value.download_url) {
        toast.value?.add('请填写必填项(目标版本号, 补丁号, 下载地址)', 'warning');
        return;
    }

    try {
        if (editingPatch.value) {
            await admin.updateAppPatch(editingPatch.value.id, patchForm.value);
            toast.value?.add('补丁更新成功', 'success');
        } else {
            await admin.createAppPatch(patchForm.value);
            toast.value?.add('补丁创建成功', 'success');
        }
        showPatchModal.value = false;
        fetchData();
    } catch (err) {
        toast.value?.add(err.response?.data?.message || '保存失败', 'error');
    }
};

const deletePatch = async (id) => {
    const confirmed = await confirmDialog.value.confirm({
        title: "确认删除补丁",
        message: '确认删除该补丁记录吗？',
        type: "danger",
        confirmText: "确认删除",
        cancelText: "取消"
    });
    if (!confirmed) return;
    try {
        await admin.deleteAppPatch(id);
        toast.value?.add('删除成功', 'success');
        fetchData();
    } catch (err) {
        toast.value?.add(err.response?.data?.message || '删除失败', 'error');
    }
};

// Lifecycle
onMounted(() => {
    fetchData();
});
</script>

<template>
  <div class="admin-workspace ui-page admin-stack">
    <div class="admin-page-bar">
      <AdminPageHeading title="App 版本" description="管理 Android 全量安装包与热修复补丁。" />
      <div class="admin-page-actions">
        <button class="ui-btn ui-btn-primary admin-control" type="button" @click="activeTab === 'versions' ? openAddVersion() : openAddPatch()">
          新增{{ activeTab === 'versions' ? '安装包' : '修复补丁' }}
        </button>
      </div>
    </div>

    <div class="admin-tabs">
      <button class="admin-tab" :class="{ 'is-active': activeTab === 'versions' }" type="button" @click="switchTab('versions')">全量更新</button>
      <button class="admin-tab" :class="{ 'is-active': activeTab === 'patches' }" type="button" @click="switchTab('patches')">热修复补丁</button>
    </div>

    <div v-if="loading" class="admin-table-card">
      <div class="admin-empty"><span class="ui-spinner"></span><span class="admin-empty-title">正在加载版本记录</span></div>
    </div>

    <div v-else-if="error" class="admin-table-card">
      <div class="admin-empty">
        <span class="admin-empty-title">无法读取版本记录</span>
        <span class="admin-empty-copy">{{ error }}</span>
        <button class="ui-btn ui-btn-outline ui-btn-sm mt-3" type="button" @click="fetchData">重试</button>
      </div>
    </div>

    <template v-else>
        <!-- ======== VERSIONS TAB ======== -->
        <div v-if="activeTab === 'versions'">
            <!-- Desktop Table -->
            <div class="hidden lg:block admin-table-card">
                <table class="w-full text-left admin-data-table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>版本信息</th>
                            <th>更新策略</th>
                            <th>发布时间</th>
                            <th class="text-right">操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="v in versions" :key="v.id" :class="{'opacity-50': v.status === 0}">
                            <td class="font-mono text-[11px]" style="color:var(--text-quote)">#{{ v.id }}</td>
                            <td>
                                <div style="color:var(--text-main);font-weight:550">{{ v.version_name }}</div>
                                <div class="text-[10px]" style="color:var(--text-quote)">Code {{ v.version_code }}</div>
                            </td>
                            <td>
                                <span class="admin-role" :class="{ 'is-admin': v.is_force }">
                                    {{ v.is_force ? '强制更新' : '普通更新' }}
                                </span>
                            </td>
                            <td class="text-[11px]" style="color:var(--text-sub)">
                                {{ new Date(v.create_time).toLocaleDateString() }}
                            </td>
                            <td class="text-right">
                                <div class="admin-row-actions justify-end">
                                    <button @click="openEditVersion(v)" class="admin-text-action">编辑</button>
                                    <button @click="deleteVersion(v.id)" class="admin-text-action danger">删除</button>
                                </div>
                            </td>
                        </tr>
                        <tr v-if="versions.length === 0">
                            <td colspan="5"><div class="admin-empty admin-table-empty"><span class="admin-empty-title">暂无安装包</span><span class="admin-empty-copy">新增版本后，客户端会收到更新提示。</span></div></td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <!-- Mobile Cards -->
            <div class="lg:hidden grid gap-4">
                <div v-for="v in versions" :key="v.id" class="ui-card p-4 relative" :class="{'opacity-50': v.status === 0}">
                    <div class="flex justify-between items-start mb-2">
                        <div>
                            <div class="text-slate-800 dark:text-white flex items-center gap-2">
                                v{{ v.version_name }}
                                <span class="px-2 py-0.5 rounded text-[10px]" :class="v.is_force ? 'bg-rose-100 text-rose-600 dark:bg-rose-900/30 dark:text-rose-400' : 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400'">
                                    {{ v.is_force ? '强制' : '普通' }}
                                </span>
                            </div>
                            <div class="text-[11px] text-slate-400 font-mono mt-1">Code: {{ v.version_code }} | ID: {{ v.id }}</div>
                        </div>
                    </div>
                    <div class="text-xs text-slate-500 dark:text-slate-400 mt-2 bg-slate-50 dark:bg-slate-800/50 p-2 rounded line-clamp-2">
                        {{ v.update_log || '无更新日志' }}
                    </div>
                    <div class="flex gap-2 mt-4">
                        <button @click="openEditVersion(v)" class="flex-1 py-1.5 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 rounded-lg text-xs font-bold transition-colors admin-control">编辑</button>
                        <button @click="deleteVersion(v.id)" class="flex-1 py-1.5 bg-rose-50 dark:bg-rose-900/20 text-rose-500 rounded-lg text-xs font-bold transition-colors admin-control">删除</button>
                    </div>
                </div>
                <div v-if="versions.length === 0" class="text-center p-5 text-slate-400 text-sm ui-card">暂无版本数据</div>
            </div>
        </div>

        <!-- ======== PATCHES TAB ======== -->
        <div v-if="activeTab === 'patches'">
            <!-- Desktop Table -->
            <div class="hidden lg:block ui-card overflow-hidden">
                <table class="w-full text-left border-collapse admin-data-table">
                    <thead>
                        <tr class="border-b border-slate-200/60 dark:border-slate-700/60 text-xs text-slate-500 dark:text-slate-400 uppercase font-bold bg-slate-50/50 dark:bg-slate-800/30">
                            <th class="">ID</th>
                            <th class="">目标版本号</th>
                            <th class="">补丁号 (Patch)</th>
                            <th class="">发布时间</th>
                            <th class="text-right">操作</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100 dark:divide-slate-700/50">
                        <tr v-for="p in patches" :key="p.id" class="group hover:bg-orange-50/30 dark:hover:bg-orange-900/10 transition-colors duration-200" :class="{'opacity-50': p.status === 0}">
                            <td class="font-mono text-xs font-bold text-slate-400">#{{ p.id }}</td>
                            <td class="font-bold text-slate-700 dark:text-slate-200">
                                Target Code: {{ p.target_version_code }}
                            </td>
                            <td class="">
                                <span class="px-2 py-1 rounded-md text-xs font-bold bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400">
                                    Patch v{{ p.patch_version }}
                                </span>
                            </td>
                            <td class="text-xs font-medium text-slate-500 dark:text-slate-400">
                                {{ new Date(p.create_time).toLocaleDateString() }}
                            </td>
                            <td class="text-right">
                                <button @click="openEditPatch(p)" class="p-2 text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-900/30 rounded-lg mr-2 admin-control">编辑</button>
                                <button @click="deletePatch(p.id)" class="p-2 text-rose-500 hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-900/30 rounded-lg admin-control">删除</button>
                            </td>
                        </tr>
                        <tr v-if="patches.length === 0">
                            <td colspan="5" class="py-8 text-center text-slate-400 text-sm">暂无补丁数据</td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <!-- Mobile Cards -->
            <div class="lg:hidden grid gap-4">
                <div v-for="p in patches" :key="p.id" class="ui-card p-4 relative" :class="{'opacity-50': p.status === 0}">
                    <div class="flex justify-between items-start mb-2">
                        <div>
                            <div class="text-slate-800 dark:text-white flex items-center gap-2">
                                适配于 Code: {{ p.target_version_code }}
                            </div>
                            <div class="text-[11px] text-slate-400 font-mono mt-1">
                                <span class="px-2 py-0.5 rounded bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400 font-bold">Patch {{ p.patch_version }}</span>
                            </div>
                        </div>
                    </div>
                    <div class="flex gap-2 mt-4">
                        <button @click="openEditPatch(p)" class="flex-1 py-1.5 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 rounded-lg text-xs font-bold transition-colors admin-control">编辑</button>
                        <button @click="deletePatch(p.id)" class="flex-1 py-1.5 bg-rose-50 dark:bg-rose-900/20 text-rose-500 rounded-lg text-xs font-bold transition-colors admin-control">删除</button>
                    </div>
                </div>
                <div v-if="patches.length === 0" class="text-center p-5 text-slate-400 text-sm ui-card">暂无补丁数据</div>
            </div>
        </div>
    </template>

    <!-- ======== VERSION MODAL ======== -->
    <div v-if="showVersionModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 transition-all p-4">
        <div class="ui-card w-full max-w-lg p-6 sm:p-8 relative overflow-hidden max-h-[90vh] flex flex-col">
            <h3 class="text-xl text-slate-800 dark:text-white mb-6 flex-shrink-0">
                {{ editingVersion ? '编辑全量安装包' : '新增全量安装包' }}
            </h3>

            <div class="space-y-4 overflow-y-auto flex-1 pr-2 custom-scrollbar">
                <div class="grid grid-cols-2 gap-4">
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">版本名 (如 1.0.1) *</label>
                        <input v-model="versionForm.version_name" type="text" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none ui-input">
                    </div>
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">版本号 (递增数字) *</label>
                        <input v-model="versionForm.version_code" type="number" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none ui-input">
                    </div>
                </div>

                <div class="space-y-1">
                    <label class="text-xs font-bold text-slate-500 ml-1">APK 下载地址 (URL) *</label>
                    <input v-model="versionForm.download_url" type="text" placeholder="https://..." class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none ui-input">
                </div>

                <div class="space-y-1">
                    <label class="text-xs font-bold text-slate-500 ml-1">更新日志</label>
                    <textarea v-model="versionForm.update_log" rows="3" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none resize-none ui-textarea"></textarea>
                </div>

                <div class="grid grid-cols-2 gap-4">
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">是否强制更新</label>
                        <select v-model="versionForm.is_force" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none ui-select">
                            <option :value="0">否 (建议更新)</option>
                            <option :value="1">是 (强制更新)</option>
                        </select>
                    </div>
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">生效状态</label>
                        <select v-model="versionForm.status" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none ui-select">
                            <option :value="1">上架生效</option>
                            <option :value="0">下架隐藏</option>
                        </select>
                    </div>
                </div>
            </div>

            <div class="flex gap-3 mt-6 pt-4 border-t border-slate-200 dark:border-slate-700 flex-shrink-0">
                <button @click="showVersionModal = false" class="flex-1 py-3 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-sm transition-colors admin-control">取消</button>
                <button @click="handleSaveVersion" class="flex-1 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm dark:shadow-none transition-all admin-control">保存</button>
            </div>
        </div>
    </div>

    <!-- ======== PATCH MODAL ======== -->
    <div v-if="showPatchModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 transition-all p-4">
        <div class="ui-card w-full max-w-lg p-6 sm:p-8 relative overflow-hidden max-h-[90vh] flex flex-col">
            <h3 class="text-xl text-slate-800 dark:text-white mb-6 flex-shrink-0">
                {{ editingPatch ? '编辑热修复补丁' : '新增热修复补丁' }}
            </h3>

            <div class="space-y-4 overflow-y-auto flex-1 pr-2 custom-scrollbar">
                <div class="grid grid-cols-2 gap-4">
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">基准版本号 (目标受众) *</label>
                        <input v-model="patchForm.target_version_code" type="number" placeholder="如修复 v2 的 bug，此处填 2" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none ui-input">
                    </div>
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">补丁版本号 (递增) *</label>
                        <input v-model="patchForm.patch_version" type="number" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none ui-input">
                    </div>
                </div>

                <div class="space-y-1">
                    <label class="text-xs font-bold text-slate-500 ml-1">补丁下载地址 (URL) *</label>
                    <input v-model="patchForm.download_url" type="text" placeholder="https://..." class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none ui-input">
                </div>

                <div class="space-y-1">
                    <label class="text-xs font-bold text-slate-500 ml-1">MD5 校验值 (可选)</label>
                    <input v-model="patchForm.md5" type="text" placeholder="建议填写，防止下载损坏" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none ui-input">
                </div>

                <div class="space-y-1">
                    <label class="text-xs font-bold text-slate-500 ml-1">生效状态</label>
                    <select v-model="patchForm.status" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none ui-select">
                        <option :value="1">分发生效</option>
                        <option :value="0">停止分发</option>
                    </select>
                </div>
            </div>

            <div class="flex gap-3 mt-6 pt-4 border-t border-slate-200 dark:border-slate-700 flex-shrink-0">
                <button @click="showPatchModal = false" class="flex-1 py-3 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-sm transition-colors admin-control">取消</button>
                <button @click="handleSavePatch" class="flex-1 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm dark:shadow-none transition-all admin-control">保存</button>
            </div>
        </div>
    </div>

    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialog" />
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 10px;
}
:deep(.dark) .custom-scrollbar::-webkit-scrollbar-thumb {
  background: #475569;
}
</style>
