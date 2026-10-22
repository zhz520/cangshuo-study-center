<script setup>
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
  <div class="max-w-7xl mx-auto space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-4">
      <div>
        <h1 class="text-2xl font-black text-slate-800 dark:text-slate-100 tracking-tight">💳 订阅卡密管理</h1>
        <p class="text-sm text-slate-500 dark:text-slate-400 mt-1">批量生成激活码，供用户兑换VIP时长及完整题库访问权</p>
      </div>
      <button @click="openCreateModal" class="px-6 py-3 bg-indigo-500 text-white rounded-2xl font-bold text-sm hover:bg-indigo-600 transition-all active:scale-95 shadow-lg shadow-indigo-500/20 flex items-center gap-2">
        <span class="text-lg">+</span> 批量生成卡密
      </button>
    </div>

    <!-- Filters -->
    <div class="flex gap-2">
        <button @click="setFilter(null)" class="px-4 py-2 text-xs font-bold rounded-lg transition-colors" :class="filterStatus === null ? 'bg-slate-800 text-white dark:bg-slate-200 dark:text-slate-800' : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400'">全部</button>
        <button @click="setFilter('unused')" class="px-4 py-2 text-xs font-bold rounded-lg transition-colors" :class="filterStatus === 'unused' ? 'bg-green-500 text-white' : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400'">未使用</button>
        <button @click="setFilter('used')" class="px-4 py-2 text-xs font-bold rounded-lg transition-colors" :class="filterStatus === 'used' ? 'bg-amber-500 text-white' : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400'">已使用</button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex justify-center py-20">
      <div class="animate-spin rounded-full h-8 w-8 border-2 border-indigo-500 border-t-transparent"></div>
    </div>

    <!-- Table -->
    <div v-else class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-2xl border border-slate-200/50 dark:border-slate-700/50 overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-slate-200 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-900/50">
              <th class="text-left px-6 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">卡密 CODE</th>
              <th class="text-left px-4 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">面额 (天)</th>
              <th class="text-left px-4 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">状态</th>
              <th class="text-left px-4 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">使用者</th>
              <th class="text-left px-4 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">生成/使用时间</th>
              <th class="text-right px-6 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="cards.length === 0">
              <td colspan="6" class="text-center py-16 text-slate-400">
                <p class="text-4xl mb-3">📭</p>
                <p>暂无卡密，点击上方生成按钮添加</p>
              </td>
            </tr>
            <tr
              v-for="card in cards"
              :key="card.id"
              class="border-b border-slate-100 dark:border-slate-700/50 hover:bg-slate-50/80 dark:hover:bg-slate-700/30 transition-colors"
            >
              <td class="px-6 py-4 font-mono font-bold text-indigo-600 dark:text-indigo-400 flex items-center gap-2">
                {{ card.code }}
                <button @click="copyToClipboard(card.code)" title="复制" class="text-slate-400 hover:text-indigo-500 transition-colors">📄</button>
              </td>
              <td class="px-4 py-4 font-bold text-slate-700 dark:text-slate-300">{{ card.duration_days }} 天</td>
              <td class="px-4 py-4">
                <span class="px-3 py-1 text-xs font-bold rounded-full" :class="card.is_used ? 'bg-amber-50 text-amber-600 dark:bg-amber-900/20 dark:text-amber-400' : 'bg-green-50 text-green-600 dark:bg-green-900/20 dark:text-green-400'">
                    {{ card.is_used ? '已使用' : '未使用' }}
                </span>
              </td>
              <td class="px-4 py-4 text-slate-600 dark:text-slate-300">
                <span v-if="card.is_used" class="font-medium bg-slate-100 dark:bg-slate-700 px-2 py-1 rounded-md text-xs">ID: {{ card.used_by }} ({{ card.used_by_name || '已删号'}})</span>
                <span v-else class="text-slate-300 dark:text-slate-600">-</span>
              </td>
              <td class="px-4 py-4 text-xs text-slate-500 dark:text-slate-400">
                <div>生成: {{ new Date(card.created_at).toLocaleString() }}</div>
                <div v-if="card.is_used" class="text-amber-600 dark:text-amber-400 mt-1">核销: {{ new Date(card.used_at).toLocaleString() }}</div>
              </td>
              <td class="px-6 py-4 text-right">
                <button @click="deleteCard(card)" class="px-3 py-1.5 text-xs font-bold text-rose-600 dark:text-rose-400 bg-rose-50 dark:bg-rose-900/20 rounded-lg hover:bg-rose-100 dark:hover:bg-rose-900/40 transition-colors">删除</button>
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
          <button @click="handlePageChange(pagination.page - 1)" :disabled="pagination.page === 1" class="p-2 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50 transition-colors">◀</button>
          <div class="flex items-center gap-1">
            <template v-for="p in pagination.totalPages" :key="p">
              <button
                v-if="p === 1 || p === pagination.totalPages || (p >= pagination.page - 1 && p <= pagination.page + 1)"
                @click="handlePageChange(p)"
                class="w-8 h-8 rounded-xl font-bold text-sm flex items-center justify-center transition-colors"
                :class="pagination.page === p ? 'bg-indigo-500 text-white shadow-md shadow-indigo-500/20' : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700'"
              >
                {{ p }}
              </button>
              <span v-else-if="p === pagination.page - 2 || p === pagination.page + 2" class="text-slate-400">...</span>
            </template>
          </div>
          <button @click="handlePageChange(pagination.page + 1)" :disabled="pagination.page === pagination.totalPages" class="p-2 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50 transition-colors">▶</button>
        </div>
      </div>
    </div>

    <!-- Create Modal -->
    <Teleport to="body">
      <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div class="absolute inset-0 bg-slate-900/50 backdrop-blur-sm" @click="showModal = false"></div>
        <div class="relative bg-white dark:bg-slate-800 rounded-3xl shadow-2xl w-full max-w-sm overflow-hidden">
          <div class="p-6">
            <h2 class="text-xl font-black text-slate-800 dark:text-slate-100 mb-6">生成激活卡密</h2>
            <div class="space-y-4">
              <div>
                <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-2">生成数量 (张)</label>
                <input v-model.number="form.count" type="number" min="1" max="1000" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/50" />
              </div>
              <div>
                <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-2">订阅时长 (天/面额)</label>
                <input v-model.number="form.duration_days" type="number" min="1" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/50" />
              </div>
            </div>
          </div>
          <div class="p-6 bg-slate-50 dark:bg-slate-900/50 border-t border-slate-100 dark:border-slate-700/50 flex gap-3">
            <button @click="showModal = false" class="flex-1 py-3 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 rounded-xl text-sm font-bold hover:bg-slate-100 transition-colors border border-slate-200 dark:border-slate-700">取消</button>
            <button @click="generateCards" class="flex-1 py-3 bg-indigo-500 text-white rounded-xl text-sm font-bold shadow-lg shadow-indigo-500/20 active:scale-95 transition-all">确认生成</button>
          </div>
        </div>
      </div>
    </Teleport>

    <ConfirmDialog ref="confirmDialog" />
    <Toast ref="toast" />
  </div>
</template>
