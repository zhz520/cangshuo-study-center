<script setup>
import { sanitizeHtml } from "../../utils/sanitize-html";
import { ref, onMounted, computed, watch } from 'vue';
import { QuillEditor } from '@vueup/vue-quill';
import '@vueup/vue-quill/dist/vue-quill.snow.css';
import MarkdownIt from 'markdown-it';
import { admin } from '../../api';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';

const CDN_BASE = 'https://studyapi.zhzgo.cn';

// Prepend domain for relative paths like /uploads/xxx.jpg
const fullUrl = (url) => {
  if (!url) return '';
  if (url.startsWith('http')) return url;
  return CDN_BASE + url;
};

const md = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true
});

const articles = ref([]);
const loading = ref(false);
const showModal = ref(false);
const editingArticle = ref(null);
const confirmDialog = ref(null);
const searchQuery = ref('');
const filterCategory = ref(null); // null = all
const customCategory = ref(false);
const modalLoading = ref(false);

const defaultCategories = ['学习资料', '考试技巧', '课外拓展', '通知公告', '经验分享'];

// editMode: 'html' 富文本, 'markdown' Markdown
const editMode = ref('html');

const form = ref({
  title: '',
  summary: '',
  // HTML 内容（富文本或由 Markdown 渲染而来）
  content: '',
  // 原始 Markdown 内容（仅在 markdown 模式下使用）
  content_markdown: '',
  // 内容格式: 'html' | 'markdown'
  content_format: 'html',
  cover_url: '',
  category: '未分类',
  is_published: 1,
  is_pinned: 0
});

const categories = computed(() => {
  const cats = new Set(articles.value.map(a => a.category || '未分类'));
  // Merge with defaults
  defaultCategories.forEach(c => cats.add(c));
  return [...cats];
});

const filteredArticles = computed(() => {
  let list = articles.value;
  if (filterCategory.value) {
    list = list.filter(a => (a.category || '未分类') === filterCategory.value);
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase();
    list = list.filter(a =>
      a.title.toLowerCase().includes(q) || (a.summary || '').toLowerCase().includes(q)
    );
  }
  return list;
});

const fetchArticles = async () => {
  loading.value = true;
  try {
    const { data } = await admin.getArticles();
    articles.value = data;
  } catch (e) {
    console.error('Failed to fetch articles:', e);
  } finally {
    loading.value = false;
  }
};

const openCreateModal = () => {
  editingArticle.value = null;
  editMode.value = 'html';
  form.value = {
    title: '',
    summary: '',
    content: '',
    content_markdown: '',
    content_format: 'html',
    cover_url: '',
    category: '未分类',
    is_published: 1,
    is_pinned: 0
  };
  showModal.value = true;
  modalLoading.value = false;
};

const contentLoadFailed = ref(false);
const openEditModal = async (article) => {
  editingArticle.value = article;
  // Reset form with basic list data immediately to avoid showing previous article's content
  form.value = {
    title: article.title,
    summary: article.summary || '',
    content: '',
    content_markdown: '',
    content_format: 'html',
    cover_url: article.cover_url || '',
    category: article.category || '未分类',
    is_published: article.is_published,
    is_pinned: article.is_pinned || 0
  };
  showModal.value = true;
  modalLoading.value = true;
  contentLoadFailed.value = false;

  const currentId = article.id;

  // 编辑时拉取完整文章（列表接口不返回 content）
  try {
    const { data: full } = await admin.getArticle(currentId);

    // Check if the user hasn't switched to another article or closed the modal
    if (editingArticle.value?.id !== currentId || !showModal.value) return;

    const format = full.content_format || 'html';
    editMode.value = format === 'markdown' ? 'markdown' : 'html';
    const rawMarkdown = full.content_markdown || '';
    const htmlFromMarkdown = rawMarkdown ? md.render(rawMarkdown) : '';

    form.value = {
      title: full.title,
      summary: full.summary || '',
      content: full.content || htmlFromMarkdown || '',
      content_markdown: rawMarkdown,
      content_format: format,
      cover_url: full.cover_url || '',
      category: full.category || '未分类',
      is_published: full.is_published,
      is_pinned: full.is_pinned || 0
    };
  } catch (e) {
    console.error('Failed to load article:', e);
    // Again, check if stale
    if (editingArticle.value?.id !== currentId || !showModal.value) return;
    contentLoadFailed.value = true;
  } finally {
    if (editingArticle.value?.id === currentId) {
      modalLoading.value = false;
    }
  }
};

const saveArticle = async () => {
  if (!form.value.title.trim()) {
    alert('请输入文章标题');
    return;
  }

  const hasContent = editMode.value === 'markdown' ? form.value.content_markdown?.trim() : form.value.content?.trim();
  if (!hasContent) {
    alert('请输入文章内容');
    return;
  }

  try {
    // 根据当前编辑模式修正格式字段
    if (editMode.value === 'markdown') {
      form.value.content_format = 'markdown';
      form.value.content = md.render(form.value.content_markdown || '');
    } else {
      form.value.content_format = 'html';
    }

    if (editingArticle.value) {
      await admin.updateArticle(editingArticle.value.id, form.value);
    } else {
      await admin.createArticle(form.value);
    }
    showModal.value = false;
    await fetchArticles();
  } catch (e) {
    alert('保存失败: ' + (e.response?.data?.error || e.message));
  }
};

const deleteArticle = async (article) => {
  const confirmed = await confirmDialog.value.confirm({
    title: '删除文章',
    message: `确定要删除文章「${article.title}」吗？此操作不可恢复。`,
    confirmText: '删除',
    cancelText: '取消',
    type: 'danger'
  });
  if (confirmed) {
    try {
      await admin.deleteArticle(article.id);
      await fetchArticles();
    } catch (e) {
      alert('删除失败: ' + (e.response?.data?.error || e.message));
    }
  }
};

const togglePublish = async (article) => {
  try {
    const newValue = article.is_published ? 0 : 1;
    await admin.updateArticle(article.id, {
      is_published: newValue
    });
    await fetchArticles();
  } catch (e) {
    alert('操作失败');
  }
};

const togglePin = async (article) => {
  try {
    const newValue = article.is_pinned ? 0 : 1;
    await admin.updateArticle(article.id, {
      is_pinned: newValue
    });
    await fetchArticles();
  } catch (e) {
    alert('操作失败');
  }
};

const uploadImage = async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  const formData = new FormData();
  formData.append('image', file);
  try {
    const { data } = await admin.uploadImage(formData);
    // Store full URL in database so it works everywhere (APP, web)
    form.value.cover_url = fullUrl(data.url);
  } catch (e) {
    alert('上传失败');
  }
};

onMounted(fetchArticles);

// 监听 Markdown 文本，实时更新 HTML 预览
watch(
  () => form.value.content_markdown,
  (val) => {
    if (editMode.value === 'markdown') {
      form.value.content = md.render(val || '');
    }
  }
);
</script>

<template>
  <div class="max-w-7xl mx-auto">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
      <div>
        <h1 class="text-2xl font-black text-slate-800 dark:text-slate-100 tracking-tight">📰 资源文章管理</h1>
        <p class="text-sm text-slate-500 dark:text-slate-400 mt-1">管理资源中心的文章内容，支持增删改查和发布控制</p>
      </div>
      <button @click="openCreateModal" class="px-6 py-3 bg-indigo-500 text-white rounded-2xl font-bold text-sm hover:bg-indigo-600 transition-all active:scale-95 shadow-lg shadow-indigo-500/20 flex items-center gap-2">
        <span class="text-lg">+</span> 新建文章
      </button>
    </div>

    <!-- Search + Category Filter -->
    <div class="mb-6 space-y-4">
      <input
        v-model="searchQuery"
        type="text"
        placeholder="搜索文章标题或摘要..."
        class="w-full sm:w-80 px-5 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-2xl text-sm text-slate-700 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-400 transition-all"
      />
      <div class="flex flex-wrap gap-2">
        <button @click="filterCategory = null" class="px-4 py-2 text-xs font-bold rounded-full transition-all" :class="!filterCategory ? 'bg-indigo-500 text-white shadow-md' : 'bg-white dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700 hover:border-indigo-300'">
          全部 ({{ articles.length }})
        </button>
        <button v-for="cat in categories" :key="cat" @click="filterCategory = filterCategory === cat ? null : cat" class="px-4 py-2 text-xs font-bold rounded-full transition-all" :class="filterCategory === cat ? 'bg-indigo-500 text-white shadow-md' : 'bg-white dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700 hover:border-indigo-300'">
          {{ cat }} ({{ articles.filter(a => (a.category || '未分类') === cat).length }})
        </button>
      </div>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
      <div class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-2xl p-5 border border-slate-200/50 dark:border-slate-700/50">
        <p class="text-2xl font-black text-slate-800 dark:text-slate-100">{{ articles.length }}</p>
        <p class="text-xs text-slate-500 dark:text-slate-400 font-medium mt-1">总文章数</p>
      </div>
      <div class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-2xl p-5 border border-slate-200/50 dark:border-slate-700/50">
        <p class="text-2xl font-black text-green-600 dark:text-green-400">{{ articles.filter(a => a.is_published).length }}</p>
        <p class="text-xs text-slate-500 dark:text-slate-400 font-medium mt-1">已发布</p>
      </div>
      <div class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-2xl p-5 border border-slate-200/50 dark:border-slate-700/50">
        <p class="text-2xl font-black text-amber-600 dark:text-amber-400">{{ articles.filter(a => !a.is_published).length }}</p>
        <p class="text-xs text-slate-500 dark:text-slate-400 font-medium mt-1">草稿</p>
      </div>
      <div class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-2xl p-5 border border-slate-200/50 dark:border-slate-700/50">
        <p class="text-2xl font-black text-indigo-600 dark:text-indigo-400">{{ categories.length }}</p>
        <p class="text-xs text-slate-500 dark:text-slate-400 font-medium mt-1">分类数</p>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex justify-center py-20">
      <div class="animate-spin rounded-full h-8 w-8 border-2 border-indigo-500 border-t-transparent"></div>
    </div>

    <!-- Articles: PC 表格 / 移动端卡片 -->
    <div v-else class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-2xl border border-slate-200/50 dark:border-slate-700/50 overflow-hidden">
      <!-- 桌面端：表格 -->
      <div class="hidden md:block overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-slate-200 dark:border-slate-700">
              <th class="text-left px-6 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">文章</th>
              <th class="text-left px-4 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">作者</th>
              <th class="text-left px-4 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">分类</th>
              <th class="text-center px-4 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">浏览</th>
              <th class="text-center px-4 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">状态</th>
              <th class="text-center px-4 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">日期</th>
              <th class="text-right px-6 py-4 font-bold text-slate-500 dark:text-slate-400 text-xs uppercase tracking-wider">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="filteredArticles.length === 0">
              <td colspan="6" class="text-center py-16 text-slate-400">
                <p class="text-4xl mb-3">📭</p>
                <p>暂无文章</p>
              </td>
            </tr>
            <tr
              v-for="article in filteredArticles"
              :key="article.id"
              class="border-b border-slate-100 dark:border-slate-700/50 hover:bg-slate-50/80 dark:hover:bg-slate-700/30 transition-colors"
            >
              <td class="px-6 py-4">
                <div class="flex items-center gap-3">
                  <div v-if="article.cover_url" class="w-12 h-12 rounded-lg bg-slate-100 dark:bg-slate-700 overflow-hidden flex-shrink-0">
                    <img :src="fullUrl(article.cover_url)" :alt="article.title" class="w-full h-full object-cover" />
                  </div>
                  <div v-else class="w-12 h-12 rounded-lg bg-gradient-to-br from-indigo-100 to-purple-100 dark:from-indigo-900/30 dark:to-purple-900/30 flex items-center justify-center text-lg flex-shrink-0">📄</div>
                  <div class="min-w-0">
                    <p class="font-bold text-slate-800 dark:text-slate-100 truncate max-w-xs">
                      <span v-if="article.is_pinned" class="text-amber-500 mr-1" title="置顶">📌</span>{{ article.title }}
                    </p>
                    <p class="text-xs text-slate-400 dark:text-slate-500 truncate max-w-xs mt-0.5">{{ article.summary || '暂无摘要' }}</p>
                  </div>
                </div>
              </td>
              <td class="px-4 py-4">
                <div class="flex items-center gap-2">
                  <div class="w-6 h-6 rounded-full bg-slate-200 dark:bg-slate-700 flex items-center justify-center text-[10px] overflow-hidden">
                    <img v-if="article.author_avatar" :src="article.author_avatar" class="w-full h-full object-cover" />
                    <span v-else>👤</span>
                  </div>
                  <span class="text-slate-600 dark:text-slate-300 font-medium whitespace-nowrap">{{ article.author_name || '系统' }}</span>
                </div>
              </td>
              <td class="px-4 py-4">
                <span class="px-3 py-1 text-xs font-bold rounded-full bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400">{{ article.category || '未分类' }}</span>
              </td>
              <td class="px-4 py-4 text-center text-slate-600 dark:text-slate-400 font-medium">{{ article.view_count || 0 }}</td>
              <td class="px-4 py-4 text-center">
                <button @click="togglePublish(article)" class="px-3 py-1 text-xs font-bold rounded-full transition-colors" :class="article.is_published ? 'bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400 hover:bg-green-100' : 'bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400 hover:bg-amber-100'">
                  {{ article.is_published ? '已发布' : '草稿' }}
                </button>
              </td>
              <td class="px-4 py-4 text-center text-xs text-slate-400">{{ article.created_at?.split('T')[0] || '-' }}</td>
              <td class="px-6 py-4 text-right">
                <div class="flex items-center justify-end gap-2">
                  <button @click="togglePin(article)" class="px-3 py-1.5 text-xs font-bold rounded-lg transition-colors" :class="article.is_pinned ? 'text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/20 hover:bg-amber-100' : 'text-slate-500 dark:text-slate-400 bg-slate-50 dark:bg-slate-800 hover:bg-slate-100 dark:hover:bg-slate-700'">
                    {{ article.is_pinned ? '取消置顶' : '置顶' }}
                  </button>
                  <button @click="openEditModal(article)" class="px-3 py-1.5 text-xs font-bold text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-900/20 rounded-lg hover:bg-indigo-100 dark:hover:bg-indigo-900/40 transition-colors">编辑</button>
                  <button @click="deleteArticle(article)" class="px-3 py-1.5 text-xs font-bold text-rose-600 dark:text-rose-400 bg-rose-50 dark:bg-rose-900/20 rounded-lg hover:bg-rose-100 dark:hover:bg-rose-900/40 transition-colors">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <!-- 移动端：卡片列表 -->
      <div class="md:hidden divide-y divide-slate-200 dark:divide-slate-700">
        <div v-if="filteredArticles.length === 0" class="text-center py-16 text-slate-400 px-4">
          <p class="text-4xl mb-3">📭</p>
          <p>暂无文章</p>
        </div>
        <div
          v-for="article in filteredArticles"
          :key="article.id"
          class="p-4 hover:bg-slate-50/80 dark:hover:bg-slate-700/30 transition-colors"
        >
          <div class="flex gap-3">
            <div v-if="article.cover_url" class="w-16 h-16 rounded-xl bg-slate-100 dark:bg-slate-700 overflow-hidden flex-shrink-0">
              <img :src="fullUrl(article.cover_url)" :alt="article.title" class="w-full h-full object-cover" />
            </div>
            <div v-else class="w-16 h-16 rounded-xl bg-gradient-to-br from-indigo-100 to-purple-100 dark:from-indigo-900/30 dark:to-purple-900/30 flex items-center justify-center text-2xl flex-shrink-0">📄</div>
            <div class="flex-1 min-w-0">
              <p class="font-bold text-slate-800 dark:text-slate-100 line-clamp-2">
                <span v-if="article.is_pinned" class="text-amber-500 mr-1">📌</span>{{ article.title }}
              </p>
              <p class="text-xs text-slate-400 dark:text-slate-500 mt-0.5 line-clamp-1">{{ article.summary || '暂无摘要' }}</p>
              <div class="flex flex-wrap items-center gap-2 mt-2">
                <span class="px-2 py-0.5 text-[10px] font-bold rounded-full bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400">👤 {{ article.author_name || '作者:系统' }}</span>
                <span class="px-2 py-0.5 text-[10px] font-bold rounded-full bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400">{{ article.category || '未分类' }}</span>
                <span class="text-[10px] text-slate-400">{{ article.view_count || 0 }} 浏览</span>
                <span class="text-[10px] text-slate-400">{{ article.created_at?.split('T')[0] || '-' }}</span>
              </div>
              <div class="flex gap-2 mt-3">
                <button @click="togglePublish(article)" class="px-2 py-1 text-[10px] font-bold rounded-lg" :class="article.is_published ? 'bg-green-100 dark:bg-green-900/30 text-green-600' : 'bg-amber-100 dark:bg-amber-900/30 text-amber-600'">{{ article.is_published ? '已发布' : '草稿' }}</button>
                <button @click="togglePin(article)" class="px-2 py-1 text-[10px] font-bold rounded-lg text-slate-500 bg-slate-100 dark:bg-slate-700">{{ article.is_pinned ? '取消置顶' : '置顶' }}</button>
                <button @click="openEditModal(article)" class="px-2 py-1 text-[10px] font-bold rounded-lg text-indigo-600 bg-indigo-50 dark:bg-indigo-900/20">编辑</button>
                <button @click="deleteArticle(article)" class="px-2 py-1 text-[10px] font-bold rounded-lg text-rose-600 bg-rose-50 dark:bg-rose-900/20">删除</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create/Edit Modal：PC 居中弹窗，移动端全屏 -->
    <Teleport to="body">
      <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center p-2 sm:p-4">
        <div class="absolute inset-0 bg-slate-900/50 backdrop-blur-sm" @click="showModal = false"></div>
        <div class="relative bg-white dark:bg-slate-800 rounded-2xl sm:rounded-3xl shadow-2xl w-full max-w-2xl max-h-[95vh] sm:max-h-[90vh] overflow-y-auto mx-auto">
          <div class="sticky top-0 bg-white dark:bg-slate-800 px-8 py-6 border-b border-slate-200 dark:border-slate-700 rounded-t-3xl z-10">
            <h2 class="text-xl font-black text-slate-800 dark:text-slate-100">{{ editingArticle ? '编辑文章' : '新建文章' }}</h2>
          </div>

          <div class="p-8 space-y-6">
            <!-- Title -->
            <div>
              <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">标题 *</label>
              <input v-model="form.title" type="text" placeholder="输入文章标题" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-sm text-slate-700 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-400" />
            </div>

            <!-- Category -->
            <div>
              <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">分类</label>
              <div class="flex gap-2 flex-wrap mb-2">
                <button v-for="cat in categories" :key="cat" @click="form.category = cat; customCategory = false" class="px-3 py-1.5 text-xs font-bold rounded-lg transition-all" :class="form.category === cat && !customCategory ? 'bg-indigo-500 text-white' : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-600'">
                  {{ cat }}
                </button>
                <button @click="customCategory = true; form.category = ''" class="px-3 py-1.5 text-xs font-bold rounded-lg transition-all" :class="customCategory ? 'bg-indigo-500 text-white' : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-600'">
                  + 自定义
                </button>
              </div>
              <input v-if="customCategory" v-model="form.category" type="text" placeholder="输入自定义分类名称" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-sm text-slate-700 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-400" />
            </div>

            <!-- Summary -->
            <div>
              <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">摘要</label>
              <textarea v-model="form.summary" rows="2" placeholder="简短的文章摘要" class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-sm text-slate-700 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-400 resize-none"></textarea>
            </div>

            <!-- 正文加载失败提示（编辑时接口 404 等） -->
            <div v-if="editingArticle && contentLoadFailed" class="rounded-xl bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 px-4 py-3 text-sm text-amber-800 dark:text-amber-200">
              ⚠️ 正文未加载（可能服务端未更新）。可先改标题/摘要等并保存，或部署最新服务端后重试。
            </div>

            <!-- 正文内容 (Markdown / HTML) -->
            <div class="relative min-h-[460px]">
              <!-- Loading Overlay -->
              <div v-if="modalLoading" class="absolute inset-x-0 top-0 bottom-0 z-20 flex flex-col items-center justify-center bg-white/60 dark:bg-slate-800/60 backdrop-blur-[2px] rounded-xl border border-slate-200 dark:border-slate-700">
                <div class="animate-spin rounded-full h-10 w-10 border-4 border-indigo-500 border-t-transparent mb-3"></div>
                <p class="text-sm font-bold text-slate-600 dark:text-slate-300">正在拉取文章详情...</p>
              </div>

              <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">正文内容</label>
              <div class="flex gap-2 mb-3">
                <button
                  type="button"
                  class="px-3 py-1.5 text-xs font-bold rounded-full border transition-colors"
                  :class="editMode === 'html'
                    ? 'bg-indigo-500 text-white border-indigo-500 shadow-sm'
                    : 'bg-slate-50 dark:bg-slate-900 border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300'"
                  @click="editMode = 'html'"
                >
                  富文本编辑
                </button>
                <button
                  type="button"
                  class="px-3 py-1.5 text-xs font-bold rounded-full border transition-colors"
                  :class="editMode === 'markdown'
                    ? 'bg-indigo-500 text-white border-indigo-500 shadow-sm'
                    : 'bg-slate-50 dark:bg-slate-900 border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300'"
                  @click="editMode = 'markdown'"
                >
                  Markdown 编辑
                </button>
              </div>

              <!-- 富文本编辑器（完整工具栏 + 大编辑区） -->
              <div v-if="editMode === 'html'" class="rounded-xl overflow-hidden border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 article-editor-wrap">
                <QuillEditor
                  v-model:content="form.content"
                  content-type="html"
                  theme="snow"
                  toolbar="full"
                  placeholder="输入正文，支持标题、加粗/斜体/下划线、列表、对齐、引用、代码块、链接、图片、颜色等"
                  class="article-editor"
                />
              </div>

              <!-- Markdown 编辑器 -->
              <div v-else class="rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 overflow-hidden">
                <textarea
                  v-model="form.content_markdown"
                  rows="14"
                  placeholder="在此输入 Markdown 文本，例如：# 标题、**加粗**、`代码`、- 列表 等"
                  class="w-full px-4 py-3 bg-transparent text-sm text-slate-800 dark:text-slate-100 font-mono resize-none outline-none"
                ></textarea>
              </div>

              <!-- 实时预览 -->
              <div class="mt-4">
                <div class="flex items-center justify-between mb-2">
                  <span class="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">实时预览</span>
                  <span class="text-[11px] text-slate-400" v-if="editMode === 'markdown'">根据 Markdown 自动渲染为 HTML，APP 与网页将显示相同效果</span>
                </div>
                <div class="rounded-xl border border-dashed border-slate-200 dark:border-slate-700 bg-slate-50/60 dark:bg-slate-900/60 max-h-[320px] overflow-auto p-4">
                  <div v-if="form.content" class="prose prose-sm max-w-none text-slate-800 dark:text-slate-100 markdown-body" v-html="sanitizeHtml(form.content)"></div>
                  <div v-else class="text-xs text-slate-400 text-center py-8">暂无内容，将根据上方编辑器实时渲染预览</div>
                </div>
              </div>
            </div>

            <!-- Cover URL -->
            <div>
              <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">封面图片</label>
              <div class="flex gap-3 items-center">
                <input v-model="form.cover_url" type="text" placeholder="图片 URL 或上传" class="flex-1 px-4 py-3 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-sm text-slate-700 dark:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-400" />
                <label class="px-4 py-3 bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-sm font-bold cursor-pointer hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors">
                  上传
                  <input type="file" accept="image/*" @change="uploadImage" class="hidden" />
                </label>
              </div>
              <div v-if="form.cover_url" class="mt-3 rounded-xl overflow-hidden border border-slate-200 dark:border-slate-700">
                <img :src="fullUrl(form.cover_url)" class="w-full h-40 object-cover" />
              </div>
            </div>

            <!-- Published -->
            <div class="flex items-center justify-between bg-slate-50 dark:bg-slate-900 rounded-xl px-4 py-3">
              <span class="text-sm font-bold text-slate-700 dark:text-slate-300">立即发布</span>
              <button @click="form.is_published = form.is_published ? 0 : 1" class="w-12 h-7 rounded-full transition-colors flex items-center px-1" :class="form.is_published ? 'bg-green-500' : 'bg-slate-300 dark:bg-slate-600'">
                <div class="w-5 h-5 bg-white rounded-full shadow transition-transform" :class="form.is_published ? 'translate-x-5' : 'translate-x-0'"></div>
              </button>
            </div>

            <!-- Pinned -->
            <div class="flex items-center justify-between bg-slate-50 dark:bg-slate-900 rounded-xl px-4 py-3">
              <div>
                <span class="text-sm font-bold text-slate-700 dark:text-slate-300">📌 置顶文章</span>
                <p class="text-xs text-slate-400 mt-0.5">置顶文章会始终显示在列表最前面</p>
              </div>
              <button @click="form.is_pinned = form.is_pinned ? 0 : 1" class="w-12 h-7 rounded-full transition-colors flex items-center px-1" :class="form.is_pinned ? 'bg-amber-500' : 'bg-slate-300 dark:bg-slate-600'">
                <div class="w-5 h-5 bg-white rounded-full shadow transition-transform" :class="form.is_pinned ? 'translate-x-5' : 'translate-x-0'"></div>
              </button>
            </div>
          </div>

          <div class="sticky bottom-0 bg-white dark:bg-slate-800 px-8 py-6 border-t border-slate-200 dark:border-slate-700 rounded-b-3xl flex gap-3 justify-end">
            <button @click="showModal = false" class="px-6 py-3 text-sm font-bold text-slate-600 dark:text-slate-400 bg-slate-100 dark:bg-slate-700 rounded-xl hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors">取消</button>
            <button @click="saveArticle" :disabled="modalLoading" class="px-6 py-3 text-sm font-bold text-white bg-indigo-50 rounded-xl hover:bg-indigo-600 transition-colors shadow-lg shadow-indigo-500/20 disabled:opacity-50 disabled:cursor-not-allowed" :class="modalLoading ? 'bg-slate-400' : 'bg-indigo-500'">{{ editingArticle ? '保存修改' : '创建文章' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <ConfirmDialog ref="confirmDialog" />
  </div>
</template>

<style scoped>
.article-editor-wrap {
  min-height: 420px;
}
.article-editor :deep(.ql-container) {
  min-height: 380px;
  font-size: 15px;
}
.article-editor :deep(.ql-editor) {
  min-height: 380px;
}
@media (min-height: 700px) {
  .article-editor-wrap { min-height: min(500px, 55vh); }
  .article-editor :deep(.ql-container),
  .article-editor :deep(.ql-editor) { min-height: min(460px, 50vh); }
}
@media (max-width: 640px) {
  .article-editor :deep(.ql-toolbar) {
    flex-wrap: wrap;
    padding: 6px 8px;
  }
  .article-editor :deep(.ql-toolbar .ql-formats) {
    margin-right: 6px;
    margin-bottom: 4px;
  }
}
</style>
