<script setup>
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';
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
  <div class="admin-workspace ui-page admin-stack">
    <div class="admin-page-bar">
      <AdminPageHeading title="题目报错" description="处理用户提交的题目纠错与内容反馈。" />
      <div class="admin-page-actions">
        <select
          v-model="filterStatus"
          @change="fetchReports(1)"
          class="ui-select admin-control"
        >
          <option value="">全部状态</option>
          <option value="pending">待处理</option>
          <option value="resolved">已处理</option>
          <option value="rejected">已驳回</option>
        </select>
        <button class="ui-btn ui-btn-outline ui-btn-sm admin-control" type="button" @click="fetchReports(1)">刷新</button>
      </div>
    </div>

    <div class="admin-metric-strip">
      <div class="admin-metric"><span>报错总数</span><strong>{{ pagination.total }}</strong></div>
      <div class="admin-metric"><span>本页显示</span><strong>{{ reports.length }}</strong></div>
      <div class="admin-metric"><span>每页</span><strong>{{ pagination.limit }}</strong></div>
      <div class="admin-metric"><span>页码</span><strong>{{ pagination.page }} / {{ pagination.totalPages }}</strong></div>
    </div>

    <!-- Reports Table / Cards -->
    <div class="admin-table-card relative min-h-[260px]">
      <div v-if="loading" class="absolute inset-0 bg-white/80 dark:bg-slate-800/80 z-10 flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left admin-data-table">
          <thead>
            <tr>
              <th class="w-16">ID</th>
              <th class="w-32">用户</th>
              <th class="w-48">题目</th>
              <th>报错内容</th>
              <th class="w-32 text-center">状态</th>
              <th class="w-40 text-center">时间</th>
              <th class="w-32 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-700/50 text-sm">
            <tr
              v-for="item in reports"
              :key="item.id"
              class="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors"
            >
              <td class="font-mono text-[11px]" style="color:var(--text-quote)">#{{ item.id }}</td>
              <td>
                <div style="color:var(--text-main);font-weight:550">{{ item.username || '未知用户' }}</div>
                <div class="text-[10px]" style="color:var(--text-quote)">UID {{ item.user_id }}</div>
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
                <span class="admin-role" :class="{ 'is-admin': item.status === 'pending' }">
                  {{ item.status === 'pending' ? '待处理' : item.status === 'resolved' ? '已处理' : '已驳回' }}
                </span>
              </td>
              <td class="p-4 text-center text-xs text-slate-500 dark:text-slate-400">
                {{ new Date(item.created_at).toLocaleString() }}
              </td>
              <td class="p-4 text-right">
                <div v-if="item.status === 'pending'" class="flex gap-2 justify-end">
                  <button
                    @click="updateStatus(item.id, 'resolved')"
                    class="admin-text-action"
                  >
                    已处理
                  </button>
                  <button
                    @click="updateStatus(item.id, 'rejected')"
                    class="admin-text-action danger"
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
              <td colspan="7">
                <div class="admin-empty admin-table-empty">
                  <span class="admin-empty-title">没有待处理报错</span>
                  <span class="admin-empty-copy">新的题目反馈会显示在这里。</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination block -->
      <div v-if="pagination.totalPages > 1" class="p-4 border-t border-slate-100 dark:border-slate-700/50 flex justify-between items-center bg-slate-50/50 dark:bg-slate-800/30">
        <div class="text-xs font-medium" style="color:var(--text-sub)">
          共 {{ pagination.total }} 条记录，当前第 {{ pagination.page }} / {{ pagination.totalPages }} 页
        </div>
        <div class="flex gap-1">
          <button
            @click="handlePageChange(pagination.page - 1)"
            class="ui-btn ui-btn-outline ui-btn-sm admin-control"
            :disabled="pagination.page === 1"
          >
            上一页
          </button>
          <button
            @click="handlePageChange(pagination.page + 1)"
            class="ui-btn ui-btn-outline ui-btn-sm admin-control"
            :disabled="pagination.page === pagination.totalPages"
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
