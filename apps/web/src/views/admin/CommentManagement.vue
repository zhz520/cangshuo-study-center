<script setup>
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
    <div class="max-w-7xl mx-auto">
        <!-- Header -->
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
            <div>
                <h1 class="text-2xl font-black text-slate-800 dark:text-slate-100 tracking-tight">💬 评论弹幕管理</h1>
                <p class="text-sm text-slate-500 dark:text-slate-400 mt-1">全局巡检用户评论，维护社区健康环境</p>
            </div>
        </div>

        <!-- Filters & Search -->
        <div class="mb-6 flex flex-col sm:flex-row gap-4 items-center justify-between">
            <div class="relative w-full sm:w-96">
                <span class="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400">🔍</span>
                <input
                    v-model="search"
                    type="text"
                    placeholder="搜索评论内容、用户名或文章标题..."
                    class="w-full pl-10 pr-5 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-2xl text-sm text-slate-700 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-400 transition-all shadow-sm"
                />
            </div>

            <div class="flex items-center gap-3">
                <select 
                    v-model="statusFilter"
                    @change="page = 1; fetchComments()"
                    class="px-4 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-2xl text-xs font-bold text-slate-600 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition-all shadow-sm cursor-pointer"
                >
                    <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                        {{ opt.label }}
                    </option>
                </select>
                
                <div class="text-xs font-bold text-slate-400 uppercase tracking-widest whitespace-nowrap">
                    共 {{ total }} 条记录
                </div>
            </div>
        </div>

        <!-- Categories/Stats (Simple) -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
            <div class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-2xl p-5 border border-slate-200/50 dark:border-slate-700/50 shadow-sm shadow-indigo-500/5">
                <p class="text-2xl font-black text-slate-800 dark:text-slate-100">{{ total }}</p>
                <p class="text-[10px] text-slate-500 dark:text-slate-400 font-bold uppercase tracking-wider mt-1">总评论数</p>
            </div>
            <div class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-2xl p-5 border border-slate-200/50 dark:border-slate-700/50 shadow-sm shadow-indigo-500/5">
                <p class="text-2xl font-black text-indigo-500">{{ totalPages }}</p>
                <p class="text-[10px] text-slate-500 dark:text-slate-400 font-bold uppercase tracking-wider mt-1">页数</p>
            </div>
            <div class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-2xl p-5 border border-slate-200/50 dark:border-slate-700/50 shadow-sm shadow-indigo-500/5">
                <p class="text-2xl font-black text-slate-800 dark:text-slate-100">{{ limit }}</p>
                <p class="text-[10px] text-slate-500 dark:text-slate-400 font-bold uppercase tracking-wider mt-1">每页限制</p>
            </div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="flex justify-center py-20">
            <div class="animate-spin rounded-full h-8 w-8 border-2 border-indigo-500 border-t-transparent"></div>
        </div>

        <!-- Comments List -->
        <div v-else class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-2xl border border-slate-200/50 dark:border-slate-700/50 overflow-hidden shadow-xl shadow-slate-200/50 dark:shadow-none">
            <!-- Desktop Table -->
            <div class="hidden md:block overflow-x-auto">
                <table class="w-full text-sm">
                    <thead>
                        <tr class="border-b border-slate-200 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-900/50">
                            <th class="text-left px-6 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">用户</th>
                            <th class="text-left px-6 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">评论内容</th>
                            <th class="text-left px-6 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">文章及状态</th>
                            <th class="text-center px-6 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">发布时间</th>
                            <th class="text-right px-6 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">巡检操作</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100 dark:divide-slate-700/50">
                        <tr v-if="comments.length === 0">
                            <td colspan="5" class="text-center py-20 text-slate-400">
                                <p class="text-5xl mb-4">🤐</p>
                                <p class="text-sm font-medium">没找到任何评论记录</p>
                            </td>
                        </tr>
                        <tr
                            v-for="comment in comments"
                            :key="comment.id"
                            class="hover:bg-slate-50/80 dark:hover:bg-slate-700/30 transition-colors group"
                        >
                            <td class="px-6 py-4 whitespace-nowrap">
                                <div class="flex items-center gap-3">
                                    <div class="w-8 h-8 rounded-full bg-slate-100 dark:bg-slate-700 flex-shrink-0 overflow-hidden ring-2 ring-white dark:ring-slate-800">
                                        <img :src="comment.avatar_icon || `https://api.dicebear.com/7.x/fun-emoji/svg?seed=${comment.username}`" class="w-full h-full object-cover" />
                                    </div>
                                    <span class="font-bold text-slate-700 dark:text-slate-200 text-xs">{{ comment.username }}</span>
                                </div>
                            </td>
                            <td class="px-6 py-4">
                                <div class="max-w-md text-slate-600 dark:text-slate-300 text-xs leading-relaxed line-clamp-3 italic">
                                    "{{ comment.content }}"
                                </div>
                            </td>
                            <td class="px-6 py-4">
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
                            <td class="px-6 py-4 text-center whitespace-nowrap text-[10px] text-slate-400 font-medium">
                                {{ formatDate(comment.created_at) }}
                            </td>
                            <td class="px-6 py-4 text-right">
                                <div class="flex items-center justify-end gap-2">
                                    <template v-if="comment.status === 'pending'">
                                        <button 
                                            @click="updateStatus(comment, 'approved')"
                                            class="px-2.5 py-1.5 text-[10px] font-black uppercase tracking-tighter text-emerald-500 bg-emerald-50 dark:bg-emerald-500/10 rounded-lg hover:bg-emerald-500 hover:text-white transition-all shadow-sm"
                                        >
                                            通过
                                        </button>
                                        <button 
                                            @click="updateStatus(comment, 'rejected')"
                                            class="px-2.5 py-1.5 text-[10px] font-black uppercase tracking-tighter text-amber-500 bg-amber-50 dark:bg-amber-500/10 rounded-lg hover:bg-amber-500 hover:text-white transition-all shadow-sm"
                                        >
                                            驳回
                                        </button>
                                    </template>
                                    <template v-else>
                                        <button 
                                            @click="updateStatus(comment, 'pending')"
                                            class="px-2.5 py-1.5 text-[10px] font-black uppercase tracking-tighter text-slate-400 bg-slate-50 dark:bg-slate-700/50 rounded-lg hover:bg-slate-500 hover:text-white transition-all"
                                        >
                                            撤回
                                        </button>
                                    </template>
                                    <button 
                                        @click="deleteComment(comment)"
                                        class="px-2.5 py-1.5 text-[10px] font-black uppercase tracking-tighter text-rose-500 bg-rose-50 dark:bg-rose-500/10 rounded-lg hover:bg-rose-500 hover:text-white transition-all transform group-hover:scale-105 shadow-sm"
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
                    <p class="text-5xl mb-4">🤐</p>
                    <p class="text-sm font-medium">没找到任何评论记录</p>
                </div>
                <div v-for="comment in comments" :key="comment.id" class="p-5 space-y-4">
                    <div class="flex justify-between items-start">
                        <div class="flex items-center gap-2">
                            <div class="w-7 h-7 rounded-full bg-slate-100 dark:bg-slate-700 overflow-hidden">
                                <img :src="comment.avatar_icon || `https://api.dicebear.com/7.x/fun-emoji/svg?seed=${comment.username}`" class="w-full h-full object-cover" />
                            </div>
                            <div>
                                <p class="text-xs font-black text-slate-800 dark:text-slate-100">{{ comment.username }}</p>
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
                                <button @click="updateStatus(comment, 'approved')" class="p-1.5 text-emerald-500 bg-emerald-50 dark:bg-emerald-500/10 rounded-lg text-[10px]">✅</button>
                                <button @click="updateStatus(comment, 'rejected')" class="p-1.5 text-amber-500 bg-amber-50 dark:bg-amber-500/10 rounded-lg text-[10px]">🚫</button>
                            </template>
                            <button @click="deleteComment(comment)" class="p-1.5 text-rose-500 bg-rose-50 dark:bg-rose-500/10 rounded-lg text-[10px]">
                                🗑️
                            </button>
                        </div>
                    </div>
                    <div class="text-xs text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-slate-900/50 p-3 rounded-xl border border-slate-100 dark:border-slate-700 leading-relaxed italic">
                        "{{ comment.content }}"
                    </div>
                    <div class="flex justify-end pt-1">
                        <span class="text-[10px] text-slate-400 font-bold uppercase tracking-widest">{{ formatDate(comment.created_at) }}</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Pagination -->
        <div v-if="totalPages > 1" class="mt-8 flex justify-center items-center gap-2">
            <button 
                @click="page-- ; fetchComments()" 
                :disabled="page === 1"
                class="w-10 h-10 flex items-center justify-center rounded-xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 disabled:opacity-30 disabled:cursor-not-allowed hover:border-indigo-500 hover:text-indigo-500 transition-all font-bold"
            >
                ←
            </button>
            <div class="px-6 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-xs font-black text-slate-500 dark:text-slate-400">
                第 {{ page }} 页 / 共 {{ totalPages }} 页
            </div>
            <button 
                @click="page++ ; fetchComments()" 
                :disabled="page === totalPages"
                class="w-10 h-10 flex items-center justify-center rounded-xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 disabled:opacity-30 disabled:cursor-not-allowed hover:border-indigo-500 hover:text-indigo-500 transition-all font-bold"
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
