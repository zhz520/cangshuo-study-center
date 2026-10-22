<script setup>
import { ref, reactive, onMounted } from "vue";
import { admin } from "../../api";
import Toast from "../../components/ui/Toast.vue";
import ConfirmDialog from "../../components/ui/ConfirmDialog.vue";

const reports = ref([]);
const loading = ref(false);
const toast = ref(null);
const confirmDialogRef = ref(null);

const pagination = reactive({
  page: 1,
  limit: 20,
  total: 0,
  totalPages: 1,
});

const filterStatus = ref('');

const fetchReports = async (page = 1) => {
  loading.value = true;
  try {
    const params = { page, limit: pagination.limit };
    if (filterStatus.value) {
      params.status = filterStatus.value;
    }
    const res = await admin.getReports(params);
    reports.value = res.data.data;
    pagination.page = res.data.page;
    pagination.total = res.data.total;
    pagination.totalPages = Math.ceil(res.data.total / pagination.limit) || 1;
  } catch (error) {
    toast.value?.add("获取报错列表失败", "error");
  } finally {
    loading.value = false;
  }
};

const handlePageChange = (newPage) => {
  if (newPage >= 1 && newPage <= pagination.totalPages) {
    fetchReports(newPage);
  }
};

const showConfirm = (title, content, type = "info") => {
  if (confirmDialogRef.value) {
    return confirmDialogRef.value.confirm({ title, message: content, type });
  }
  return Promise.resolve(false);
};

const updateStatus = async (id, newStatus) => {
  const label = newStatus === 'resolved' ? '标记为已处理' : '标记为已驳回';
  const confirmed = await showConfirm(`确定要${label}吗？`, "", "info");
  if (!confirmed) return;

  try {
    await admin.updateReportStatus(id, newStatus);
    toast.value?.add("状态已更新", "success");
    fetchReports(pagination.page);
  } catch (error) {
    toast.value?.add("状态更新失败", "error");
  }
};

onMounted(() => {
  fetchReports();
});
</script>

<template>
  <div class="px-3 py-4 sm:p-6 space-y-5">
    <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
      <div>
        <h2 class="text-2xl font-black text-slate-800 dark:text-white tracking-tight">题目报错管理</h2>
        <p class="text-sm text-slate-500 dark:text-slate-400 font-medium mt-1">处理用户提交的题目报错信息</p>
      </div>

      <div class="flex gap-2 w-full sm:w-auto">
        <select
          v-model="filterStatus"
          @change="fetchReports(1)"
          class="w-full sm:w-auto px-4 py-2 border bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
        >
          <option value="">全部状态</option>
          <option value="pending">待处理 (Pending)</option>
          <option value="resolved">已处理 (Resolved)</option>
          <option value="rejected">已驳回 (Rejected)</option>
        </select>
        <button
          @click="fetchReports(1)"
          class="px-4 py-2 bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 rounded-xl text-sm font-bold flex flex-row items-center gap-2 hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors"
        >
          刷新
        </button>
      </div>
    </div>

    <!-- Reports Table / Cards -->
    <div class="glass-card overflow-hidden relative min-h-[300px]">
      <div v-if="loading" class="absolute inset-0 bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm z-10 flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="border-b-2 border-slate-100 dark:border-slate-700 text-xs font-black text-slate-400 dark:text-slate-500 uppercase tracking-wider bg-slate-50/50 dark:bg-slate-800/50">
              <th class="p-4 rounded-tl-xl w-16">ID</th>
              <th class="p-4 w-32">用户</th>
              <th class="p-4 w-48">题目</th>
              <th class="p-4">报错内容</th>
              <th class="p-4 w-32 text-center">状态</th>
              <th class="p-4 w-40 text-center">时间</th>
              <th class="p-4 rounded-tr-xl w-32 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-700/50 text-sm">
            <tr
              v-for="item in reports"
              :key="item.id"
              class="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors"
            >
              <td class="p-4 text-slate-400 font-mono">#{{ item.id }}</td>
              <td class="p-4">
                <div class="font-bold text-slate-700 dark:text-slate-200">{{ item.username || 'Unkown' }}</div>
                <div class="text-[10px] text-slate-400">UID: {{ item.user_id }}</div>
              </td>
              <td class="p-4">
                <div class="text-xs text-slate-400 mb-1">QID: #{{ item.question_id }}</div>
                <div class="text-slate-700 dark:text-slate-300 font-medium line-clamp-2" :title="JSON.stringify(item.question_content, null, 2)">
                  {{ typeof item.question_content === 'object' ? item.question_content.question : item.question_content }}
                </div>
              </td>
              <td class="p-4 max-w-sm">
                <div class="font-bold text-rose-500 dark:text-rose-400 mb-1">{{ item.reason }}</div>
                <div class="text-slate-500 dark:text-slate-400 line-clamp-2 text-xs" :title="item.detail">{{ item.detail || '无详细描述' }}</div>
              </td>
              <td class="p-4 text-center">
                <span
                  class="px-2.5 py-1 rounded-full text-[10px] font-black tracking-wider uppercase inline-block"
                  :class="{
                    'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400': item.status === 'pending',
                    'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400': item.status === 'resolved',
                    'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400': item.status === 'rejected',
                  }"
                >
                  {{ item.status }}
                </span>
              </td>
              <td class="p-4 text-center text-xs text-slate-500 dark:text-slate-400">
                {{ new Date(item.created_at).toLocaleString() }}
              </td>
              <td class="p-4 text-right">
                <div v-if="item.status === 'pending'" class="flex gap-2 justify-end">
                  <button
                    @click="updateStatus(item.id, 'resolved')"
                    class="px-3 py-1.5 bg-green-50 hover:bg-green-100 text-green-600 dark:bg-green-900/20 dark:hover:bg-green-900/40 dark:text-green-400 rounded-lg text-xs font-bold transition-colors"
                  >
                    已处理
                  </button>
                  <button
                    @click="updateStatus(item.id, 'rejected')"
                    class="px-3 py-1.5 bg-slate-50 hover:bg-slate-200 text-slate-600 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-slate-400 rounded-lg text-xs font-bold transition-colors"
                  >
                    驳回
                  </button>
                </div>
                <div v-else class="text-xs text-slate-400">
                  <span v-if="item.resolved_at">于 {{ new Date(item.resolved_at).toLocaleString() }} 更新</span>
                </div>
              </td>
            </tr>
            <tr v-if="reports.length === 0 && !loading">
              <td colspan="7" class="p-12 text-center text-slate-400 font-medium border-none">
                <div class="flex flex-col items-center gap-3">
                  <span class="text-4xl text-slate-300 dark:text-slate-600">🎉</span>
                  <p>没有找到任何报错信息</p>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination block -->
      <div v-if="pagination.totalPages > 1" class="p-4 border-t border-slate-100 dark:border-slate-700/50 flex justify-between items-center bg-slate-50/50 dark:bg-slate-800/30">
        <div class="text-xs font-medium text-slate-500">
          共 {{ pagination.total }} 条记录，当前第 {{ pagination.page }} / {{ pagination.totalPages }} 页
        </div>
        <div class="flex gap-1">
          <button
            @click="handlePageChange(pagination.page - 1)"
            :disabled="pagination.page === 1"
            class="px-3 py-1.5 rounded-lg text-xs font-bold transition-all"
            :class="pagination.page === 1 ? 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-600 cursor-not-allowed' : 'bg-white text-slate-700 hover:bg-indigo-50 hover:text-indigo-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-indigo-900/30 border border-slate-200 dark:border-slate-600 shadow-sm'"
          >
            上一页
          </button>
          <button
            @click="handlePageChange(pagination.page + 1)"
            :disabled="pagination.page === pagination.totalPages"
            class="px-3 py-1.5 rounded-lg text-xs font-bold transition-all"
            :class="pagination.page === pagination.totalPages ? 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-600 cursor-not-allowed' : 'bg-white text-slate-700 hover:bg-indigo-50 hover:text-indigo-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-indigo-900/30 border border-slate-200 dark:border-slate-600 shadow-sm'"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialogRef" />
  </div>
</template>
