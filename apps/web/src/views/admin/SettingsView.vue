<script setup>
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';
import { ref, reactive, onMounted } from "vue";
import { admin } from "../../api";
import Toast from "../../components/ui/Toast.vue";
import ConfirmDialog from "../../components/ui/ConfirmDialog.vue";
import IconPicker from "../../components/ui/IconPicker.vue";

const activeTab = ref("settings");
const loading = ref(false);
const message = ref("");

// Data
const settings = ref({ registration_open: "true" });
const countdowns = ref([]);

const links = ref([]);
const toast = ref(null);

const confirmDialogRef = ref(null);

const showConfirm = (title, content, type = "info") => {
  if (confirmDialogRef.value) {
    return confirmDialogRef.value.confirm({ title, message: content, type });
  }
  return Promise.resolve(false);
};

// --- Daily Content Operations ---
const dailyContents = ref([]);
const dailyContentType = ref("all");
const showDailyModal = ref(false);
const editingDaily = ref({});
const dailyPagination = reactive({
  page: 1,
  limit: 10,
  total: 0,
  totalPages: 1,
});
const jumpPageNum = ref("");
const dailyTypes = [
  { value: "quote", label: "励志语录" },
  { value: "proverb", label: "英语谚语" },
  { value: "poetry", label: "古诗词" },
];

const dailyTypeLabel = (type) => dailyTypes.find(item => item.value === type)?.label || type;

const loadDailyContents = async (page = 1) => {
  try {
    const type = dailyContentType.value === "all" ? "" : dailyContentType.value;
    const res = await admin.getDailyContents(type, page, dailyPagination.limit);
    dailyContents.value = res.data.data;
    dailyPagination.page = res.data.pagination.page;
    dailyPagination.total = res.data.pagination.total;
    dailyPagination.totalPages = res.data.pagination.totalPages;
    jumpPageNum.value = ""; // Reset jump input
  } catch (e) {
    console.error(e);
  }
};

const handleDailyPageChange = (newPage) => {
  if (newPage >= 1 && newPage <= dailyPagination.totalPages) {
    loadDailyContents(newPage);
  }
};

const handleDailyJump = () => {
  const page = parseInt(jumpPageNum.value);
  if (page && page >= 1 && page <= dailyPagination.totalPages) {
    loadDailyContents(page);
  } else {
    toast.value?.add("请输入有效的页码", "warning");
  }
};

const openAddDaily = () => {
  editingDaily.value = { content: "", translation: "", type: "quote" };
  showDailyModal.value = true;
};
const openEditDaily = (item) => {
  editingDaily.value = { ...item };
  showDailyModal.value = true;
};
const saveDaily = async () => {
  try {
    await admin.updateDailyContent(editingDaily.value);
    showDailyModal.value = false;
    loadDailyContents();
    showMessage("内容已保存");
  } catch (e) {
    toast.value?.add(e.response?.data?.error || e.message, "error");
  }
};
const deleteDaily = async (id) => {
  const confirmed = await showConfirm(
    "确定删除吗？",
    "删除后无法恢复",
    "danger",
  );
  if (!confirmed) return;
  try {
    await admin.deleteDailyContent(id);
    loadDailyContents();
    showMessage("删除成功");
  } catch (e) {
    toast.value?.add(e.message, "error");
  }
};

// Modals
const showLinkModal = ref(false);
const showCountdownModal = ref(false);

const editingLink = ref({});
const editingCountdown = ref({});

// Fetch Data
const fetchData = async () => {
  loading.value = true;
  try {
    const [sRes, cRes, lRes] = await Promise.all([
      admin.getSettings(),
      admin.getCountdowns(),
      admin.getLinks(),
    ]);
    settings.value = sRes.data;
    countdowns.value = cRes.data;
    links.value = lRes.data;

    if (activeTab.value === "daily") loadDailyContents();
  } catch (error) {
    console.error("Failed to fetch settings:", error);
  } finally {
    loading.value = false;
  }
};

const saveMailSettings = async () => {
  try {
    const fields = [
      ['smtp_host', settings.value.smtp_host || ''],
      ['smtp_user', settings.value.smtp_user || ''],
      ['smtp_pass', settings.value.smtp_pass || ''],
      ['admin_email_for_reports', settings.value.admin_email_for_reports || ''],
    ];
    for (const [key, value] of fields) await admin.updateSettings({ key, value });
    showMessage('邮件设置已保存');
  } catch (error) {
    toast.value?.add('邮件设置保存失败', 'error');
  }
};

// Update Settings
const updateSetting = async (key, value) => {
  try {
    await admin.updateSettings({ key, value });
    settings.value[key] = value;
    showMessage("设置已更新");
  } catch (error) {
    toast.value?.add("更新失败", "error");
  }
};

// --- Countdown Operations ---
const openAddCountdown = () => {
  editingCountdown.value = {
    title: "",
    target_date: "",
    icon: "📅",
    sort_order: 0,
  };
  showCountdownModal.value = true;
};

const openEditCountdown = (item) => {
  editingCountdown.value = { ...item };
  showCountdownModal.value = true;
};

const saveCountdown = async () => {
  try {
    await admin.updateCountdown(editingCountdown.value);
    showCountdownModal.value = false;
    fetchData();
    showMessage("倒计时已保存");
  } catch (e) {
    toast.value?.add(e.response?.data?.error || e.message, "error");
  }
};

// --- Link Operations ---
const openAddLink = () => {
  editingLink.value = {
    name: "",
    description: "",
    url: "",
    icon: "🔗",
    color: "bg-slate-500",
    sort_order: 0,
  };
  showLinkModal.value = true;
};
const openEditLink = (item) => {
  editingLink.value = { ...item };
  showLinkModal.value = true;
};
const saveLink = async () => {
  try {
    await admin.updateLink(editingLink.value);
    showLinkModal.value = false;
    fetchData();
    showMessage("链接已保存");
  } catch (e) {
    toast.value?.add(e.response?.data?.error || e.message, "error");
  }
};

// Common Delete
const deleteItem = async (type, id) => {
  const confirmed = await showConfirm(
    "确定删除吗？",
    "删除后无法恢复",
    "danger",
  );
  if (!confirmed) return;
  try {
    if (type === "countdown") await admin.deleteCountdown(id);
    if (type === "link") await admin.deleteLink(id);
    fetchData();
    showMessage("删除成功");
  } catch (e) {
    toast.value?.add(e.message, "error");
  }
};

const showMessage = (msg, type = "success") => {
  toast.value?.add(msg, type);
};

const changeTab = (tab) => {
  activeTab.value = tab;
  if (tab === "daily") loadDailyContents(1);
};

onMounted(fetchData);
</script>

<template>
  <div class="admin-workspace ui-page settings-page">
    <header class="admin-page-bar">
      <AdminPageHeading title="系统设置" description="管理运行参数、App 内容与运营资源。" />
    </header>

    <div class="admin-tabs settings-tabs" role="tablist" aria-label="系统与内容分类">
      <button v-for="tab in [{id:'settings',label:'系统开关'},{id:'countdowns',label:'考试倒计时'},{id:'links',label:'快捷链接'},{id:'daily',label:'每日内容'}]" :key="tab.id" type="button" role="tab" :aria-selected="activeTab === tab.id" class="admin-tab" :class="{ 'is-active': activeTab === tab.id }" @click="changeTab(tab.id)">{{ tab.label }}</button>
    </div>

    <div class="settings-content">
    <!-- 1. System Settings -->
    <div v-if="activeTab === 'settings'" class="animate-fade-in settings-system-grid">
      <div class="settings-block">
        <h3
          class="settings-block-title"
        >
          注册入口
        </h3>

        <div
          class="settings-row"
        >
          <div class="flex gap-4 items-center flex-1 min-w-0">
            <div
              class="w-12 h-12 flex-shrink-0 rounded-xl flex items-center justify-center text-2xl"
              :class="['settings-registration-icon', { 'is-on': settings.registration_open === 'true' }]"
            >
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M15 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><path d="M19 8v6M16 11h6"/></svg>
            </div>
            <div class="min-w-0">
              <h3 class="font-bold text-slate-800 dark:text-slate-200">
                开放新用户注册
              </h3>
              <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                关闭后前端注册入口将被隐藏
              </p>
            </div>
          </div>

          <button
            @click="
              updateSetting(
                'registration_open',
                settings.registration_open === 'true' ? 'false' : 'true',
              )
            "
            class="admin-switch" :class="{ 'is-on': settings.registration_open === 'true' }" :aria-pressed="settings.registration_open === 'true'">
            <span></span>
          </button>
        </div>
      </div>

      <!-- AI Day Limit -->
      <div class="settings-block">
        <h3
          class="settings-block-title"
        >
          每日配额
        </h3>

        <div
          class="settings-row"
        >
          <div class="flex gap-4 items-center flex-1 min-w-0">
            <div class="settings-accent-icon">AI</div>
            <div class="min-w-0">
              <h3 class="font-bold text-slate-800 dark:text-slate-200">
                每日 AI 使用次数限制
              </h3>
              <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                分别设置普通用户和 Plus 订阅用户的每日 AI 助教使用次数
              </p>
            </div>
          </div>
        </div>

        <!-- Free user AI limit -->
        <div
          class="settings-mini-row"
        >
          <div class="flex gap-3 items-center flex-1 min-w-0">

            <div class="min-w-0">
              <h3 class="settings-row-title">普通用户</h3>
              <p class="settings-row-hint">每天可用次数</p>
            </div>
          </div>
          <div class="settings-input-action">
            <input v-model="settings.ai_day_limit_free" type="number" min="0" class="ui-input" placeholder="未设置" aria-label="普通用户每日 AI 次数" />
            <button type="button" class="ui-btn ui-btn-primary ui-btn-sm admin-control" @click="updateSetting('ai_day_limit_free', settings.ai_day_limit_free)">保存</button>
          </div>
        </div>

        <!-- Plus user AI limit -->
        <div
          class="settings-mini-row is-plus"
        >
          <div class="flex gap-3 items-center flex-1 min-w-0">
            <span class="admin-panel-mark">Plus</span>
            <div class="min-w-0">
              <h3 class="settings-row-title">Plus 会员</h3>
              <p class="settings-row-hint">每天可用次数</p>
            </div>
          </div>
          <div class="settings-input-action">
            <input v-model="settings.ai_day_limit_plus" type="number" min="0" class="ui-input" placeholder="未设置" aria-label="Plus 会员每日 AI 次数" />
            <button type="button" class="ui-btn ui-btn-primary ui-btn-sm admin-control" @click="updateSetting('ai_day_limit_plus', settings.ai_day_limit_plus)">保存</button>
          </div>
        </div>
      </div>

      <!-- APP Announcement -->
      <div class="settings-block settings-span-2">
        <h3
          class="settings-block-title"
        >
          公告内容
        </h3>

        <div
          class="settings-row"
        >
          <div class="flex gap-4 items-center flex-1 min-w-0">
            <div
              class="w-12 h-12 flex-shrink-0 rounded-xl flex items-center justify-center text-2xl"
              :class="
                settings.app_announcement_enabled === 'true'
                  ? 'bg-green-100 text-green-600 dark:bg-green-900/20 dark:text-green-400'
                  : 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500'
              "
            >
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m3 11 15-6v14L3 13v-2Z"/><path d="M11.6 16.1 13 21H8l-1.1-4"/><path d="M21 9v6"/></svg>
            </div>
            <div class="min-w-0">
              <h3 class="font-bold text-slate-800 dark:text-slate-200">
                启用 APP 首页公告
              </h3>
              <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                开启后将在 APP 首页顶部显示公告栏
              </p>
            </div>
          </div>

          <button
            @click="
              updateSetting(
                'app_announcement_enabled',
                settings.app_announcement_enabled === 'true' ? 'false' : 'true',
              )
            "
            class="admin-switch" :class="{ 'is-on': settings.app_announcement_enabled === 'true' }" :aria-pressed="settings.app_announcement_enabled === 'true'">
            <span></span>
          </button>
        </div>

        <div
          class="flex flex-col p-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-xl border border-slate-100 dark:border-slate-700 gap-3"
        >
          <label
            class="block text-xs font-bold text-slate-500 dark:text-slate-400"
            >公告内容</label
          >
          <textarea
            v-model="settings.app_announcement"
            rows="3"
            class="w-full p-4 rounded-xl border bg-white dark:bg-slate-700/50 border-slate-200 dark:border-slate-600 text-slate-800 dark:text-slate-100 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-textarea"
            placeholder="输入需要展示在 APP 首页的公告内容..."
          ></textarea>
          <div class="flex justify-end mt-2">
            <button
              @click="
                updateSetting(
                  'app_announcement',
                  settings.app_announcement || '',
                )
              "
              class="px-6 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold dark:shadow-none transition-all admin-control"
            >
              保存公告内容
            </button>
          </div>
        </div>
      </div>

      <!-- 邮件服务 -->
      <div class="settings-block settings-span-2">
        <h3
          class="settings-block-title"
        >
          邮件投递
        </h3>

        <div class="settings-mail-grid">
          <label class="settings-field"><span>邮件服务器</span><small>例如 smtp.qq.com</small><input v-model="settings.smtp_host" class="ui-input" placeholder="SMTP Host" /></label>
          <label class="settings-field"><span>发信邮箱</span><small>用于发送系统邮件</small><input v-model="settings.smtp_user" class="ui-input" type="email" placeholder="name@example.com" /></label>
          <label class="settings-field"><span>发信授权码</span><small>只保存在服务端</small><input v-model="settings.smtp_pass" class="ui-input" type="password" autocomplete="new-password" placeholder="SMTP 授权码" /></label>
          <label class="settings-field"><span>管理员收件邮箱</span><small>接收题目报错和系统提醒</small><input v-model="settings.admin_email_for_reports" class="ui-input" type="email" placeholder="admin@example.com" /></label>
        </div>
        <div class="settings-mail-actions"><button type="button" class="ui-btn ui-btn-primary ui-btn-sm admin-control" @click="saveMailSettings">保存邮件设置</button></div>
        <div class="settings-callout" style="margin-top:12px"><strong>安全提示</strong><span>授权码不会下发到前端页面。保存后请用一次找回密码流程验证邮件投递。</span></div>
      </div>
    </div>

    <div v-if="activeTab === 'countdowns'" class="animate-fade-in settings-content">
      <div class="settings-panel-toolbar"><div><strong>考试节点</strong><span>共 {{ countdowns.length }} 个日期</span></div><button type="button" class="ui-btn ui-btn-primary ui-btn-sm admin-control" @click="openAddCountdown">新增倒计时</button></div>
      <div v-if="countdowns.length" class="settings-directory">
        <div v-for="item in countdowns" :key="item.id" class="settings-countdown-row">
          <span class="settings-date">{{ item.target_date }}</span>
          <div class="settings-row-copy"><strong>{{ item.title }}</strong><span>首页与学习页考试倒计时</span></div>
          <div class="settings-row-actions"><button type="button" class="admin-text-action" @click="openEditCountdown(item)">编辑</button><button type="button" class="admin-text-action danger" @click="deleteItem('countdown', item.id)">删除</button></div>
        </div>
      </div>
      <div v-else class="settings-empty"><strong>暂无考试节点</strong><span>新增后会出现在首页倒计时区域。</span></div>
    </div>

    <div v-if="activeTab === 'links'" class="animate-fade-in settings-content">
      <div class="settings-panel-toolbar"><div><strong>外部资源</strong><span>共 {{ links.length }} 个链接</span></div><button type="button" class="ui-btn ui-btn-primary ui-btn-sm admin-control" @click="openAddLink">新增链接</button></div>
      <div v-if="links.length" class="admin-directory">
        <div v-for="(item, index) in links" :key="item.id" class="admin-link-row">
          <span class="admin-link-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="admin-link-mark">{{ item.name.slice(0, 1) }}</span>
          <div class="admin-link-copy"><strong>{{ item.name }}</strong><p>{{ item.description || '常用学习资源' }}</p><a :href="item.url" target="_blank" rel="noopener">{{ item.url }}</a></div>
          <div class="admin-row-actions"><button type="button" class="admin-text-action" @click="openEditLink(item)">编辑</button><button type="button" class="admin-text-action danger" @click="deleteItem('link', item.id)">删除</button></div>
        </div>
      </div>
      <div v-else class="settings-empty"><strong>暂无快捷链接</strong><span>新增后会出现在学习首页的资源区。</span></div>
    </div>

    <div v-if="activeTab === 'daily'" class="animate-fade-in settings-content">
      <div class="settings-panel-toolbar daily-toolbar">
        <div class="settings-filter-bar" role="group" aria-label="每日内容类型">
          <button v-for="filter in [{id:'all',label:'全部'},{id:'quote',label:'语录'},{id:'proverb',label:'谚语'},{id:'poetry',label:'诗词'}]" :key="filter.id" type="button" class="settings-filter-chip" :class="{ 'is-active': dailyContentType === filter.id }" @click="dailyContentType = filter.id; loadDailyContents()">{{ filter.label }}</button>
        </div>
        <button type="button" class="ui-btn ui-btn-primary ui-btn-sm admin-control" @click="openAddDaily">新增内容</button>
      </div>
      <div v-if="dailyContents.length" class="settings-daily-list">
        <article v-for="item in dailyContents" :key="item.id" class="settings-daily-row">
          <div class="settings-daily-meta"><span class="settings-type-tag">{{ dailyTypeLabel(item.type) }}</span><span>#{{ item.id }}</span></div>
          <div class="settings-daily-copy"><p>“{{ item.content }}”</p><span v-if="item.translation">{{ item.translation }}</span></div>
          <div class="settings-row-actions"><button type="button" class="admin-text-action" @click="openEditDaily(item)">编辑</button><button type="button" class="admin-text-action danger" @click="deleteDaily(item.id)">删除</button></div>
        </article>
      </div>
      <div v-else class="settings-empty"><strong>暂无内容</strong><span>新增语录、谚语或诗词后会显示在这里。</span></div>
      <div v-if="dailyPagination.totalPages > 1" class="settings-pager">
        <span>共 {{ dailyPagination.total }} 条 · 第 {{ dailyPagination.page }} / {{ dailyPagination.totalPages }} 页</span>
        <div>
          <button type="button" class="ui-btn ui-btn-outline ui-btn-sm admin-control" :disabled="dailyPagination.page === 1" @click="handleDailyPageChange(dailyPagination.page - 1)">上一页</button>
          <input v-model="jumpPageNum" type="number" min="1" :max="dailyPagination.totalPages" class="ui-input" @keyup.enter="handleDailyJump" />
          <button type="button" class="ui-btn ui-btn-outline ui-btn-sm admin-control" @click="handleDailyJump">跳转</button>
          <button type="button" class="ui-btn ui-btn-outline ui-btn-sm admin-control" :disabled="dailyPagination.page === dailyPagination.totalPages" @click="handleDailyPageChange(dailyPagination.page + 1)">下一页</button>
        </div>
      </div>
    </div>

    </div>

    <!-- MODALS -->

    <!-- Daily Content Modal -->
    <div
      v-if="showDailyModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 transition-all animate-fade-in"
    >
      <div
        class="ui-card w-full max-w-lg p-5 animate-scale-up border border-white/50 dark:border-slate-600/50"
      >
        <h3 class="text-xl text-slate-800 dark:text-white mb-6">
          {{ editingDaily.id ? "编辑内容" : "新增内容" }}
        </h3>
        <div class="space-y-5">
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-2"
              >内容类型</label
            >
            <div class="grid grid-cols-3 gap-3">
              <label class="cursor-pointer">
                <input
                  type="radio"
                  v-model="editingDaily.type"
                  value="quote"
                  class="peer sr-only"
                />
                <div
                  class="text-center py-2 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 text-xs font-bold peer-checked:bg-blue-50 peer-checked:border-blue-500 peer-checked:text-blue-600 dark:peer-checked:bg-blue-900/20 dark:peer-checked:text-blue-400 transition-all"
                >
                  励志语录
                </div>
              </label>
              <label class="cursor-pointer">
                <input
                  type="radio"
                  v-model="editingDaily.type"
                  value="proverb"
                  class="peer sr-only"
                />
                <div
                  class="text-center py-2 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 text-xs font-bold peer-checked:bg-purple-50 peer-checked:border-purple-500 peer-checked:text-purple-600 dark:peer-checked:bg-purple-900/20 dark:peer-checked:text-purple-400 transition-all"
                >
                  英语谚语
                </div>
              </label>
              <label class="cursor-pointer">
                <input
                  type="radio"
                  v-model="editingDaily.type"
                  value="poetry"
                  class="peer sr-only"
                />
                <div
                  class="text-center py-2 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 text-xs font-bold peer-checked:bg-green-50 peer-checked:border-green-500 peer-checked:text-green-600 dark:peer-checked:bg-green-900/20 dark:peer-checked:text-green-400 transition-all"
                >
                  古诗词
                </div>
              </label>
            </div>
          </div>
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-2"
              >正文内容</label
            >
            <textarea
              v-model="editingDaily.content"
              rows="4"
              class="w-full p-4 rounded-xl border bg-slate-50 dark:bg-slate-800/50 border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-100 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-textarea"
              placeholder="输入精彩内容..."
            ></textarea>
          </div>
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-2"
            >
              {{
                editingDaily.type === "proverb"
                  ? "中文翻译"
                  : editingDaily.type === "poetry"
                    ? "出处 (如: 《诗经》)"
                    : "出处 / 作者 (可选)"
              }}
            </label>
            <input
              v-model="editingDaily.translation"
              type="text"
              class="w-full px-4 py-3 rounded-xl border bg-slate-50 dark:bg-slate-800/50 border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-100 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-input"
            />
          </div>
        </div>
        <div class="flex gap-3 mt-8">
          <button
            @click="showDailyModal = false"
            class="flex-1 py-3 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs transition-colors admin-control"
          >
            取消
          </button>
          <button
            @click="saveDaily"
            class="flex-1 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs dark:shadow-none transition-all admin-control"
          >
            保存更改
          </button>
        </div>
      </div>
    </div>

    <!-- Countdown Modal -->
    <div
      v-if="showCountdownModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 transition-all animate-fade-in"
    >
      <div
        class="ui-card w-full max-w-sm p-6 animate-scale-up border border-white/50 dark:border-slate-600/50 max-h-[90vh] overflow-y-auto custom-scrollbar"
      >
        <h3 class="text-xl text-slate-800 dark:text-white mb-6">
          配置倒计时
        </h3>
        <div class="space-y-4">
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5"
              >标题</label
            >
            <input
              v-model="editingCountdown.title"
              type="text"
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-input"
            />
          </div>
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5"
              >目标日期</label
            >
            <input
              v-model="editingCountdown.target_date"
              type="text"
              placeholder="MM-DD 或 YYYY-MM-DD"
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-input"
            />
          </div>
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5"
              >图标</label
            >
            <IconPicker v-model="editingCountdown.icon" />
          </div>
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5"
              >排序权重 (越小越靠前)</label
            >
            <input
              v-model.number="editingCountdown.sort_order"
              type="number"
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-input"
            />
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button
            @click="showCountdownModal = false"
            class="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs transition-colors admin-control"
          >
            取消
          </button>
          <button
            @click="saveCountdown"
            class="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs dark:shadow-none transition-all admin-control"
          >
            保存
          </button>
        </div>
      </div>
    </div>

    <!-- Link Modal -->
    <div
      v-if="showLinkModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 transition-all animate-fade-in"
    >
      <div
        class="ui-card w-full max-w-md p-6 animate-scale-up border border-white/50 dark:border-slate-600/50 max-h-[90vh] overflow-y-auto custom-scrollbar"
      >
        <h3 class="text-xl text-slate-800 dark:text-white mb-6">
          {{ editingLink.id ? "编辑链接" : "新增链接" }}
        </h3>
        <div class="space-y-4">
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5"
              >名称</label
            >
            <input
              v-model="editingLink.name"
              type="text"
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-input"
            />
          </div>
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5"
              >URL</label
            >
            <input
              v-model="editingLink.url"
              type="text"
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all font-mono ui-input"
            />
          </div>
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5"
              >描述</label
            >
            <input
              v-model="editingLink.description"
              type="text"
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-input"
            />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label
                class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5"
                >图标</label
              >
              <IconPicker v-model="editingLink.icon" />
            </div>
            <div>
              <label
                class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5"
                >背景色 (Tailwind Class)</label
              >
              <input
                v-model="editingLink.color"
                type="text"
                placeholder="bg-slate-500"
                class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-input"
              />
            </div>
          </div>
          <div>
            <label
              class="block text-xs font-bold text-slate-500 dark:text-slate-400 mb-1.5"
              >排序权重</label
            >
            <input
              v-model.number="editingLink.sort_order"
              type="number"
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all ui-input"
            />
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button
            @click="showLinkModal = false"
            class="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs transition-colors admin-control"
          >
            取消
          </button>
          <button
            @click="saveLink"
            class="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs dark:shadow-none transition-all admin-control"
          >
            保存
          </button>
        </div>
      </div>
    </div>

    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialogRef" />
  </div>
</template>

<style scoped>
.settings-tabs { margin-bottom:14px; }
.settings-system-grid { display:grid; grid-template-columns:1fr; gap:0; }
.settings-span-2 { grid-column:auto; }
.settings-page-head { display:flex; align-items:flex-start; justify-content:space-between; gap:20px; padding-bottom:20px; margin-bottom:4px; border-bottom:1px solid var(--border); }
.settings-content { display:flex; flex-direction:column; gap:14px; }
.settings-block { padding:18px 0; border:0; border-bottom:1px solid var(--border); border-radius:0; background:transparent; }
.settings-block-title { margin:0 0 14px; color:var(--text-main); font-size:13px; font-weight:600; letter-spacing:-.01em; }
.settings-row { display:flex; align-items:center; justify-content:space-between; gap:18px; padding:12px 0; border-top:1px solid var(--border); }
.settings-row:first-of-type { border-top:0; }
.settings-row-copy { display:flex; flex-direction:column; gap:4px; min-width:0; }
.settings-row-copy strong { color:var(--text-main); font-size:13px; font-weight:550; }
.settings-row-copy span { color:var(--text-quote); font-size:10px; line-height:1.55; }
.settings-row-title { margin:0; color:var(--text-main); font-size:13px; font-weight:550; }
.settings-row-hint { margin:3px 0 0; color:var(--text-quote); font-size:10px; }
.settings-mini-row { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:12px 14px; margin-top:10px; border:1px solid var(--border); border-radius:9px; background:var(--bg-card-highlight); }
.settings-mini-row.is-plus { border-color:color-mix(in srgb,var(--primary) 20%,var(--border)); background:var(--primary-soft); }
.admin-switch { position:relative; width:44px; height:26px; flex:0 0 44px; border-radius:999px; background:var(--border-strong); transition:background .2s ease; }
.admin-switch span { position:absolute; top:3px; left:3px; width:20px; height:20px; border-radius:50%; background:var(--bg-card); box-shadow:0 1px 2px rgba(15,23,42,.12); transition:transform .2s ease; }
.admin-switch.is-on { background:var(--primary); }
.settings-registration-icon { width:44px; height:44px; flex:0 0 44px; display:flex; align-items:center; justify-content:center; border-radius:10px; background:var(--bg-card-highlight); color:var(--text-sub); }
.settings-registration-icon.is-on { background:var(--primary-soft); color:var(--primary); }
.admin-switch.is-on span { transform:translateX(18px); }
.settings-field-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; }
.settings-field-card { display:flex; flex-direction:column; gap:12px; padding:14px; border:1px solid var(--border); border-radius:9px; background:var(--bg-card-highlight); }
.settings-field-card.is-plus { border-color:color-mix(in srgb,var(--primary) 18%,var(--border)); background:var(--primary-soft); }
.settings-field-card > div:first-child { display:flex; flex-direction:column; gap:3px; }
.settings-accent-icon { width:38px; height:38px; flex:0 0 38px; display:grid; place-items:center; border:1px solid color-mix(in srgb,var(--primary) 16%,var(--border)); border-radius:8px; background:var(--primary-soft); color:var(--primary); font-size:10px; font-weight:650; letter-spacing:.03em; }
.settings-field-card strong { color:var(--text-main); font-size:13px; font-weight:550; }
.settings-field-card span { color:var(--text-quote); font-size:10px; }
.settings-input-action { display:flex; gap:7px; }
.settings-input-action .ui-input { width:80px; text-align:center; }
.settings-callout { display:flex; gap:10px; padding:12px 14px; border:1px solid var(--border); border-radius:9px; background:var(--bg-card-highlight); color:var(--text-sub); font-size:10px; line-height:1.6; }
.settings-callout strong { flex:0 0 auto; color:var(--text-main); font-weight:550; }
.settings-panel-toolbar { display:flex; align-items:center; justify-content:space-between; gap:14px; padding:16px 18px; border:1px solid var(--border); border-radius:10px; background:var(--bg-card); }
.settings-panel-toolbar > div:first-child { display:flex; flex-direction:column; gap:3px; }
.settings-panel-toolbar strong { color:var(--text-main); font-size:13px; font-weight:550; }
.settings-panel-toolbar span { color:var(--text-quote); font-size:10px; }
.settings-directory { margin-top:10px; overflow:hidden; border:1px solid var(--border); border-radius:10px; background:var(--bg-card); }
.settings-countdown-row { display:flex; align-items:center; gap:12px; padding:11px 16px; border-bottom:1px solid var(--border); }
.settings-countdown-row:last-child { border-bottom:0; }
.settings-date { flex:0 0 74px; color:var(--text-sub); font-family:ui-monospace,SFMono-Regular,Menlo,monospace; font-size:10px; font-weight:600; }
.settings-row-actions { display:flex; align-items:center; gap:2px; }
.settings-empty { display:flex; flex-direction:column; align-items:center; gap:5px; margin-top:10px; padding:40px 20px; border:1px solid var(--border); border-radius:10px; background:var(--bg-card); color:var(--text-sub); text-align:center; }
.settings-empty strong { color:var(--text-main); font-size:13px; font-weight:550; }
.settings-empty span { color:var(--text-quote); font-size:10px; }
.daily-toolbar { align-items:center; }
.settings-filter-bar { display:flex; align-items:center; gap:2px; }
.settings-panel-toolbar > div.settings-filter-bar { flex-direction:row; gap:2px; }
.settings-filter-chip { min-height:28px; padding:0 9px; border-radius:6px; color:var(--text-sub); font-size:11px; }
.settings-filter-chip:hover { background:var(--bg-card-highlight); color:var(--text-main); }
.settings-filter-chip.is-active { background:var(--primary-soft); color:var(--primary); font-weight:600; }
.settings-daily-list { display:flex; flex-direction:column; gap:8px; margin-top:10px; }
.settings-daily-row { display:flex; align-items:center; gap:16px; padding:14px 16px; border:1px solid var(--border); border-radius:9px; background:var(--bg-card); }
.settings-daily-meta { display:flex; flex:0 0 76px; flex-direction:column; gap:4px; color:var(--text-quote); font-size:9px; }
.settings-type-tag { display:inline-flex; width:fit-content; padding:2px 5px; border-radius:3px; background:var(--primary-soft); color:var(--primary); font-size:9px; font-weight:600; }
.settings-daily-copy { display:flex; flex:1; min-width:0; flex-direction:column; gap:4px; }
.settings-daily-copy p { color:var(--text-main); font-size:13px; line-height:1.65; }
.settings-daily-copy span { color:var(--text-sub); font-size:10px; line-height:1.5; }
.settings-pager { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-top:12px; color:var(--text-quote); font-size:10px; }
.settings-pager > div { display:flex; align-items:center; gap:6px; }
.settings-pager .ui-input { width:52px; min-height:32px; padding:0 6px; text-align:center; }
.settings-mail-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; }
.settings-field { display:flex; flex-direction:column; gap:5px; }
.settings-field > span { color:var(--text-main); font-size:11px; font-weight:550; }
.settings-field > small { color:var(--text-quote); font-size:9px; }
.settings-field .ui-input { margin-top:2px; }
.settings-mail-actions { display:flex; flex-wrap:wrap; gap:6px; margin-top:14px; padding-top:14px; border-top:1px solid var(--border); }
.settings-editor { margin-top:14px; padding-top:14px; border-top:1px solid var(--border); }
.settings-editor label { display:block; margin-bottom:6px; color:var(--text-main); font-size:11px; font-weight:550; }
.settings-editor-foot { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-top:8px; color:var(--text-quote); font-size:9px; }
.settings-state { display:inline-flex; align-items:center; min-height:21px; padding:0 7px; border-radius:5px; background:var(--bg-card-highlight); color:var(--text-sub); font-size:9px; white-space:nowrap; }
.settings-state.is-on { background:color-mix(in srgb,var(--success) 9%,var(--bg-card)); color:var(--success); }

@media(max-width:760px) {
  .settings-page-head { align-items:flex-start; flex-direction:column; }
  .settings-field-grid,.settings-mail-grid { grid-template-columns:1fr; }
  .settings-system-grid { grid-template-columns:1fr; }
  .settings-span-2 { grid-column:auto; }
  .settings-daily-row { align-items:flex-start; flex-wrap:wrap; }
  .settings-daily-meta { flex-basis:100%; flex-direction:row; align-items:center; }
  .settings-pager { align-items:flex-start; flex-direction:column; }
}
</style>
