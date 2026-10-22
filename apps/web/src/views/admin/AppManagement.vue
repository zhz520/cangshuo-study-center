<script setup>
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
  <div class="p-4 sm:p-6 space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
            <h2 class="text-2xl font-black text-slate-800 dark:text-white tracking-tight">App版本管理</h2>
            <p class="text-sm text-slate-500 dark:text-slate-400 font-medium">管理原生客户端的 APK 全量包与 Tinker 热修复补丁</p>
        </div>
        
        <button 
            @click="activeTab === 'versions' ? openAddVersion() : openAddPatch()" 
            class="group relative px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-2xl shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95 overflow-hidden w-full sm:w-auto text-center"
        >
            <div class="absolute inset-0 bg-white/20 translate-y-full group-hover:translate-y-0 transition-transform duration-300"></div>
            <span class="relative font-bold flex items-center justify-center gap-2 text-sm">
                <span>+</span> 新增{{ activeTab === 'versions' ? '安装包' : '修复补丁' }}
            </span>
        </button>
    </div>

    <!-- Tabs -->
    <div class="flex space-x-2 bg-slate-200/50 dark:bg-slate-800/50 p-1 rounded-2xl w-full sm:w-fit">
        <button 
            @click="switchTab('versions')"
            class="flex-1 sm:flex-none px-6 py-2.5 rounded-xl text-sm font-bold transition-all relative z-10"
            :class="activeTab === 'versions' ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-400 shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'"
        >
            全量更新 (APK)
        </button>
        <button 
            @click="switchTab('patches')"
            class="flex-1 sm:flex-none px-6 py-2.5 rounded-xl text-sm font-bold transition-all relative z-10"
            :class="activeTab === 'patches' ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-400 shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'"
        >
            热修复补丁 (Tinker)
        </button>
    </div>

    <!-- Content Area (Loading, Error, List) -->
    <div v-if="loading" class="glass-card p-12 flex flex-col items-center justify-center text-slate-400 animate-pulse">
        <div class="w-12 h-12 rounded-full border-4 border-slate-200 border-t-indigo-500 animate-spin mb-4"></div>
        <span class="font-bold text-sm tracking-wider">LOADING DATA...</span>
    </div>
    
    <div v-else-if="error" class="glass-card p-10 text-center">
        <div class="text-4xl mb-4">⚠️</div>
        <div class="text-rose-500 font-bold mb-2">数据加载失败</div>
        <div class="text-slate-400 text-sm">{{ error }}</div>
        <button @click="fetchData" class="mt-4 px-4 py-2 bg-slate-100 dark:bg-slate-800 rounded-lg text-xs font-bold text-slate-600 dark:text-slate-400 hover:text-indigo-500 transition-colors">重试</button>
    </div>

    <template v-else>
        <!-- ======== VERSIONS TAB ======== -->
        <div v-if="activeTab === 'versions'">
            <!-- Desktop Table -->
            <div class="hidden lg:block glass-card overflow-hidden">
                <table class="w-full text-left border-collapse">
                    <thead>
                        <tr class="border-b border-slate-200/60 dark:border-slate-700/60 text-xs text-slate-500 dark:text-slate-400 uppercase tracking-wider font-bold bg-slate-50/50 dark:bg-slate-800/30">
                            <th class="px-6 py-4">ID</th>
                            <th class="px-6 py-4">版本信息</th>
                            <th class="px-6 py-4">更新策略</th>
                            <th class="px-6 py-4">发布时间</th>
                            <th class="px-6 py-4 text-right">操作</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100 dark:divide-slate-700/50">
                        <tr v-for="v in versions" :key="v.id" class="group hover:bg-indigo-50/30 dark:hover:bg-indigo-900/10 transition-colors duration-200" :class="{'opacity-50': v.status === 0}">
                            <td class="px-6 py-4 font-mono text-xs font-bold text-slate-400">#{{ v.id }}</td>
                            <td class="px-6 py-4">
                                <div class="font-bold text-slate-700 dark:text-slate-200">{{ v.version_name }}</div>
                                <div class="text-[10px] text-slate-400">Code: {{ v.version_code }}</div>
                            </td>
                            <td class="px-6 py-4">
                                <span class="px-2 py-1 rounded-md text-xs font-bold" :class="v.is_force ? 'bg-rose-100 text-rose-600 dark:bg-rose-900/30 dark:text-rose-400' : 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400'">
                                    {{ v.is_force ? '强制更新' : '普通更新' }}
                                </span>
                            </td>
                            <td class="px-6 py-4 text-xs font-medium text-slate-500 dark:text-slate-400">
                                {{ new Date(v.create_time).toLocaleDateString() }}
                            </td>
                            <td class="px-6 py-4 text-right">
                                <button @click="openEditVersion(v)" class="p-2 text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-900/30 rounded-lg mr-2">编辑</button>
                                <button @click="deleteVersion(v.id)" class="p-2 text-rose-500 hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-900/30 rounded-lg">删除</button>
                            </td>
                        </tr>
                        <tr v-if="versions.length === 0">
                            <td colspan="5" class="px-6 py-8 text-center text-slate-400 text-sm">暂无版本数据</td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <!-- Mobile Cards -->
            <div class="lg:hidden grid gap-4">
                <div v-for="v in versions" :key="v.id" class="glass-card p-4 relative" :class="{'opacity-50': v.status === 0}">
                    <div class="flex justify-between items-start mb-2">
                        <div>
                            <div class="font-black text-slate-800 dark:text-white flex items-center gap-2">
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
                        <button @click="openEditVersion(v)" class="flex-1 py-1.5 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 rounded-lg text-xs font-bold transition-colors">编辑</button>
                        <button @click="deleteVersion(v.id)" class="flex-1 py-1.5 bg-rose-50 dark:bg-rose-900/20 text-rose-500 rounded-lg text-xs font-bold transition-colors">删除</button>
                    </div>
                </div>
                <div v-if="versions.length === 0" class="text-center p-8 text-slate-400 text-sm glass-card">暂无版本数据</div>
            </div>
        </div>

        <!-- ======== PATCHES TAB ======== -->
        <div v-if="activeTab === 'patches'">
            <!-- Desktop Table -->
            <div class="hidden lg:block glass-card overflow-hidden">
                <table class="w-full text-left border-collapse">
                    <thead>
                        <tr class="border-b border-slate-200/60 dark:border-slate-700/60 text-xs text-slate-500 dark:text-slate-400 uppercase tracking-wider font-bold bg-slate-50/50 dark:bg-slate-800/30">
                            <th class="px-6 py-4">ID</th>
                            <th class="px-6 py-4">目标版本号</th>
                            <th class="px-6 py-4">补丁号 (Patch)</th>
                            <th class="px-6 py-4">发布时间</th>
                            <th class="px-6 py-4 text-right">操作</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100 dark:divide-slate-700/50">
                        <tr v-for="p in patches" :key="p.id" class="group hover:bg-orange-50/30 dark:hover:bg-orange-900/10 transition-colors duration-200" :class="{'opacity-50': p.status === 0}">
                            <td class="px-6 py-4 font-mono text-xs font-bold text-slate-400">#{{ p.id }}</td>
                            <td class="px-6 py-4 font-bold text-slate-700 dark:text-slate-200">
                                Target Code: {{ p.target_version_code }}
                            </td>
                            <td class="px-6 py-4">
                                <span class="px-2 py-1 rounded-md text-xs font-bold bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400">
                                    Patch v{{ p.patch_version }}
                                </span>
                            </td>
                            <td class="px-6 py-4 text-xs font-medium text-slate-500 dark:text-slate-400">
                                {{ new Date(p.create_time).toLocaleDateString() }}
                            </td>
                            <td class="px-6 py-4 text-right">
                                <button @click="openEditPatch(p)" class="p-2 text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-900/30 rounded-lg mr-2">编辑</button>
                                <button @click="deletePatch(p.id)" class="p-2 text-rose-500 hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-900/30 rounded-lg">删除</button>
                            </td>
                        </tr>
                        <tr v-if="patches.length === 0">
                            <td colspan="5" class="px-6 py-8 text-center text-slate-400 text-sm">暂无补丁数据</td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <!-- Mobile Cards -->
            <div class="lg:hidden grid gap-4">
                <div v-for="p in patches" :key="p.id" class="glass-card p-4 relative" :class="{'opacity-50': p.status === 0}">
                    <div class="flex justify-between items-start mb-2">
                        <div>
                            <div class="font-black text-slate-800 dark:text-white flex items-center gap-2">
                                适配于 Code: {{ p.target_version_code }}
                            </div>
                            <div class="text-[11px] text-slate-400 font-mono mt-1">
                                <span class="px-2 py-0.5 rounded bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400 font-bold">Patch {{ p.patch_version }}</span>
                            </div>
                        </div>
                    </div>
                    <div class="flex gap-2 mt-4">
                        <button @click="openEditPatch(p)" class="flex-1 py-1.5 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 rounded-lg text-xs font-bold transition-colors">编辑</button>
                        <button @click="deletePatch(p.id)" class="flex-1 py-1.5 bg-rose-50 dark:bg-rose-900/20 text-rose-500 rounded-lg text-xs font-bold transition-colors">删除</button>
                    </div>
                </div>
                <div v-if="patches.length === 0" class="text-center p-8 text-slate-400 text-sm glass-card">暂无补丁数据</div>
            </div>
        </div>
    </template>

    <!-- ======== VERSION MODAL ======== -->
    <div v-if="showVersionModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-md transition-all p-4">
        <div class="glass-card w-full max-w-lg p-6 sm:p-8 relative overflow-hidden shadow-2xl max-h-[90vh] flex flex-col">
            <h3 class="text-xl font-black text-slate-800 dark:text-white mb-6 flex-shrink-0">
                {{ editingVersion ? '编辑全量安装包' : '新增全量安装包' }}
            </h3>
            
            <div class="space-y-4 overflow-y-auto flex-1 pr-2 custom-scrollbar">
                <div class="grid grid-cols-2 gap-4">
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">版本名 (如 1.0.1) *</label>
                        <input v-model="versionForm.version_name" type="text" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none">
                    </div>
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">版本号 (递增数字) *</label>
                        <input v-model="versionForm.version_code" type="number" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none">
                    </div>
                </div>
                
                <div class="space-y-1">
                    <label class="text-xs font-bold text-slate-500 ml-1">APK 下载地址 (URL) *</label>
                    <input v-model="versionForm.download_url" type="text" placeholder="https://..." class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none">
                </div>
                
                <div class="space-y-1">
                    <label class="text-xs font-bold text-slate-500 ml-1">更新日志</label>
                    <textarea v-model="versionForm.update_log" rows="3" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none resize-none"></textarea>
                </div>

                <div class="grid grid-cols-2 gap-4">
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">是否强制更新</label>
                        <select v-model="versionForm.is_force" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none">
                            <option :value="0">否 (建议更新)</option>
                            <option :value="1">是 (强制更新)</option>
                        </select>
                    </div>
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">生效状态</label>
                        <select v-model="versionForm.status" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none">
                            <option :value="1">上架生效</option>
                            <option :value="0">下架隐藏</option>
                        </select>
                    </div>
                </div>
            </div>

            <div class="flex gap-3 mt-6 pt-4 border-t border-slate-200 dark:border-slate-700 flex-shrink-0">
                <button @click="showVersionModal = false" class="flex-1 py-3 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-sm font-black transition-colors">取消</button>
                <button @click="handleSaveVersion" class="flex-1 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm font-black shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95">保存</button>
            </div>
        </div>
    </div>

    <!-- ======== PATCH MODAL ======== -->
    <div v-if="showPatchModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-md transition-all p-4">
        <div class="glass-card w-full max-w-lg p-6 sm:p-8 relative overflow-hidden shadow-2xl max-h-[90vh] flex flex-col">
            <h3 class="text-xl font-black text-slate-800 dark:text-white mb-6 flex-shrink-0">
                {{ editingPatch ? '编辑热修复补丁' : '新增热修复补丁' }}
            </h3>
            
            <div class="space-y-4 overflow-y-auto flex-1 pr-2 custom-scrollbar">
                <div class="grid grid-cols-2 gap-4">
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">基准版本号 (目标受众) *</label>
                        <input v-model="patchForm.target_version_code" type="number" placeholder="如修复 v2 的 bug，此处填 2" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none">
                    </div>
                    <div class="space-y-1">
                        <label class="text-xs font-bold text-slate-500 ml-1">补丁版本号 (递增) *</label>
                        <input v-model="patchForm.patch_version" type="number" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none">
                    </div>
                </div>
                
                <div class="space-y-1">
                    <label class="text-xs font-bold text-slate-500 ml-1">补丁下载地址 (URL) *</label>
                    <input v-model="patchForm.download_url" type="text" placeholder="https://..." class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none">
                </div>
                
                <div class="space-y-1">
                    <label class="text-xs font-bold text-slate-500 ml-1">MD5 校验值 (可选)</label>
                    <input v-model="patchForm.md5" type="text" placeholder="建议填写，防止下载损坏" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none">
                </div>

                <div class="space-y-1">
                    <label class="text-xs font-bold text-slate-500 ml-1">生效状态</label>
                    <select v-model="patchForm.status" class="w-full px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:outline-none">
                        <option :value="1">分发生效</option>
                        <option :value="0">停止分发</option>
                    </select>
                </div>
            </div>

            <div class="flex gap-3 mt-6 pt-4 border-t border-slate-200 dark:border-slate-700 flex-shrink-0">
                <button @click="showPatchModal = false" class="flex-1 py-3 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-sm font-black transition-colors">取消</button>
                <button @click="handleSavePatch" class="flex-1 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm font-black shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95">保存</button>
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
