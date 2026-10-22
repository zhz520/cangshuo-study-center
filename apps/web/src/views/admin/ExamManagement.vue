<template>
  <div class="p-6 space-y-6">
    <!-- Header -->
    <div class="flex justify-between items-center">
      <div>
        <h2 class="text-2xl font-black text-slate-800 dark:text-white tracking-tight">考试系统管理</h2>
        <p class="text-sm text-slate-500 dark:text-slate-400 font-medium">监控考试记录并管理用户重考权限</p>
      </div>
    </div>

    <!-- 试卷列表 Table -->
    <div class="glass-card overflow-hidden">
      <div class="px-8 py-5 border-b border-slate-200/60 dark:border-slate-700/60 flex justify-between items-center bg-slate-50/50 dark:bg-slate-800/30">
        <h3 class="text-sm font-black text-slate-700 dark:text-slate-200 flex items-center gap-2">
          <span>📋</span> 试卷列表
        </h3>
      </div>
      
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="text-xs text-slate-500 dark:text-slate-400 uppercase tracking-wider font-bold">
              <th class="px-8 py-4">ID</th>
              <th class="px-8 py-4">试卷名称</th>
              <th class="px-8 py-4">题目与分值</th>
              <th class="px-8 py-4">时长</th>
              <th class="px-8 py-4">创建时间</th>
              <th class="px-8 py-4">状态</th>
              <th class="px-8 py-4 text-right">管理</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-700/50">
            <tr v-if="loadingPapers" class="animate-pulse">
              <td colspan="7" class="px-8 py-10 text-center text-slate-400 font-bold text-sm">加载中...</td>
            </tr>
            <tr v-else-if="papers.length === 0" class="px-8 py-5">
              <td colspan="7" class="px-8 py-10 text-center text-slate-400 font-bold text-sm">暂无试卷数据</td>
            </tr>
            <tr v-for="paper in papers" :key="paper.id" class="group hover:bg-indigo-50/30 dark:hover:bg-indigo-900/10 transition-colors">
              <td class="px-8 py-5 text-mono text-xs font-bold text-slate-400">#{{ paper.id }}</td>
              <td class="px-8 py-5">
                <div class="font-bold text-slate-700 dark:text-slate-200">{{ paper.title }}</div>
              </td>
              <td class="px-8 py-5">
                <div class="flex flex-col gap-1">
                  <span class="text-xs font-bold text-indigo-600 dark:text-indigo-400">{{ paper.question_count }} 题</span>
                  <span class="text-[10px] text-slate-400 font-bold uppercase tracking-tighter">满分 {{ paper.total_score }} 分</span>
                </div>
              </td>
              <td class="px-8 py-5 text-sm font-bold text-slate-600 dark:text-slate-300">
                {{ paper.duration_minutes }} 分钟
              </td>
              <td class="px-8 py-5">
                <div class="text-xs text-slate-500">
                  {{ new Date(paper.created_at).toLocaleDateString() }}
                </div>
              </td>
              <td class="px-8 py-5">
                 <button 
                    @click="togglePaperStatus(paper)" 
                    :disabled="statusLoading === paper.id"
                    class="px-2 py-1 rounded text-[10px] font-black uppercase tracking-wider transition-all active:scale-95"
                    :class="paper.is_active ? 'bg-green-100 text-green-700 hover:bg-green-200' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'"
                >
                    {{ statusLoading === paper.id ? '更新中...' : (paper.is_active ? '已上线' : '已下线') }}
                </button>
              </td>
              <td class="px-8 py-5 text-right">
                <button @click="viewRecords(paper)" class="px-4 py-2 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 rounded-xl text-xs font-black hover:bg-indigo-100 transition-all mr-2">
                   查看记录
                 </button>
                 <button @click="viewPaperQuestions(paper)" class="px-4 py-2 bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs font-black hover:bg-slate-200 transition-all">
                   题目管理
                 </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Records Sidebar/Modal Overlay -->
    <div v-if="drawerVisible" class="fixed inset-0 z-50 flex justify-end bg-slate-900/40 backdrop-blur-md transition-all animate-fade-in">
        <div class="bg-white dark:bg-slate-800 w-full max-w-4xl h-full shadow-2xl flex flex-col animate-slide-left border-l border-slate-200 dark:border-slate-700">
            <div class="p-8 border-b border-slate-100 dark:border-slate-700 flex justify-between items-center">
                <div>
                  <h3 class="text-xl font-black text-slate-800 dark:text-white">考试记录</h3>
                  <p class="text-xs text-slate-500 font-bold">{{ currentPaper?.title }}</p>
                </div>
                <button @click="drawerVisible = false" class="w-10 h-10 rounded-full bg-slate-100 dark:bg-slate-700 flex items-center justify-center hover:bg-slate-200 text-slate-400 transition-colors">✕</button>
            </div>
            
            <div class="flex-1 overflow-y-auto p-8 pt-4">
                <table class="w-full text-left border-collapse">
                  <thead>
                    <tr class="text-[10px] text-slate-400 uppercase tracking-widest font-black border-b border-slate-100 dark:border-slate-700">
                      <th class="py-4">考生</th>
                      <th class="py-4 text-center">状态</th>
                      <th class="py-4 text-center">得分</th>
                      <th class="py-4 text-center">开始时间</th>
                      <th class="py-4 text-right">允许补考</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-50 dark:divide-slate-700/50">
                    <tr v-if="loadingRecords" class="animate-pulse">
                      <td colspan="5" class="py-10 text-center text-slate-300 font-black text-xs">加载记录中...</td>
                    </tr>
                    <tr v-else-if="records.length === 0">
                      <td colspan="5" class="py-10 text-center text-slate-300 font-black text-xs">暂时没有考生参加该场考试</td>
                    </tr>
                    <tr v-for="record in records" :key="record.id" class="text-sm font-medium">
                      <td class="py-4">
                        <div class="font-bold text-slate-700 dark:text-slate-200">{{ record.username }}</div>
                      </td>
                      <td class="py-4 text-center">
                        <span :class="getStatusClass(record.status)" class="px-2 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider">
                          {{ getStatusText(record.status) }}
                        </span>
                      </td>
                      <td class="py-4 text-center">
                        <span :class="getScoreClass(record)" class="font-bold">
                          {{ record.status === 'abandoned' ? 0 : record.score }}
                        </span>
                      </td>
                      <td class="py-4 text-center text-xs text-slate-400">
                        {{ new Date(record.start_time).toLocaleString() }}
                      </td>
                      <td class="py-4 text-right">
                        <div class="flex justify-end items-center gap-3">
                          <button 
                            @click="handleExportRecord(record)" 
                            :disabled="exportLoading === record.id"
                            class="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors text-slate-400 hover:text-indigo-600"
                            title="导出为 Word"
                          >
                            <span v-if="exportLoading === record.id" class="animate-spin inline-block">⏳</span>
                            <span v-else>📄</span>
                          </button>
                          <label class="relative inline-flex items-center cursor-pointer" :disabled="record.status === 'in_progress'">
                            <input type="checkbox" v-model="record.can_retry" @change="(e) => handleRetryChange(record, e.target.checked)" class="sr-only peer" :disabled="record.status === 'in_progress'">
                            <div class="w-9 h-5 bg-slate-200 dark:bg-slate-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-indigo-600 disabled:opacity-50"></div>
                          </label>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>

                <div class="mt-8 flex justify-between items-center" v-if="totalRecords > 0">
                    <span class="text-xs text-slate-400 font-bold">共 {{ totalRecords }} 条记录</span>
                    <div class="flex gap-2">
                        <button 
                          @click="changePage(recordPage - 1)" 
                          :disabled="recordPage <= 1"
                          class="px-4 py-2 bg-slate-50 dark:bg-slate-700 rounded-lg text-[10px] font-black uppercase tracking-wider disabled:opacity-30 hover:bg-slate-100 transition-colors"
                        >Prev</button>
                        <button 
                          @click="changePage(recordPage + 1)" 
                          :disabled="recordPage * 10 >= totalRecords"
                          class="px-4 py-2 bg-slate-50 dark:bg-slate-700 rounded-lg text-[10px] font-black uppercase tracking-wider disabled:opacity-30 hover:bg-slate-100 transition-colors"
                        >Next</button>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Questions Management Drawer -->
    <div v-if="questionsDrawerVisible" class="fixed inset-0 z-50 flex justify-end bg-slate-900/40 backdrop-blur-md transition-all animate-fade-in">
        <div class="bg-white dark:bg-slate-800 w-full max-w-5xl h-full shadow-2xl flex flex-col animate-slide-left border-l border-slate-200 dark:border-slate-700">
            <div class="p-8 border-b border-slate-100 dark:border-slate-700 flex justify-between items-center bg-slate-50/50 dark:bg-slate-800/30">
                <div>
                   <h3 class="text-xl font-black text-slate-800 dark:text-white flex items-center gap-2">
                       <span>📝</span> 题目管理
                   </h3>
                   <p class="text-xs text-slate-500 font-bold uppercase tracking-wider">{{ currentPaper?.title }}</p>
                </div>
                <div class="flex items-center gap-3">
                    <button @click="openAddPaperQuestion" class="px-4 py-2 bg-indigo-600 text-white rounded-xl text-xs font-black shadow-lg shadow-indigo-600/20 hover:scale-105 transition-all">
                      + 添加题目
                    </button>
                    <button @click="questionsDrawerVisible = false" class="w-10 h-10 rounded-full bg-white dark:bg-slate-700 flex items-center justify-center hover:bg-slate-50 text-slate-400 transition-colors shadow-sm">✕</button>
                </div>
            </div>
            
            <div class="flex-1 overflow-y-auto p-8 custom-scrollbar">
                <div v-if="loadingPaperQuestions" class="py-20 text-center animate-pulse">
                    <div class="w-10 h-10 border-4 border-slate-200 border-t-indigo-500 rounded-full animate-spin mx-auto mb-4"></div>
                    <p class="text-slate-400 font-bold text-xs uppercase tracking-widest">加载题目中...</p>
                </div>
                <div v-else-if="paperQuestions.length === 0" class="py-20 text-center border-2 border-dashed border-slate-100 dark:border-slate-700 rounded-3xl">
                    <p class="text-slate-400 font-bold text-sm">此试卷暂无题目</p>
                </div>
                <div v-else class="space-y-4">
                    <div v-for="q in paperQuestions" :key="q.id" class="p-5 bg-slate-50 dark:bg-slate-800/50 rounded-2xl border border-slate-100 dark:border-white/5 group hover:border-indigo-200 transition-all">
                        <div class="flex justify-between items-start gap-4">
                            <div class="flex-1 min-w-0">
                                <div class="flex items-center gap-2 mb-2">
                                    <span class="w-6 h-6 flex items-center justify-center bg-indigo-600 text-white rounded-lg font-black text-[10px]">{{ q.question_index }}</span>
                                    <span class="px-2 py-0.5 bg-indigo-50 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 rounded text-[10px] font-black uppercase tracking-wider">{{ q.type }}</span>
                                    <span class="text-[10px] font-bold text-slate-400">ID: {{ q.id }}</span>
                                </div>
                                <h4 class="text-sm font-bold text-slate-700 dark:text-slate-200 mb-3 leading-relaxed line-clamp-2">{{ q.content }}</h4>
                                <div class="flex items-center gap-4 text-[10px] font-bold">
                                    <span class="text-indigo-600 dark:text-indigo-400">分值: {{ q.score }}</span>
                                    <span :class="q.is_active ? 'text-green-500' : 'text-slate-400'">{{ q.is_active ? '● 已上线' : '○ 已下线' }}</span>
                                </div>
                            </div>
                            <div class="flex gap-2">
                                <button @click="toggleQuestionStatus(q)" class="p-2 bg-white dark:bg-slate-700 rounded-lg shadow-sm hover:text-indigo-600 transition-colors" :title="q.is_active ? '停用' : '启用'">
                                   {{ q.is_active ? '🚫' : '✅' }}
                                </button>
                                <button @click="openEditPaperQuestion(q)" class="p-2 bg-white dark:bg-slate-700 rounded-lg shadow-sm hover:text-indigo-600 transition-colors" title="编辑">
                                   ✏️
                                </button>
                                <button @click="deletePaperQuestion(q.id)" class="p-2 bg-white dark:bg-slate-700 rounded-lg shadow-sm hover:text-rose-600 transition-colors" title="删除">
                                   🗑️
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Paper Question Edit Modal -->
    <div v-if="showPaperQuestionModal" class="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
        <div class="bg-white dark:bg-slate-800 w-full max-w-2xl rounded-3xl shadow-2xl flex flex-col border border-white/20">
            <div class="px-8 py-5 border-b border-slate-100 dark:border-slate-700 flex justify-between items-center">
                <h3 class="text-lg font-black text-slate-800 dark:text-white">{{ editingPaperQuestion.id ? '编辑试卷题目' : '添加试卷题目' }}</h3>
                <button @click="showPaperQuestionModal = false" class="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 text-xl font-bold">×</button>
            </div>
            <div class="p-8 space-y-5">
                <div class="grid grid-cols-2 gap-4">
                    <div class="space-y-1.5">
                        <label class="text-xs font-bold text-slate-500 ml-1">题目索引 (排序)</label>
                        <input type="number" v-model="editingPaperQuestion.question_index" class="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-700/50 border border-slate-100 dark:border-slate-700 rounded-xl text-sm font-bold focus:ring-2 focus:ring-indigo-500/20 outline-none">
                    </div>
                    <div class="space-y-1.5">
                        <label class="text-xs font-bold text-slate-500 ml-1">题目分值</label>
                        <input type="number" v-model="editingPaperQuestion.score" class="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-700/50 border border-slate-100 dark:border-slate-700 rounded-xl text-sm font-bold focus:ring-2 focus:ring-indigo-500/20 outline-none">
                    </div>
                </div>
                <div class="space-y-1.5">
                    <label class="text-xs font-bold text-slate-500 ml-1">题目类型</label>
                    <select v-model="editingPaperQuestion.type" class="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-700/50 border border-slate-100 dark:border-slate-700 rounded-xl text-sm font-bold focus:ring-2 focus:ring-indigo-500/20 outline-none">
                        <option value="single">单选题</option>
                        <option value="multiple">多选题</option>
                        <option value="judge">判断题</option>
                        <option value="fill">填空题</option>
                    </select>
                </div>
                <div class="space-y-1.5">
                    <label class="text-xs font-bold text-slate-500 ml-1">题目内容</label>
                    <textarea v-model="editingPaperQuestion.content" rows="3" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700/50 border border-slate-100 dark:border-slate-700 rounded-xl text-sm font-medium focus:ring-2 focus:ring-indigo-500/20 outline-none resize-none" placeholder="题干内容..."></textarea>
                </div>

                <!-- Options -->
                <div v-if="editingPaperQuestion.type !== 'fill'" class="space-y-3">
                    <label class="text-xs font-bold text-slate-500 ml-1">选项设置</label>
                    <div v-for="(opt, idx) in editingPaperQuestion.options" :key="idx" class="flex items-center gap-3">
                        <span class="w-8 h-8 flex items-center justify-center bg-slate-100 dark:bg-slate-700 rounded-lg text-xs font-black">{{ opt.label }}</span>
                        <input type="text" v-model="opt.content" class="flex-1 px-4 py-2 bg-slate-50 dark:bg-slate-700/50 border border-slate-100 dark:border-slate-700 rounded-xl text-sm font-bold focus:ring-2 focus:ring-indigo-500/20 outline-none" placeholder="选项内容...">
                        <button v-if="editingPaperQuestion.options.length > 2" @click="editingPaperQuestion.options.splice(idx, 1)" class="text-rose-500 hover:text-rose-600">✕</button>
                    </div>
                    <button @click="addOption" class="text-[10px] font-black text-indigo-600 uppercase tracking-widest">+ 添加选项</button>
                </div>

                <!-- Answer -->
                <div class="space-y-1.5">
                    <label class="text-xs font-bold text-slate-500 ml-1">正确答案</label>
                    <div v-if="editingPaperQuestion.type === 'single' || editingPaperQuestion.type === 'judge'" class="flex flex-wrap gap-4 px-1">
                        <label v-for="opt in editingPaperQuestion.options" :key="opt.label" class="flex items-center gap-2 cursor-pointer group">
                            <input type="radio" :value="opt.label" v-model="editingPaperQuestion.answer" class="w-4 h-4 text-indigo-600 focus:ring-indigo-500/20">
                            <span class="text-sm font-bold text-slate-600 group-hover:text-indigo-600">{{ opt.label }}</span>
                        </label>
                    </div>
                    <div v-else-if="editingPaperQuestion.type === 'multiple'" class="flex flex-wrap gap-4 px-1">
                        <label v-for="opt in editingPaperQuestion.options" :key="opt.label" class="flex items-center gap-2 cursor-pointer group">
                            <input type="checkbox" :value="opt.label" :checked="isOptionChecked(opt.label)" @change="toggleMultipleAnswer(opt.label)" class="w-4 h-4 text-indigo-600 rounded focus:ring-indigo-500/20">
                            <span class="text-sm font-bold text-slate-600 group-hover:text-indigo-600">{{ opt.label }}</span>
                        </label>
                    </div>
                    <input v-else type="text" v-model="editingPaperQuestion.answer" class="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-700/50 border border-slate-100 dark:border-slate-700 rounded-xl text-sm font-bold focus:ring-2 focus:ring-indigo-500/20 outline-none" placeholder="如果是填空题，请输入答案内容...">
                </div>

                <!-- Analysis -->
                <div class="space-y-1.5">
                    <label class="text-xs font-bold text-slate-500 ml-1">题目解析</label>
                    <textarea v-model="editingPaperQuestion.analysis" rows="2" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700/50 border border-slate-100 dark:border-slate-700 rounded-xl text-sm font-medium focus:ring-2 focus:ring-indigo-500/20 outline-none resize-none" placeholder="题目解析内容..."></textarea>
                </div>

                 <div class="flex items-center gap-3 py-2">
                    <label class="flex items-center gap-2 cursor-pointer select-none">
                        <input type="checkbox" v-model="editingPaperQuestion.is_active" :true-value="1" :false-value="0" class="w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500">
                        <span class="text-xs font-bold text-slate-600 dark:text-slate-300">上线状态</span>
                    </label>
                </div>
            </div>
            <div class="px-8 py-5 border-t border-slate-100 dark:border-slate-700 flex justify-end gap-3 bg-slate-50/50 dark:bg-slate-800/30">
                <button @click="showPaperQuestionModal = false" class="px-6 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-500 font-bold text-xs hover:bg-slate-100 transition-colors">取消</button>
                <button @click="savePaperQuestion" class="px-6 py-2.5 bg-indigo-600 text-white rounded-xl font-bold text-xs shadow-lg shadow-indigo-600/20 hover:scale-[1.02] active:scale-95 transition-all">保存题目</button>
            </div>
        </div>
    </div>

    <!-- Global Components -->
    <Toast ref="toast" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { admin } from '../../api';
import Toast from '../../components/ui/Toast.vue';
import { exportRecordToWord } from '../../utils/export-record-docx.js';

const papers = ref([]);
const loadingPapers = ref(false);
const toast = ref(null);

const drawerVisible = ref(false);
const currentPaper = ref(null);
const records = ref([]);
const loadingRecords = ref(false);
const recordPage = ref(1);
const totalRecords = ref(0);
const statusLoading = ref(null);
const exportLoading = ref(null);

// Paper Questions
const questionsDrawerVisible = ref(false);
const paperQuestions = ref([]);
const loadingPaperQuestions = ref(false);
const showPaperQuestionModal = ref(false);
const editingPaperQuestion = ref({
    id: null,
    question_index: 1,
    type: 'single',
    content: '',
    options: [],
    answer: '',
    analysis: '',
    score: 5,
    is_active: 1
});

const loadPapers = async () => {
  loadingPapers.value = true;
  try {
    const res = await admin.getExams();
    papers.value = res.data;
  } catch (err) {
    toast.value?.add(err.response?.data?.error || '加载试卷列表失败', 'error');
  } finally {
    loadingPapers.value = false;
  }
};

const viewRecords = (paper) => {
  currentPaper.value = paper;
  recordPage.value = 1;
  drawerVisible.value = true;
  loadRecords();
};

const loadRecords = async () => {
  if (!currentPaper.value) return;
  loadingRecords.value = true;
  try {
    const res = await admin.getExamRecords(currentPaper.value.id, {
      page: recordPage.value,
      limit: 10
    });
    records.value = res.data.records;
    totalRecords.value = res.data.total;
  } catch (err) {
    toast.value?.add(err.response?.data?.error || '加载考试记录失败', 'error');
  } finally {
    loadingRecords.value = false;
  }
};

const changePage = (newPage) => {
    recordPage.value = newPage;
    loadRecords();
};

const handleRetryChange = async (record, canRetry) => {
  try {
    await admin.updateExamRetryPermission(record.id, canRetry);
    toast.value?.add(`已${canRetry ? '允许' : '取消'}用户 ${record.username} 的重考权限`, 'success');
  } catch (err) {
    record.can_retry = !canRetry; // 恢复状态
    toast.value?.add(err.response?.data?.error || '修改失败', 'error');
  }
};

const handleExportRecord = async (record) => {
  if (exportLoading.value) return;
  exportLoading.value = record.id;
  try {
    const res = await admin.getExamRecordDetails(record.id);
    await exportRecordToWord(res.data);
    toast.value?.add('导出成功', 'success');
  } catch (err) {
    console.error('导出失败:', err);
    toast.value?.add(err.response?.data?.error || '导出失败', 'error');
  } finally {
    exportLoading.value = null;
  }
};

const togglePaperStatus = async (paper) => {
  const newStatus = paper.is_active ? 0 : 1;
  statusLoading.value = paper.id;
  try {
    await admin.updateExam(paper.id, { is_active: newStatus });
    paper.is_active = newStatus;
    toast.value?.add(`${paper.title} 已${newStatus ? '上线' : '下线'}`, 'success');
  } catch (err) {
    toast.value?.add(err.response?.data?.error || '更新状态失败', 'error');
  } finally {
    statusLoading.value = null;
  }
};

const viewPaperQuestions = (paper) => {
    currentPaper.value = paper;
    questionsDrawerVisible.value = true;
    loadPaperQuestions();
};

const loadPaperQuestions = async () => {
    if (!currentPaper.value) return;
    loadingPaperQuestions.value = true;
    try {
        const res = await admin.getExam(currentPaper.value.id);
        paperQuestions.value = res.data.questions;
    } catch (err) {
        toast.value?.add('加载题目失败', 'error');
    } finally {
        loadingPaperQuestions.value = false;
    }
};

const toggleQuestionStatus = async (q) => {
    const newStatus = q.is_active ? 0 : 1;
    try {
        await admin.updateExamQuestion(q.id, { is_active: newStatus });
        q.is_active = newStatus;
        toast.value?.add('状态更新成功', 'success');
    } catch (err) {
        toast.value?.add('更新失败', 'error');
    }
};

const openAddPaperQuestion = () => {
    editingPaperQuestion.value = {
        id: null,
        question_index: paperQuestions.value.length + 1,
        type: 'single',
        content: '',
        options: [
            { label: 'A', content: '' },
            { label: 'B', content: '' },
            { label: 'C', content: '' },
            { label: 'D', content: '' }
        ],
        answer: '',
        analysis: '',
        score: 5,
        is_active: 1
    };
    showPaperQuestionModal.value = true;
};

const openEditPaperQuestion = (q) => {
    editingPaperQuestion.value = { ...q };
    showPaperQuestionModal.value = true;
};

const deletePaperQuestion = async (id) => {
    if (!confirm('确定要从试卷中移除这道题目吗？')) return;
    try {
        await admin.deleteExamQuestion(id);
        toast.value?.add('删除成功', 'success');
        loadPaperQuestions();
    } catch (err) {
        toast.value?.add('删除失败', 'error');
    }
};

const savePaperQuestion = async () => {
    const paperId = currentPaper.value.id;
    try {
        const payload = { ...editingPaperQuestion.value };
        if (payload.id) {
            await admin.updateExamQuestion(payload.id, payload);
            toast.value?.add('更新成功', 'success');
        } else {
            await admin.addExamQuestion(paperId, payload);
            toast.value?.add('添加成功', 'success');
        }
        showPaperQuestionModal.value = false;
        loadPaperQuestions();
    } catch (err) {
        toast.value?.add('保存失败', 'error');
    }
};

const addOption = () => {
    const nextLabel = String.fromCharCode(65 + editingPaperQuestion.value.options.length); // A, B, C...
    editingPaperQuestion.value.options.push({ label: nextLabel, content: '' });
};

const isOptionChecked = (label) => {
    const answer = editingPaperQuestion.value.answer;
    if (typeof answer === 'string') {
        try {
            const parsed = JSON.parse(answer);
            return Array.isArray(parsed) && parsed.includes(label);
        } catch (e) {
            return answer.includes(label);
        }
    }
    return Array.isArray(answer) && answer.includes(label);
};

const toggleMultipleAnswer = (label) => {
    let current = [];
    const answer = editingPaperQuestion.value.answer;
    try {
        current = typeof answer === 'string' ? JSON.parse(answer) : (Array.isArray(answer) ? answer : []);
    } catch (e) {
        current = [];
    }
    
    if (current.includes(label)) {
        current = current.filter(l => l !== label);
    } else {
        current.push(label);
        current.sort();
    }
    editingPaperQuestion.value.answer = JSON.stringify(current);
};

const getStatusText = (status) => {
  switch (status) {
    case 'completed': return '已完成';
    case 'in_progress': return '考试中';
    case 'abandoned': return '已中止';
    default: return status;
  }
};

const getStatusClass = (status) => {
  switch (status) {
    case 'completed': return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-400';
    case 'in_progress': return 'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-400';
    case 'abandoned': return 'bg-rose-100 text-rose-700 dark:bg-rose-500/20 dark:text-rose-400';
    default: return 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400';
  }
};

const getScoreClass = (record) => {
  if (record.status === 'abandoned') return 'text-rose-500';
  if (record.passed) return 'text-emerald-600 dark:text-emerald-400';
  return 'text-slate-600 dark:text-slate-300';
};

onMounted(() => {
  loadPapers();
});
</script>

<style scoped>
.glass-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(226, 232, 240, 0.5);
  border-radius: 1.5rem;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
}

.dark .glass-card {
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid rgba(51, 65, 85, 0.5);
}

@keyframes slide-left {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}

.animate-slide-left {
  animation: slide-left 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.animate-fade-in {
  animation: fade-in 0.3s ease-out;
}
</style>
