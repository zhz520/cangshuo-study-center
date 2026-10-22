<script setup>
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
<div class="p-6 space-y-6">
    <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
            <h2 class="text-2xl font-black text-slate-800 dark:text-white tracking-tight">题库结构</h2>
             <p class="text-sm text-slate-500 dark:text-slate-400 font-medium">配置与管理专业分类及科目</p>
        </div>
    </div>

    <div class="space-y-8">
        <!-- Majors Panel -->
        <div class="glass-card flex flex-col overflow-hidden animate-slide-up">
            <div class="p-6 border-b border-slate-100 dark:border-slate-700/50 flex justify-between items-center bg-slate-50/50 dark:bg-slate-800/30">
                <div class="flex items-center gap-3">
                   <div class="w-8 h-8 rounded-lg bg-indigo-100 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 flex items-center justify-center text-lg">📂</div>
                   <div>
                       <h3 class="font-bold text-slate-800 dark:text-slate-100">专业大类</h3>
                       <p class="text-[10px] text-slate-400 font-bold uppercase tracking-widest mt-0.5">Total {{ majors.length }} Sectors</p>
                   </div>
                </div>
                <button @click="openAddMajor" class="group relative px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95 overflow-hidden">
                     <span class="relative font-bold text-xs flex items-center gap-1.5">
                        <span>+</span> 新增专业
                    </span>
                </button>
            </div>
            
            <div class="p-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                <div v-if="loading && majors.length === 0" class="col-span-full py-12 text-center flex flex-col items-center justify-center text-slate-400 animate-pulse">
                    <div class="w-8 h-8 border-2 border-slate-200 border-t-indigo-500 rounded-full animate-spin mb-2"></div>
                    <span class="text-xs font-bold">LOADING...</span>
                </div>
                
                <div 
                    v-for="m in majors" 
                    :key="m.id"
                    @click="selectMajor(m)"
                    class="p-4 rounded-2xl cursor-pointer flex justify-between items-center group transition-all border relative overflow-hidden"
                    :class="selectedMajor?.id === m.id ? 'bg-indigo-600 text-white border-indigo-600 shadow-xl shadow-indigo-200/50 dark:shadow-none' : 'bg-white dark:bg-slate-800/50 border-slate-100 dark:border-slate-700 hover:border-indigo-300 dark:hover:border-indigo-500/50 hover:shadow-md'"
                >
                     <!-- Active/Hover Decorator -->
                     <div v-if="selectedMajor?.id === m.id" class="absolute -right-4 -top-4 w-16 h-16 bg-white/10 rounded-full blur-xl pointer-events-none"></div>

                    <div class="flex items-center gap-3 relative z-10">
                        <span class="w-10 h-10 rounded-xl flex items-center justify-center text-base transition-colors"
                            :class="selectedMajor?.id === m.id ? 'bg-white/20 text-white' : 'bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-300 group-hover:bg-indigo-50 dark:group-hover:bg-indigo-900/20 group-hover:text-indigo-600 dark:group-hover:text-indigo-400'"
                        >
                            {{ m.name.charAt(0) }}
                        </span>
                        <span class="font-bold text-sm truncate max-w-[120px]">{{ m.name }}</span>
                    </div>
                    
                    <button 
                        @click.stop="deleteMajor(m.id)"
                        class="w-8 h-8 flex items-center justify-center rounded-lg transition-all opacity-0 lg:group-hover:opacity-100 group-active:opacity-100 relative z-10"
                        :class="selectedMajor?.id === m.id ? 'text-white/70 hover:bg-white/20 hover:text-white' : 'text-slate-300 hover:bg-rose-50 dark:hover:bg-rose-900/20 hover:text-rose-500'"
                         title="删除专业"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                    </button>
                    
                    <!-- Selection Indicator for Mobile/Desktop -->
                    <div v-if="selectedMajor?.id === m.id" class="absolute bottom-0 left-1/2 -translate-x-1/2 w-1.5 h-1.5 bg-white rounded-full mb-1 lg:hidden"></div>
                </div>
            </div>
        </div>

        <!-- Subjects Panel (Conditional) -->
        <transition name="fade-slide" mode="out-in">
            <div v-if="selectedMajor" class="glass-card flex flex-col overflow-hidden" :key="selectedMajor.id">
                 <div class="p-6 border-b border-slate-100 dark:border-slate-700/50 flex justify-between items-center bg-slate-50/50 dark:bg-slate-800/30">
                    <div class="flex items-center gap-3">
                       <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 text-white flex items-center justify-center font-bold shadow-lg shadow-indigo-200/50 dark:shadow-none">
                           {{ selectedMajor.name.charAt(0) }}
                       </div>
                       <div>
                           <h3 class="font-black text-slate-800 dark:text-slate-100 text-lg flex items-center gap-2">
                                {{ selectedMajor.name }} 
                               <span class="px-2 py-0.5 rounded-full bg-slate-100 dark:bg-slate-700 text-[10px] text-slate-500 dark:text-slate-400 font-bold uppercase tracking-wider">Major</span>
                           </h3>
                           <p class="text-[10px] text-slate-400 font-bold uppercase tracking-widest mt-0.5">Manage Subjects</p>
                       </div>
                    </div>
                    <button 
                        @click="openAddSubject" 
                        class="px-5 py-2.5 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-300 rounded-xl font-black text-xs transition-all hover:bg-indigo-100 dark:hover:bg-indigo-900/40 active:scale-95 border border-indigo-100 dark:border-indigo-500/20"
                    >
                        + 新增科目
                    </button>
                </div>
                
                <div class="p-8 bg-slate-50/30 dark:bg-slate-900/20 min-h-[200px]">
                    <div v-if="subjects.length === 0" class="h-full flex flex-col items-center justify-center py-10 text-center border-2 border-dashed border-slate-200 dark:border-slate-700 rounded-2xl">
                        <div class="text-4xl mb-3 opacity-50">📭</div>
                        <p class="text-slate-400 font-bold text-sm">暂无科目数据</p>
                        <p class="text-xs text-slate-400 mt-1">点击上方按钮添加第一个科目</p>
                    </div>
                    
                    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                        <div 
                            v-for="s in subjects" 
                            :key="s.id"
                            class="group bg-white dark:bg-slate-800 p-5 rounded-2xl border border-slate-100 dark:border-slate-700 hover:border-indigo-200 dark:hover:border-indigo-500/30 hover:shadow-lg hover:-translate-y-1 transition-all duration-300 relative overflow-hidden"
                        >
                             <!-- Hover bg -->
                             <div class="absolute inset-0 bg-gradient-to-br from-indigo-50/50 to-purple-50/50 dark:from-indigo-900/10 dark:to-purple-900/10 opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>

                            <div class="relative z-10 flex justify-between items-start">
                                <div class="flex items-center gap-3">
                                    <div class="w-10 h-10 rounded-lg bg-slate-50 dark:bg-slate-700/50 flex items-center justify-center text-xl group-hover:scale-110 transition-transform duration-300">🎓</div>
                                    <div>
                                        <div class="font-bold text-slate-700 dark:text-slate-200 text-sm mb-0.5">{{ s.name }}</div>
                                        <div class="text-[10px] text-slate-400 font-mono">ID: {{ s.id }}</div>
                                    </div>
                                </div>
                                <button 
                                    @click="deleteSubject(s.id)"
                                    class="w-8 h-8 flex items-center justify-center rounded-lg text-rose-300 hover:bg-rose-50 dark:hover:bg-rose-900/20 hover:text-rose-500 transition-all opacity-0 lg:group-hover:opacity-100 group-active:opacity-100"
                                     title="删除科目"
                                >
                                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            
            <div v-else class="glass-card py-20 px-6 text-center flex flex-col items-center justify-center animate-pulse-slow">
                <div class="bg-indigo-50 dark:bg-indigo-900/20 text-indigo-500 w-16 h-16 rounded-full flex items-center justify-center text-3xl mb-4">👆</div>
                <h3 class="text-lg font-black text-slate-700 dark:text-white mb-2">请先选择一个专业</h3>
                <p class="text-sm text-slate-400 dark:text-slate-500 max-w-xs mx-auto">点击上方的专业卡片，查看并管理其下属的所有科目。</p>
            </div>
        </transition>
    </div>

    <!-- Modals -->
    <div v-if="showMajorModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-md p-4 transition-all animate-fade-in">
        <div class="glass-card w-full max-w-sm p-6 shadow-2xl animate-scale-up border border-white/50 dark:border-slate-600/50">
            <h3 class="text-lg font-black text-slate-800 dark:text-white mb-4">新增专业大类</h3>
            <div class="space-y-4">
                <div>
                     <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5 ml-1">专业名称</label>
                    <input v-model="majorForm.name" placeholder="例如: 计算机类" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all">
                </div>
            </div>
            <div class="flex gap-3 mt-6">
                <button @click="showMajorModal = false" class="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs font-black transition-colors">取消</button>
                <button @click="saveMajor" class="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-black shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95">确认添加</button>
            </div>
        </div>
    </div>

    <div v-if="showSubjectModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-md p-4 transition-all animate-fade-in">
        <div class="glass-card w-full max-w-sm p-6 shadow-2xl animate-scale-up border border-white/50 dark:border-slate-600/50">
            <h3 class="text-lg font-black text-slate-800 dark:text-white mb-1">新增科目</h3>
            <p class="text-xs text-slate-500 dark:text-slate-400 mb-6 font-medium">
                所属专业：<span class="text-indigo-600 dark:text-indigo-400 font-bold">{{ selectedMajor?.name }}</span>
            </p>
            <div class="space-y-4">
                <div>
                     <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5 ml-1">科目名称</label>
                    <input v-model="subjectForm.name" placeholder="例如: 计算机基础" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all">
                </div>
            </div>
             <div class="flex gap-3 mt-6">
                <button @click="showSubjectModal = false" class="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs font-black transition-colors">取消</button>
                <button @click="saveSubject" class="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-black shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95">确认添加</button>
            </div>
        </div>
    </div>

    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialog" />
</div>
</template>
