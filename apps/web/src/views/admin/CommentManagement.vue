<script setup>
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';
import { ref, onMounted, computed, watch } from 'vue';
import { admin } from '../../api';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';

const comments = ref([]);
const loading = ref(false);
const total = ref(0);
const page = ref(1);
const limit = ref(20);
const search = ref('');
const confirmDialog = ref(null);
const statusFilter = ref('');

const statusOptions = [
    { label: '全部状态', value: '' },
    { label: '待审核', value: 'pending' },
    { label: '已通过', value: 'approved' },
    { label: '已驳回', value: 'rejected' }
];

const getStatusStyle = (status) => {
    switch (status) {
        case 'approved': return 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400 border-emerald-100 dark:border-emerald-500/20';
        case 'rejected': return 'bg-rose-50 text-rose-600 dark:bg-rose-500/10 dark:text-rose-400 border-rose-100 dark:border-rose-500/20';
        default: return 'bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-400 border-amber-100 dark:border-amber-500/20';
    }
};

const getStatusLabel = (status) => {
    switch (status) {
        case 'approved': return '已通过';
        case 'rejected': return '已驳回';
        default: return '待审核';
    }
};

const fetchComments = async () => {
    loading.value = true;
    try {
        const { data } = await admin.getComments({
            page: page.value,
            limit: limit.value,
            search: search.value,
            status: statusFilter.value
        });
        comments.value = data.data;
        total.value = data.total;
    } catch (e) {
        console.error('Failed to fetch comments:', e);
    } finally {
        loading.value = false;
    }
};

const updateStatus = async (comment, status) => {
    try {
        await admin.updateAdminCommentStatus(comment.id, status);
        await fetchComments();
    } catch (e) {
        alert('操作失败: ' + (e.response?.data?.error || e.message));
    }
};

const deleteComment = async (comment) => {
// ... existing deleteComment logic ...
    const confirmed = await confirmDialog.value.confirm({
        title: '删除评论',
        message: `确定要删除用户「${comment.username}」在文章《${comment.article_title}》下的评论吗？此操作不可恢复。`,
        confirmText: '删除',
        cancelText: '取消',
        type: 'danger'
    });
    if (confirmed) {
        try {
            await admin.deleteAdminComment(comment.id);
            await fetchComments();
        } catch (e) {
            alert('删除失败: ' + (e.response?.data?.error || e.message));
        }
    }
};

const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
};

// Search with debounce logic
let searchTimeout = null;
watch(search, () => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
        page.value = 1;
        fetchComments();
    }, 500);
});

onMounted(fetchComments);

const totalPages = computed(() => Math.ceil(total.value / limit.value));
</script>

<template>
    <div class="admin-workspace ui-page admin-stack">
        <div class="admin-page-bar">
            <AdminPageHeading title="评论管理" description="巡检文章评论，处理审核状态与不当内容。" />
        </div>

        <!-- Filters & Search -->
        <div class="admin-filter-bar">
            <div class="ui-search admin-search">
                <svg class="ui-search-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
                <input v-model="search" type="search" placeholder="搜索评论、用户名或文章标题" aria-label="搜索评论" class="ui-input" />
            </div>
            <div class="flex items-center gap-3 ml-auto">
                <select
                    v-model="statusFilter"
                    @change="page = 1; fetchComments()"
                    class="px-4 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-xs font-bold text-slate-600 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition-all cursor-pointer ui-select"
                >
                    <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                        {{ opt.label }}
                    </option>
                </select>

                <div class="text-[11px] whitespace-nowrap" style="color:var(--text-quote)">
                    {{ total }} 条
                </div>
            </div>
        </div>

        <div class="admin-metric-strip">
            <div class="admin-metric"><span>总评论</span><strong>{{ total }}</strong></div>
            <div class="admin-metric"><span>本页显示</span><strong>{{ comments.length }}</strong></div>
            <div class="admin-metric"><span>每页</span><strong>{{ limit }}</strong></div>
            <div class="admin-metric"><span>页码</span><strong>{{ page }} / {{ totalPages || 1 }}</strong></div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="flex justify-center py-20">
            <div class="animate-spin rounded-full h-8 w-8 border-2 border-indigo-500 border-t-transparent"></div>
        </div>

        <!-- Comments List -->
        <div v-else class="admin-table-card">
            <!-- Desktop Table -->
            <div class="hidden md:block overflow-x-auto">
                <table class="w-full text-sm admin-data-table">
                    <thead>
                        <tr class="border-b border-slate-200 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-900/50">
                            <th class="text-left font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">用户</th>
                            <th class="text-left font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">评论内容</th>
                            <th class="text-left font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">文章及状态</th>
                            <th class="text-center font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">发布时间</th>
                            <th class="text-right font-bold text-slate-500 dark:text-slate-400 text-xs uppercase">巡检操作</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100 dark:divide-slate-700/50">
                        <tr v-if="comments.length === 0">
                            <td colspan="5" class="text-center py-20 text-slate-400">

                                <p class="text-sm font-medium">没找到任何评论记录</p>
                            </td>
                        </tr>
                        <tr
                            v-for="comment in comments"
                            :key="comment.id"
                            class="hover:bg-slate-50/80 dark:hover:bg-slate-700/30 transition-colors group"
                        >
                            <td class="whitespace-nowrap">
                                <div class="flex items-center gap-3">
                                    <div class="w-8 h-8 rounded-full bg-slate-100 dark:bg-slate-700 flex-shrink-0 overflow-hidden ring-2 ring-white dark:ring-slate-800">
                                        <img :src="comment.avatar_icon || `https://api.dicebear.com/7.x/fun-emoji/svg?seed=${comment.username}`" class="w-full h-full object-cover" />
                                    </div>
                                    <span class="font-bold text-slate-700 dark:text-slate-200 text-xs">{{ comment.username }}</span>
                                </div>
                            </td>
                            <td class="">
                                <div class="max-w-md text-slate-600 dark:text-slate-300 text-xs leading-relaxed line-clamp-3 italic">
                                    "{{ comment.content }}"
                                </div>
                            </td>
                            <td class="">
                                <div class="max-w-[180px] truncate text-[11px] font-bold text-indigo-500 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-900/20 px-2.5 py-1 rounded-lg mb-2">
                                    {{ comment.article_title }}
                                </div>
                                <div
                                    :class="getStatusStyle(comment.status)"
                                    class="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border"
                                >
                                    {{ getStatusLabel(comment.status) }}
                                </div>
                            </td>
                            <td class="text-center whitespace-nowrap text-[10px] text-slate-400 font-medium">
                                {{ formatDate(comment.created_at) }}
                            </td>
                            <td class="text-right">
                                <div class="flex items-center justify-end gap-2">
                                    <template v-if="comment.status === 'pending'">
                                        <button
                                            @click="updateStatus(comment, 'approved')"
                                            class="px-2.5 py-1.5 text-[10px] uppercase tracking-tighter text-emerald-500 bg-emerald-50 dark:bg-emerald-500/10 rounded-lg hover:bg-emerald-500 hover:text-white transition-all admin-control"
                                        >
                                            通过
                                        </button>
                                        <button
                                            @click="updateStatus(comment, 'rejected')"
                                            class="px-2.5 py-1.5 text-[10px] uppercase tracking-tighter text-amber-500 bg-amber-50 dark:bg-amber-500/10 rounded-lg hover:bg-amber-500 hover:text-white transition-all admin-control"
                                        >
                                            驳回
                                        </button>
                                    </template>
                                    <template v-else>
                                        <button
                                            @click="updateStatus(comment, 'pending')"
                                            class="px-2.5 py-1.5 text-[10px] uppercase tracking-tighter text-slate-400 bg-slate-50 dark:bg-slate-700/50 rounded-lg hover:bg-slate-500 hover:text-white transition-all admin-control"
                                        >
                                            撤回
                                        </button>
                                    </template>
                                    <button
                                        @click="deleteComment(comment)"
                                        class="px-2.5 py-1.5 text-[10px] uppercase tracking-tighter text-rose-500 bg-rose-50 dark:bg-rose-500/10 rounded-lg hover:bg-rose-500 hover:text-white transition-all admin-control"
                                    >
                                        删除
                                    </button>
                                </div>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <!-- Mobile Card List -->
            <div class="md:hidden divide-y divide-slate-100 dark:divide-slate-700/50">
                <div v-if="comments.length === 0" class="text-center py-20 text-slate-400">

                    <p class="text-sm font-medium">没找到任何评论记录</p>
                </div>
                <div v-for="comment in comments" :key="comment.id" class="p-5 space-y-4">
                    <div class="flex justify-between items-start">
                        <div class="flex items-center gap-2">
                            <div class="w-7 h-7 rounded-full bg-slate-100 dark:bg-slate-700 overflow-hidden">
                                <img :src="comment.avatar_icon || `https://api.dicebear.com/7.x/fun-emoji/svg?seed=${comment.username}`" class="w-full h-full object-cover" />
                            </div>
                            <div>
                                <p class="text-xs text-slate-800 dark:text-slate-100">{{ comment.username }}</p>
                                <p class="text-[10px] text-slate-400 italic mb-1">在《{{ comment.article_title }}》</p>
                                <div
                                    :class="getStatusStyle(comment.status)"
                                    class="inline-flex items-center px-2 py-0.5 rounded-full text-[9px] font-bold border"
                                >
                                    {{ getStatusLabel(comment.status) }}
                                </div>
                            </div>
                        </div>
                        <div class="flex items-center gap-2">
                            <template v-if="comment.status === 'pending'">
                                <button @click="updateStatus(comment, 'approved')" class="px-2 py-1 text-emerald-600 bg-emerald-50 dark:bg-emerald-500/10 rounded text-[10px] admin-control">通过</button>
                                <button @click="updateStatus(comment, 'rejected')" class="px-2 py-1 text-amber-600 bg-amber-50 dark:bg-amber-500/10 rounded text-[10px] admin-control">驳回</button>
                            </template>
                            <button @click="deleteComment(comment)" class="p-1.5 text-rose-500 bg-rose-50 dark:bg-rose-500/10 rounded-lg text-[10px] admin-control">
                                删除
                            </button>
                        </div>
                    </div>
                    <div class="text-xs text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-slate-900/50 p-3 rounded-xl border border-slate-100 dark:border-slate-700 leading-relaxed italic">
                        "{{ comment.content }}"
                    </div>
                    <div class="flex justify-end pt-1">
                        <span class="text-[10px] text-slate-400 font-bold uppercase">{{ formatDate(comment.created_at) }}</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Pagination -->
        <div v-if="totalPages > 1" class="mt-8 flex justify-center items-center gap-2">
            <button
                @click="page-- ; fetchComments()"
                :disabled="page === 1"
                class="w-10 h-10 flex items-center justify-center rounded-xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 disabled:opacity-30 disabled:cursor-not-allowed hover:border-indigo-500 hover:text-indigo-500 transition-all font-bold admin-control"
            >
                ←
            </button>
            <div class="px-6 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-xs text-slate-500 dark:text-slate-400">
                第 {{ page }} 页 / 共 {{ totalPages }} 页
            </div>
            <button
                @click="page++ ; fetchComments()"
                :disabled="page === totalPages"
                class="w-10 h-10 flex items-center justify-center rounded-xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 disabled:opacity-30 disabled:cursor-not-allowed hover:border-indigo-500 hover:text-indigo-500 transition-all font-bold admin-control"
            >
                →
            </button>
        </div>

        <ConfirmDialog ref="confirmDialog" />
    </div>
</template>

<style scoped>
.line-clamp-3 {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    overflow: hidden;
}
</style>
