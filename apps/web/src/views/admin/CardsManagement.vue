<script setup>
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';
import { ref, onMounted, computed } from 'vue';
import { admin } from '../../api';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';
import Toast from '../../components/ui/Toast.vue';

const cards = ref([]);
const loading = ref(false);
const showModal = ref(false);
const confirmDialog = ref(null);
const toast = ref(null);

const filterStatus = ref(null); // 'used', 'unused', or null

const pagination = ref({
  total: 0,
  page: 1,
  limit: 20,
  totalPages: 1
});

const form = ref({
  count: 10,
  duration_days: 30
});

const fetchCards = async (page = 1) => {
  loading.value = true;
  try {
    const { data } = await admin.getCards({ page, limit: pagination.value.limit, status: filterStatus.value });
    cards.value = data.data;
    pagination.value = data.pagination;
  } catch (e) {
    toast.value?.add('获取卡密失败: ' + (e.response?.data?.error || e.message), 'error');
  } finally {
    loading.value = false;
  }
};

const handlePageChange = (newPage) => {
  if (newPage >= 1 && newPage <= pagination.value.totalPages) {
    fetchCards(newPage);
  }
};

const openCreateModal = () => {
  form.value = { count: 10, duration_days: 30 };
  showModal.value = true;
};

const generateCards = async () => {
  try {
    await admin.generateCards(form.value);
    showModal.value = false;
    toast.value?.add(`成功生成 ${form.value.count} 张卡密`, 'success');
    fetchCards(1);
  } catch (e) {
    toast.value?.add('生成失败: ' + (e.response?.data?.error || e.message), 'error');
  }
};

const deleteCard = async (card) => {
  const confirmed = await confirmDialog.value.confirm({
    title: '删除卡密',
    message: `确定要删除卡密 ${card.code} 吗？如果已被使用删除后不影响当前VIP有效性，但无法再查阅此卡。`,
    confirmText: '删除',
    cancelText: '取消',
    type: 'danger'
  });
  if (confirmed) {
    try {
      await admin.deleteCard(card.id);
      toast.value?.add('删除成功', 'success');
      fetchCards(pagination.value.page);
    } catch (e) {
      toast.value?.add('删除失败: ' + (e.response?.data?.error || e.message), 'error');
    }
  }
};

const copyToClipboard = async (text) => {
    try {
        await navigator.clipboard.writeText(text);
        toast.value?.add('卡密已复制', 'success');
    } catch (err) {
        toast.value?.add('复制失败', 'error');
    }
};

onMounted(() => fetchCards(1));

const setFilter = (status) => {
    filterStatus.value = status;
    fetchCards(1);
};
</script>

<template>
  <div class="admin-workspace ui-page admin-stack">
    <div class="admin-page-bar">
      <AdminPageHeading title="订阅卡密" description="批量生成兑换码，管理 Plus 会员时长。" />
      <div class="admin-page-actions">
        <button class="ui-btn ui-btn-primary admin-control" type="button" @click="openCreateModal">批量生成</button>
      </div>
    </div>

    <div class="admin-tabs">
      <button class="admin-tab" :class="{ 'is-active': filterStatus === null }" type="button" @click="setFilter(null)">全部</button>
      <button class="admin-tab" :class="{ 'is-active': filterStatus === 'unused' }" type="button" @click="setFilter('unused')">未使用</button>
      <button class="admin-tab" :class="{ 'is-active': filterStatus === 'used' }" type="button" @click="setFilter('used')">已使用</button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="admin-table-card">
      <div class="admin-empty"><span class="ui-spinner"></span><span class="admin-empty-title">正在加载卡密</span></div>
    </div>

    <!-- Table -->
    <div v-else class="admin-table-card">
      <div class="overflow-x-auto">
        <table class="w-full text-sm admin-data-table">
          <thead>
            <tr>
              <th class="text-left">兑换码</th>
              <th class="text-left">时长</th>
              <th class="text-left">状态</th>
              <th class="text-left">使用者</th>
              <th class="text-left">生成 / 使用时间</th>
              <th class="text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="cards.length === 0">
              <td colspan="6"><div class="admin-empty admin-table-empty"><span class="admin-empty-title">暂无卡密</span><span class="admin-empty-copy">生成后可在列表中复制和核销。</span></div></td>
            </tr>
            <tr
              v-for="card in cards"
              :key="card.id"
              class="border-b border-slate-100 dark:border-slate-700/50 hover:bg-slate-50/80 dark:hover:bg-slate-700/30 transition-colors"
            >
              <td class="font-mono font-bold text-indigo-600 dark:text-indigo-400 flex items-center gap-2">
                {{ card.code }}
                <button @click="copyToClipboard(card.code)" title="复制" class="text-[10px] text-slate-400 hover:text-indigo-500 transition-colors admin-control">复制</button>
              </td>
              <td class="px-4 font-bold text-slate-700 dark:text-slate-300">{{ card.duration_days }} 天</td>
              <td class="px-4">
                <span class="admin-role" :class="{ 'is-demo': card.is_used }">
                    {{ card.is_used ? '已使用' : '未使用' }}
                </span>
              </td>
              <td class="px-4 text-slate-600 dark:text-slate-300">
                <span v-if="card.is_used" class="font-medium bg-slate-100 dark:bg-slate-700 px-2 py-1 rounded-md text-xs">ID: {{ card.used_by }} ({{ card.used_by_name || '已删号'}})</span>
                <span v-else class="text-slate-300 dark:text-slate-600">-</span>
              </td>
              <td class="px-4 text-xs text-slate-500 dark:text-slate-400">
                <div>生成: {{ new Date(card.created_at).toLocaleString() }}</div>
                <div v-if="card.is_used" class="text-amber-600 dark:text-amber-400 mt-1">核销: {{ new Date(card.used_at).toLocaleString() }}</div>
              </td>
              <td class="text-right">
                <button @click="deleteCard(card)" class="admin-text-action danger">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="pagination.totalPages > 1" class="px-6 py-4 border-t border-slate-200 dark:border-slate-700 flex flex-wrap items-center justify-between gap-4">
        <div class="text-sm text-slate-500 dark:text-slate-400">
          共 <span class="font-bold text-slate-800 dark:text-slate-200">{{ pagination.total }}</span> 条记录
        </div>
        <div class="flex items-center gap-2">
          <button @click="handlePageChange(pagination.page - 1)" :disabled="pagination.page === 1" class="p-2 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50 transition-colors admin-control">◀</button>
          <div class="flex items-center gap-1">
            <template v-for="p in pagination.totalPages" :key="p">
              <button
                v-if="p === 1 || p === pagination.totalPages || (p >= pagination.page - 1 && p <= pagination.page + 1)"
                @click="handlePageChange(p)"
                class="w-8 h-8 rounded-xl font-bold text-sm flex items-center justify-center transition-colors admin-control"
                :class="pagination.page === p ? 'bg-indigo-500 text-white shadow-md shadow-indigo-500/20' : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700'"
              >
                {{ p }}
              </button>
              <span v-else-if="p === pagination.page - 2 || p === pagination.page + 2" class="text-slate-400">...</span>
            </template>
          </div>
          <button @click="handlePageChange(pagination.page + 1)" :disabled="pagination.page === pagination.totalPages" class="p-2 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50 transition-colors admin-control">▶</button>
        </div>
      </div>
    </div>

    <!-- Create Modal -->
    <Teleport to="body">
      <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div class="absolute inset-0 bg-slate-900/50" @click="showModal = false"></div>
        <div class="relative bg-white dark:bg-slate-800 rounded-xl w-full max-w-sm overflow-hidden">
          <div class="p-6">
            <h2 class="text-xl text-slate-800 dark:text-slate-100 mb-6">生成激活卡密</h2>
            <div class="space-y-4">
              <div>
                <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-2">生成数量 (张)</label>
                <input v-model.number="form.count" type="number" min="1" max="1000" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 ui-input" />
              </div>
              <div>
                <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-2">订阅时长 (天/面额)</label>
                <input v-model.number="form.duration_days" type="number" min="1" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 ui-input" />
              </div>
            </div>
          </div>
          <div class="p-6 bg-slate-50 dark:bg-slate-900/50 border-t border-slate-100 dark:border-slate-700/50 flex gap-3">
            <button @click="showModal = false" class="flex-1 py-3 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 rounded-xl text-sm font-bold hover:bg-slate-100 transition-colors border border-slate-200 dark:border-slate-700 admin-control">取消</button>
            <button @click="generateCards" class="flex-1 py-3 bg-indigo-500 text-white rounded-xl text-sm font-bold transition-all admin-control">确认生成</button>
          </div>
        </div>
      </div>
    </Teleport>

    <ConfirmDialog ref="confirmDialog" />
    <Toast ref="toast" />
  </div>
</template>
