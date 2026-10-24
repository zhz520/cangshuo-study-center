<script setup>
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';
import { ref, reactive, onMounted } from 'vue';
import { admin } from '../../api';
import Toast from '../../components/ui/Toast.vue';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';

const loading = ref(false);
const toast = ref(null);

// Data
const majors = ref([]);
const subjects = ref([]);
const selectedMajor = ref(null);

// Modal State
const showMajorModal = ref(false);
const showSubjectModal = ref(false);

const majorForm = reactive({ name: '' });
const subjectForm = reactive({ name: '' });

// Confirmation
const confirmDialog = ref(null);

const showMessage = (msg, type = 'success') => toast.value?.add(msg, type);

const loadMajors = async () => {
    loading.value = true;
    try {
        const res = await admin.getMajors();
        majors.value = res.data;
        if (selectedMajor.value) {
            // refresh selected
            const stillExists = majors.value.find(m => m.id === selectedMajor.value.id);
            if (stillExists) selectMajor(stillExists);
            else selectedMajor.value = null;
        }
    } catch (e) { showMessage('加载专业失败', 'error'); }
    finally { loading.value = false; }
};

const selectMajor = async (major) => {
    selectedMajor.value = major;
    try {
        const res = await admin.getSubjects(major.id);
        subjects.value = res.data;
    } catch (e) { showMessage('加载科目失败', 'error'); }
};

// Major Actions
const openAddMajor = () => {
    majorForm.name = '';
    showMajorModal.value = true;
};

const saveMajor = async () => {
    if (!majorForm.name.trim()) return showMessage('请输入专业名称', 'warning');
    try {
        await admin.addMajor({ name: majorForm.name });
        showMessage('添加专业成功');
        showMajorModal.value = false;
        loadMajors();
    } catch (e) { showMessage('添加失败: ' + (e.response?.data?.error || e.message), 'error'); }
};

const deleteMajor = async (id) => {
    const confirmed = await confirmDialog.value.confirm({
        title: '删除专业',
        message: '确定删除该专业及其所有科目和题目吗？慎重！',
        type: 'danger',
        confirmText: '删除',
        cancelText: '取消'
    });
    if (!confirmed) return;
    try {
        await admin.deleteMajor(id);
        showMessage('删除成功');
        loadMajors();
    } catch (e) { showMessage('删除失败', 'error'); }
};

// Subject Actions
const openAddSubject = () => {
    if (!selectedMajor.value) return;
    subjectForm.name = '';
    showSubjectModal.value = true;
};

const saveSubject = async () => {
    if (!subjectForm.name.trim()) return showMessage('请输入科目名称', 'warning');
    try {
        await admin.addSubject({
            major_id: selectedMajor.value.id,
            name: subjectForm.name
        });
        showMessage('添加科目成功');
        showSubjectModal.value = false;
        selectMajor(selectedMajor.value); // Reload subjects
    } catch (e) { showMessage('添加失败', 'error'); }
};

const deleteSubject = async (id) => {
    const confirmed = await confirmDialog.value.confirm({
        title: '删除科目',
        message: '确定删除该科目及其所有题目吗？',
        type: 'danger',
        confirmText: '删除',
        cancelText: '取消'
    });
    if (!confirmed) return;
    try {
        await admin.deleteSubject(id);
        showMessage('删除成功');
        selectMajor(selectedMajor.value);
    } catch (e) { showMessage('删除失败', 'error'); }
};

onMounted(() => {
    loadMajors();
});
</script>

<template>
<div class="admin-workspace ui-page admin-stack">
    <div class="admin-page-bar">
        <AdminPageHeading title="知识结构" description="配置专业分类与科目层级。" />
    </div>

    <div class="space-y-5">
        <!-- Majors Panel -->
        <div class="ui-card flex flex-col overflow-hidden animate-slide-up">
            <div class="admin-table-toolbar">
                <div><strong>专业大类</strong><span>{{ majors.length }} 个专业</span></div>
                <button @click="openAddMajor" class="ui-btn ui-btn-outline ui-btn-sm admin-control" type="button">新增专业</button>
            </div>

            <div class="admin-structure-grid">
                <div v-if="loading && majors.length === 0" class="col-span-full admin-empty">
                    <span class="ui-spinner"></span>
                    <span class="admin-empty-title">正在加载专业</span>
                </div>

                <div
                    v-for="m in majors"
                    :key="m.id"
                    @click="selectMajor(m)"
                    class="admin-structure-major group"
                    :class="{ 'is-selected': selectedMajor?.id === m.id }"
                >
                    <div class="flex items-center gap-3 relative z-10">
                        <span class="admin-structure-index">{{ String(majors.indexOf(m) + 1).padStart(2, '0') }}</span>
                        <span class="font-bold text-sm truncate max-w-[120px]">{{ m.name }}</span>
                    </div>

                    <button
                        @click.stop="deleteMajor(m.id)"
                        class="w-8 h-8 flex items-center justify-center rounded-lg transition-all opacity-0 lg:group-hover:opacity-100 group-active:opacity-100 relative z-10 admin-control"
                        :class="selectedMajor?.id === m.id ? 'text-slate-400 hover:bg-white/50 hover:text-rose-500' : 'text-slate-300 hover:bg-rose-50 dark:hover:bg-rose-900/20 hover:text-rose-500'"
                         title="删除专业"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                    </button>

                </div>
            </div>
        </div>

        <!-- Subjects Panel (Conditional) -->
        <transition name="fade-slide" mode="out-in">
            <div v-if="selectedMajor" class="ui-card flex flex-col overflow-hidden" :key="selectedMajor.id">
                 <div class="p-6 border-b border-slate-100 dark:border-slate-700/50 flex justify-between items-center bg-slate-50/50 dark:bg-slate-800/30">
                    <div class="flex items-center gap-3">
                       <span class="admin-structure-index is-panel">{{ selectedMajor.name.charAt(0) }}</span>
                       <div>
                           <h3 class="text-slate-800 dark:text-slate-100 text-lg flex items-center gap-2">
                                {{ selectedMajor.name }}
                               <span class="px-2 py-0.5 rounded-full bg-slate-100 dark:bg-slate-700 text-[10px] text-slate-500 dark:text-slate-400 font-bold uppercase">Major</span>
                           </h3>
                           <p class="text-[10px] text-slate-400 font-bold uppercase mt-0.5">Manage Subjects</p>
                       </div>
                    </div>
                    <button
                        @click="openAddSubject"
                        class="px-5 py-2.5 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-300 rounded-xl text-xs transition-all hover:bg-indigo-100 dark:hover:bg-indigo-900/40 border border-indigo-100 dark:border-indigo-500/20 admin-control"
                    >
                        + 新增科目
                    </button>
                </div>

                <div class="p-5 bg-slate-50/30 dark:bg-slate-900/20 min-h-[200px]">
                    <div v-if="subjects.length === 0" class="h-full flex flex-col items-center justify-center py-10 text-center border-2 border-dashed border-slate-200 dark:border-slate-700 rounded-xl">

                        <p class="text-slate-400 font-bold text-sm">暂无科目数据</p>
                        <p class="text-xs text-slate-400 mt-1">点击上方按钮添加第一个科目</p>
                    </div>

                    <div class="admin-subject-grid">
                        <div
                            v-for="s in subjects"
                            :key="s.id"
                            class="admin-subject-card"
                        >
                            <div class="relative z-10 flex justify-between items-start">
                                <div class="flex items-center gap-3">
                                    <span class="admin-subject-mark">{{ s.name.slice(0, 1) }}</span>
                                    <div>
                                        <div class="font-bold text-slate-700 dark:text-slate-200 text-sm mb-0.5">{{ s.name }}</div>
                                        <div class="text-[10px] text-slate-400 font-mono">ID: {{ s.id }}</div>
                                    </div>
                                </div>
                                <button
                                    @click="deleteSubject(s.id)"
                                    class="admin-text-action danger"
                                     title="删除科目"
                                >
                                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div v-else class="admin-table-card">
                <div class="admin-empty">
                    <span class="admin-empty-title">选择一个专业</span>
                    <span class="admin-empty-copy">点击上方专业，查看并管理下属科目。</span>
                </div>
            </div>
        </transition>
    </div>

    <!-- Modals -->
    <div v-if="showMajorModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 transition-all animate-fade-in">
        <div class="ui-card w-full max-w-sm p-6 animate-scale-up border border-white/50 dark:border-slate-600/50">
            <h3 class="text-lg text-slate-800 dark:text-white mb-4">新增专业大类</h3>
            <div class="space-y-4">
                <div>
                     <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5 ml-1">专业名称</label>
                    <input v-model="majorForm.name" placeholder="例如: 计算机类" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-input">
                </div>
            </div>
            <div class="flex gap-3 mt-6">
                <button @click="showMajorModal = false" class="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs transition-colors admin-control">取消</button>
                <button @click="saveMajor" class="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs dark:shadow-none transition-all admin-control">确认添加</button>
            </div>
        </div>
    </div>

    <div v-if="showSubjectModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 transition-all animate-fade-in">
        <div class="ui-card w-full max-w-sm p-6 animate-scale-up border border-white/50 dark:border-slate-600/50">
            <h3 class="text-lg text-slate-800 dark:text-white mb-1">新增科目</h3>
            <p class="text-xs text-slate-500 dark:text-slate-400 mb-6 font-medium">
                所属专业：<span class="text-indigo-600 dark:text-indigo-400 font-bold">{{ selectedMajor?.name }}</span>
            </p>
            <div class="space-y-4">
                <div>
                     <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5 ml-1">科目名称</label>
                    <input v-model="subjectForm.name" placeholder="例如: 计算机基础" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-input">
                </div>
            </div>
             <div class="flex gap-3 mt-6">
                <button @click="showSubjectModal = false" class="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs transition-colors admin-control">取消</button>
                <button @click="saveSubject" class="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs dark:shadow-none transition-all admin-control">确认添加</button>
            </div>
        </div>
    </div>

    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialog" />
</div>
</template>
