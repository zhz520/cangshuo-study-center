<script setup>
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
  <div class="px-3 py-4 sm:p-6 space-y-5">
    <div
      class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4"
    >
      <div>
        <h2
          class="text-2xl font-black text-slate-800 dark:text-white tracking-tight"
        >
          系统与内容
        </h2>
        <div class="flex items-center gap-2 mt-1">
          <p class="text-sm text-slate-500 dark:text-slate-400 font-medium">
            配置系统参数与首页内容
          </p>
          <span class="text-slate-300 dark:text-slate-600">|</span>
          <router-link
            to="/admin/users"
            class="text-xs font-bold text-indigo-500 hover:text-indigo-600 dark:text-indigo-400 dark:hover:text-indigo-300 transition-colors"
            >返回用户管理</router-link
          >
        </div>
      </div>
    </div>

    <!-- Custom Tabs -->
    <div
      class="flex p-1 bg-slate-100 dark:bg-slate-800/50 rounded-xl overflow-x-auto no-scrollbar border border-slate-200 dark:border-slate-700/50 gap-1"
    >
      <button
        v-for="tab in [
          { id: 'settings', label: '系统开关', icon: '⚙️' },
          { id: 'countdowns', label: '倒计时', icon: '⏳' },
          { id: 'links', label: '外部链接', icon: '🔗' },
          { id: 'daily', label: '每日内容', icon: '📅' },
        ]"
        :key="tab.id"
        @click="changeTab(tab.id)"
        class="flex-1 min-w-[60px] px-2 sm:px-4 py-2 sm:py-2.5 rounded-lg text-[10px] sm:text-xs font-black transition-all whitespace-nowrap flex flex-col sm:flex-row items-center justify-center gap-0.5 sm:gap-2"
        :class="
          activeTab === tab.id
            ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-300 shadow-sm'
            : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-700/30'
        "
      >
        <span class="text-base sm:text-sm">{{ tab.icon }}</span>
        <span class="hidden xs:inline sm:inline">{{ tab.label }}</span>
      </button>
    </div>

    <!-- 1. System Settings -->
    <div v-if="activeTab === 'settings'" class="animate-fade-in space-y-6">
      <div class="glass-card p-4 sm:p-6">
        <h3
          class="text-sm font-black text-slate-400 dark:text-slate-500 uppercase tracking-widest mb-6"
        >
          Global Configuration
        </h3>

        <div
          class="flex flex-wrap items-center justify-between p-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-2xl border border-slate-100 dark:border-slate-700 hover:border-indigo-100 dark:hover:border-indigo-500/20 transition-colors gap-3"
        >
          <div class="flex gap-4 items-center flex-1 min-w-0">
            <div
              class="w-12 h-12 flex-shrink-0 rounded-xl flex items-center justify-center text-2xl"
              :class="
                settings.registration_open === 'true'
                  ? 'bg-green-100 text-green-600 dark:bg-green-900/20 dark:text-green-400'
                  : 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500'
              "
            >
              📝
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
            class="relative w-14 h-7 rounded-full transition-colors duration-300 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 flex-shrink-0"
            :class="
              settings.registration_open === 'true'
                ? 'bg-green-500'
                : 'bg-slate-200 dark:bg-slate-700'
            "
          >
            <span
              class="absolute top-1 left-1 bg-white w-5 h-5 rounded-full shadow-sm transition-transform duration-300"
              :class="
                settings.registration_open === 'true'
                  ? 'translate-x-7'
                  : 'translate-x-0'
              "
            ></span>
          </button>
        </div>
      </div>

      <!-- AI Day Limit -->
      <div class="glass-card p-4 sm:p-6">
        <h3
          class="text-sm font-black text-slate-400 dark:text-slate-500 uppercase tracking-widest mb-6"
        >
          AI Usage Configuration
        </h3>

        <div
          class="flex flex-wrap items-center justify-between p-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-2xl border border-slate-100 dark:border-slate-700 hover:border-indigo-100 dark:hover:border-indigo-500/20 transition-colors gap-3"
        >
          <div class="flex gap-4 items-center flex-1 min-w-0">
            <div
              class="w-12 h-12 flex-shrink-0 rounded-xl flex items-center justify-center text-2xl bg-purple-100 text-purple-600 dark:bg-purple-900/20 dark:text-purple-400"
            >
              🤖
            </div>
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
          class="flex flex-wrap items-center justify-between p-4 ml-16 bg-slate-50/50 dark:bg-slate-800/30 rounded-2xl border border-slate-100 dark:border-slate-700 gap-3"
        >
          <div class="flex gap-3 items-center flex-1 min-w-0">
            <span class="text-lg">👤</span>
            <div class="min-w-0">
              <h3 class="font-bold text-slate-700 dark:text-slate-300 text-sm">
                普通用户
              </h3>
              <p class="text-[10px] text-slate-400">每天可用次数</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <input
              v-model="settings.ai_day_limit_free"
              type="number"
              min="0"
              class="w-20 px-3 py-2 bg-white dark:bg-slate-700 border border-slate-200 dark:border-slate-600 rounded-lg text-center font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
            />
            <button
              @click="updateSetting('ai_day_limit_free', settings.ai_day_limit_free)"
              class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-bold transition-all active:scale-95"
            >
              保存
            </button>
          </div>
        </div>

        <!-- Plus user AI limit -->
        <div
          class="flex flex-wrap items-center justify-between p-4 ml-16 bg-purple-50/50 dark:bg-purple-900/10 rounded-2xl border border-purple-100 dark:border-purple-800/30 gap-3"
        >
          <div class="flex gap-3 items-center flex-1 min-w-0">
            <span class="text-lg">✨</span>
            <div class="min-w-0">
              <h3 class="font-bold text-purple-700 dark:text-purple-300 text-sm">
                Plus 订阅用户
              </h3>
              <p class="text-[10px] text-purple-400">每天可用次数</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <input
              v-model="settings.ai_day_limit_plus"
              type="number"
              min="0"
              class="w-20 px-3 py-2 bg-white dark:bg-slate-700 border border-purple-200 dark:border-purple-700 rounded-lg text-center font-bold text-purple-700 dark:text-purple-200 focus:outline-none focus:ring-2 focus:ring-purple-500/50"
            />
            <button
              @click="updateSetting('ai_day_limit_plus', settings.ai_day_limit_plus)"
              class="px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg text-xs font-bold transition-all active:scale-95"
            >
              保存
            </button>
          </div>
        </div>
      </div>

      <!-- APP Announcement -->
      <div class="glass-card p-4 sm:p-6">
        <h3
          class="text-sm font-black text-slate-400 dark:text-slate-500 uppercase tracking-widest mb-6"
        >
          APP Announcement Configuration
        </h3>

        <div
          class="flex flex-wrap items-center justify-between p-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-2xl border border-slate-100 dark:border-slate-700 hover:border-indigo-100 dark:hover:border-indigo-500/20 transition-colors gap-3 mb-4"
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
              📢
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
            class="relative w-14 h-7 rounded-full transition-colors duration-300 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 flex-shrink-0"
            :class="
              settings.app_announcement_enabled === 'true'
                ? 'bg-green-500'
                : 'bg-slate-200 dark:bg-slate-700'
            "
          >
            <span
              class="absolute top-1 left-1 bg-white w-5 h-5 rounded-full shadow-sm transition-transform duration-300"
              :class="
                settings.app_announcement_enabled === 'true'
                  ? 'translate-x-7'
                  : 'translate-x-0'
              "
            ></span>
          </button>
        </div>

        <div
          class="flex flex-col p-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-2xl border border-slate-100 dark:border-slate-700 gap-3"
        >
          <label
            class="block text-xs font-bold text-slate-500 dark:text-slate-400"
            >公告内容</label
          >
          <textarea
            v-model="settings.app_announcement"
            rows="3"
            class="w-full p-4 rounded-xl border bg-white dark:bg-slate-700/50 border-slate-200 dark:border-slate-600 text-slate-800 dark:text-slate-100 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all"
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
              class="px-6 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95"
            >
              保存公告内容
            </button>
          </div>
        </div>
      </div>

      <!-- SMTP Email Configuration -->
      <div class="glass-card p-4 sm:p-6 mt-6">
        <h3
          class="text-sm font-black text-slate-400 dark:text-slate-500 uppercase tracking-widest mb-6"
        >
          SMTP Email Configuration
        </h3>

        <div class="space-y-4">
          <!-- SMTP Host -->
          <div
            class="flex flex-col p-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-2xl border border-slate-100 dark:border-slate-700 gap-3"
          >
            <div class="flex justify-between items-center">
              <label class="block text-xs font-bold text-slate-500 dark:text-slate-400">
                邮件Host <span class="text-[10px] font-normal ml-2">请根据所选发件平台获取</span>
              </label>
              <button
                @click="updateSetting('smtp_host', settings.smtp_host || '')"
                class="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-[10px] font-bold transition-all active:scale-95"
              >
                保存系统设置
              </button>
            </div>
            <input
              v-model="settings.smtp_host"
              type="text"
              class="w-full px-4 py-2 border bg-white dark:bg-slate-700 border-slate-200 dark:border-slate-600 rounded-xl text-sm font-medium text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
              placeholder="smtp.qq.com"
            />
          </div>

          <!-- 发信邮箱 -->
          <div
            class="flex flex-col p-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-2xl border border-slate-100 dark:border-slate-700 gap-3"
          >
            <div class="flex justify-between items-center">
              <label class="block text-xs font-bold text-slate-500 dark:text-slate-400">
                发信邮箱
              </label>
              <button
                @click="updateSetting('smtp_user', settings.smtp_user || '')"
                class="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-[10px] font-bold transition-all active:scale-95"
              >
                保存系统设置
              </button>
            </div>
            <input
              v-model="settings.smtp_user"
              type="text"
              class="w-full px-4 py-2 border bg-white dark:bg-slate-700 border-slate-200 dark:border-slate-600 rounded-xl text-sm font-medium text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
              placeholder="2156628804@qq.com"
            />
          </div>

          <!-- 发信授权码 -->
          <div
            class="flex flex-col p-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-2xl border border-slate-100 dark:border-slate-700 gap-3"
          >
            <div class="flex justify-between items-center">
              <label class="block text-xs font-bold text-slate-500 dark:text-slate-400">
                发信授权码 <span class="text-[10px] font-normal ml-2">请根据所选发件平台获取</span>
              </label>
              <button
                @click="updateSetting('smtp_pass', settings.smtp_pass || '')"
                class="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-[10px] font-bold transition-all active:scale-95"
              >
                保存系统设置
              </button>
            </div>
            <input
              v-model="settings.smtp_pass"
              type="password"
              class="w-full px-4 py-2 border bg-white dark:bg-slate-700 border-slate-200 dark:border-slate-600 rounded-xl text-sm font-medium text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
              placeholder="********"
            />
          </div>

          <!-- 收信邮箱 (管理员) -->
          <div
            class="flex flex-col p-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-2xl border border-slate-100 dark:border-slate-700 gap-3"
          >
            <div class="flex justify-between items-center">
              <label class="block text-xs font-bold text-slate-500 dark:text-slate-400">
                接收报错提醒邮箱 (管理员邮箱)
              </label>
              <button
                @click="updateSetting('admin_email_for_reports', settings.admin_email_for_reports || '')"
                class="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-[10px] font-bold transition-all active:scale-95"
              >
                保存系统设置
              </button>
            </div>
            <input
              v-model="settings.admin_email_for_reports"
              type="text"
              class="w-full px-4 py-2 border bg-white dark:bg-slate-700 border-slate-200 dark:border-slate-600 rounded-xl text-sm font-medium text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
              placeholder="2183649927@qq.com"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 2. Countdowns -->
    <div v-if="activeTab === 'countdowns'" class="animate-fade-in space-y-6">
      <div
        class="flex justify-between items-center bg-white dark:bg-slate-800 p-4 rounded-2xl shadow-sm border border-slate-100 dark:border-slate-700"
      >
        <h3 class="font-bold text-slate-700 dark:text-slate-200 ml-2">
          倒计时列表
        </h3>
        <button
          @click="openAddCountdown"
          class="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-bold transition-all active:scale-95 shadow-lg shadow-indigo-200/50 dark:shadow-none"
        >
          + 新增倒计时
        </button>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div
          v-for="item in countdowns"
          :key="item.id"
          class="glass-card p-5 group flex justify-between items-start hover:-translate-y-1 transition-transform"
        >
          <div class="flex gap-4">
            <div class="text-3xl">{{ item.icon }}</div>
            <div>
              <div class="font-black text-slate-800 dark:text-slate-100">
                {{ item.title }}
              </div>
              <div
                class="text-xs font-mono font-bold text-indigo-500 dark:text-indigo-400 mt-1 bg-indigo-50 dark:bg-indigo-900/30 px-2 py-0.5 rounded-lg inline-block"
              >
                {{ item.target_date }}
              </div>
            </div>
          </div>
          <div
            class="flex flex-col gap-2 opacity-0 group-hover:opacity-100 transition-opacity"
          >
            <button
              @click="openEditCountdown(item)"
              class="text-slate-400 hover:text-indigo-500 transition-colors"
              title="编辑"
            >
              ✏️
            </button>
            <button
              @click="deleteItem('countdown', item.id)"
              class="text-slate-400 hover:text-rose-500 transition-colors"
              title="删除"
            >
              🗑️
            </button>
          </div>
        </div>
        <div
          v-if="countdowns.length === 0"
          class="col-span-full py-12 text-center text-slate-400 font-bold border-2 border-dashed border-slate-200 dark:border-slate-700 rounded-2xl"
        >
          暂无倒计时配置
        </div>
      </div>
    </div>

    <!-- 3. Links -->
    <div v-if="activeTab === 'links'" class="animate-fade-in space-y-6">
      <div
        class="flex justify-between items-center bg-white dark:bg-slate-800 p-4 rounded-2xl shadow-sm border border-slate-100 dark:border-slate-700"
      >
        <h3 class="font-bold text-slate-700 dark:text-slate-200 ml-2">
          快捷链接列表
        </h3>
        <button
          @click="openAddLink"
          class="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-bold transition-all active:scale-95 shadow-lg shadow-indigo-200/50 dark:shadow-none"
        >
          + 新增链接
        </button>
      </div>

      <div
        class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4"
      >
        <div
          v-for="item in links"
          :key="item.id"
          class="glass-card p-5 group relative overflow-hidden hover:shadow-lg transition-all"
        >
          <div
            class="absolute top-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity z-10"
          >
            <button
              @click="openEditLink(item)"
              class="p-1.5 bg-white/80 dark:bg-slate-700/80 rounded-lg text-indigo-500 hover:text-indigo-600 transition-colors"
            >
              ✏️
            </button>
            <button
              @click="deleteItem('link', item.id)"
              class="p-1.5 bg-white/80 dark:bg-slate-700/80 rounded-lg text-rose-500 hover:text-rose-600 transition-colors"
            >
              🗑️
            </button>
          </div>

          <div class="flex flex-col items-center text-center">
            <div
              :class="[
                item.color,
                'w-14 h-14 rounded-2xl flex items-center justify-center text-3xl text-white shadow-lg mb-4 transform group-hover:scale-110 transition-transform duration-300',
              ]"
            >
              {{ item.icon }}
            </div>
            <h3 class="font-black text-slate-800 dark:text-slate-100 mb-1">
              {{ item.name }}
            </h3>
            <p class="text-xs text-slate-400 mb-3 line-clamp-2 h-8">
              {{ item.description }}
            </p>
            <a
              :href="item.url"
              target="_blank"
              class="text-[10px] font-mono text-indigo-400 bg-indigo-50 dark:bg-indigo-900/20 px-2 py-1 rounded max-w-full truncate hover:underline"
            >
              {{ item.url }}
            </a>
          </div>
        </div>
        <div
          v-if="links.length === 0"
          class="col-span-full py-12 text-center text-slate-400 font-bold border-2 border-dashed border-slate-200 dark:border-slate-700 rounded-2xl"
        >
          暂无外部链接配置
        </div>
      </div>
    </div>

    <!-- 4. Daily Content -->
    <div v-if="activeTab === 'daily'" class="animate-fade-in space-y-6">
      <div
        class="bg-white dark:bg-slate-800 p-5 rounded-[28px] shadow-sm border border-slate-100 dark:border-slate-700 flex flex-col sm:flex-row justify-between items-center gap-4"
      >
        <div class="flex items-center gap-4 w-full sm:w-auto">
          <span
            class="text-sm font-bold text-slate-500 dark:text-slate-400 whitespace-nowrap"
            >筛选类型:</span
          >
          <div class="flex p-1 bg-slate-100 dark:bg-slate-700 rounded-lg">
            <button
              @click="
                dailyContentType = 'all';
                loadDailyContents();
              "
              class="px-3 py-1 rounded-md text-xs font-bold transition-colors"
              :class="
                dailyContentType === 'all'
                  ? 'bg-white dark:bg-slate-600 text-indigo-600 dark:text-indigo-300 shadow-sm'
                  : 'text-slate-500 dark:text-slate-400'
              "
            >
              全部
            </button>
            <button
              @click="
                dailyContentType = 'quote';
                loadDailyContents();
              "
              class="px-3 py-1 rounded-md text-xs font-bold transition-colors"
              :class="
                dailyContentType === 'quote'
                  ? 'bg-white dark:bg-slate-600 text-blue-600 dark:text-blue-300 shadow-sm'
                  : 'text-slate-500 dark:text-slate-400'
              "
            >
              语录
            </button>
            <button
              @click="
                dailyContentType = 'proverb';
                loadDailyContents();
              "
              class="px-3 py-1 rounded-md text-xs font-bold transition-colors"
              :class="
                dailyContentType === 'proverb'
                  ? 'bg-white dark:bg-slate-600 text-purple-600 dark:text-purple-300 shadow-sm'
                  : 'text-slate-500 dark:text-slate-400'
              "
            >
              谚语
            </button>
            <button
              @click="
                dailyContentType = 'poetry';
                loadDailyContents();
              "
              class="px-3 py-1 rounded-md text-xs font-bold transition-colors"
              :class="
                dailyContentType === 'poetry'
                  ? 'bg-white dark:bg-slate-600 text-green-600 dark:text-green-300 shadow-sm'
                  : 'text-slate-500 dark:text-slate-400'
              "
            >
              诗词
            </button>
          </div>
        </div>
        <button
          @click="openAddDaily"
          class="w-full sm:w-auto px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-black shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95"
        >
          + 新增内容
        </button>
      </div>

      <div class="space-y-4">
        <div
          v-for="item in dailyContents"
          :key="item.id"
          class="glass-card p-6 border-l-4 hover:bg-white/80 dark:hover:bg-slate-800/80 transition-all flex flex-col md:flex-row gap-4 justify-between group"
          :class="{
            'border-l-blue-500': item.type === 'quote',
            'border-l-purple-500': item.type === 'proverb',
            'border-l-green-500': item.type === 'poetry',
          }"
        >
          <div class="flex-1">
            <div class="flex items-center gap-2 mb-2">
              <span
                class="px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-wider"
                :class="{
                  'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300':
                    item.type === 'quote',
                  'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300':
                    item.type === 'proverb',
                  'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300':
                    item.type === 'poetry',
                }"
              >
                {{
                  item.type === "quote"
                    ? "Motivation"
                    : item.type === "proverb"
                      ? "Proverb"
                      : "Poetry"
                }}
              </span>
              <span
                class="text-[10px] text-slate-300 dark:text-slate-600 font-mono"
                >#{{ item.id }}</span
              >
            </div>
            <p
              class="text-slate-800 dark:text-slate-100 font-serif font-medium text-lg mb-2 leading-relaxed"
            >
              “{{ item.content }}”
            </p>
            <p
              v-if="item.translation"
              class="text-sm text-slate-500 dark:text-slate-400 italic"
            >
              {{ item.type !== "quote" ? "" : "— " }} {{ item.translation }}
            </p>
          </div>

          <div
            class="flex md:flex-col gap-2 opacity-0 group-hover:opacity-100 transition-opacity self-start md:self-center"
          >
            <button
              @click="openEditDaily(item)"
              class="px-3 py-1.5 bg-slate-100 hover:bg-indigo-50 text-slate-600 hover:text-indigo-600 dark:bg-slate-700 dark:hover:bg-indigo-900/30 dark:text-slate-300 dark:hover:text-indigo-400 rounded-lg text-xs font-bold transition-colors"
            >
              编辑
            </button>
            <button
              @click="deleteDaily(item.id)"
              class="px-3 py-1.5 bg-slate-100 hover:bg-rose-50 text-slate-600 hover:text-rose-600 dark:bg-slate-700 dark:hover:bg-rose-900/30 dark:text-slate-300 dark:hover:text-rose-400 rounded-lg text-xs font-bold transition-colors"
            >
              删除
            </button>
          </div>
        </div>

        <div
          v-if="dailyContents.length === 0"
          class="text-center py-20 bg-slate-50/50 dark:bg-slate-800/30 border-2 border-dashed border-slate-200 dark:border-slate-700 rounded-[32px] text-slate-400"
        >
          <div class="text-3xl mb-2">🍃</div>
          暂无内容数据
        </div>
      </div>

      <!-- Pagination -->
      <div
        v-if="dailyPagination.totalPages > 1"
        class="flex flex-col sm:flex-row justify-between items-center pt-6 border-t border-slate-200 dark:border-slate-700 gap-4"
      >
        <div class="text-xs font-bold text-slate-400 uppercase tracking-wider">
          Total {{ dailyPagination.total }} items • Page
          {{ dailyPagination.page }} of {{ dailyPagination.totalPages }}
        </div>
        <div class="flex items-center gap-3">
          <button
            @click="handleDailyPageChange(dailyPagination.page - 1)"
            :disabled="dailyPagination.page === 1"
            class="w-8 h-8 flex items-center justify-center rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            ←
          </button>

          <div
            class="flex items-center gap-2 bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 p-1"
          >
            <input
              v-model="jumpPageNum"
              @keyup.enter="handleDailyJump"
              type="number"
              min="1"
              :max="dailyPagination.totalPages"
              class="w-10 text-center text-xs font-bold bg-transparent outline-none text-slate-700 dark:text-slate-200"
            />
            <button
              @click="handleDailyJump"
              class="px-2 py-1 text-[10px] bg-indigo-50 text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-300 rounded font-black hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors uppercase"
            >
              GO
            </button>
          </div>

          <button
            @click="handleDailyPageChange(dailyPagination.page + 1)"
            :disabled="dailyPagination.page === dailyPagination.totalPages"
            class="w-8 h-8 flex items-center justify-center rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            →
          </button>
        </div>
      </div>
    </div>

    <!-- MODALS -->

    <!-- Daily Content Modal -->
    <div
      v-if="showDailyModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-md p-4 transition-all animate-fade-in"
    >
      <div
        class="glass-card w-full max-w-lg p-8 shadow-2xl animate-scale-up border border-white/50 dark:border-slate-600/50"
      >
        <h3 class="text-xl font-black text-slate-800 dark:text-white mb-6">
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
              class="w-full p-4 rounded-xl border bg-slate-50 dark:bg-slate-800/50 border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-100 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all"
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
              class="w-full px-4 py-3 rounded-xl border bg-slate-50 dark:bg-slate-800/50 border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-100 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all"
            />
          </div>
        </div>
        <div class="flex gap-3 mt-8">
          <button
            @click="showDailyModal = false"
            class="flex-1 py-3 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs font-black transition-colors"
          >
            取消
          </button>
          <button
            @click="saveDaily"
            class="flex-1 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-black shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95"
          >
            保存更改
          </button>
        </div>
      </div>
    </div>

    <!-- Countdown Modal -->
    <div
      v-if="showCountdownModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-md p-4 transition-all animate-fade-in"
    >
      <div
        class="glass-card w-full max-w-sm p-6 shadow-2xl animate-scale-up border border-white/50 dark:border-slate-600/50 max-h-[90vh] overflow-y-auto custom-scrollbar"
      >
        <h3 class="text-xl font-black text-slate-800 dark:text-white mb-6">
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
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all"
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
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all"
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
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all"
            />
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button
            @click="showCountdownModal = false"
            class="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs font-black transition-colors"
          >
            取消
          </button>
          <button
            @click="saveCountdown"
            class="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-black shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95"
          >
            保存
          </button>
        </div>
      </div>
    </div>

    <!-- Link Modal -->
    <div
      v-if="showLinkModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-md p-4 transition-all animate-fade-in"
    >
      <div
        class="glass-card w-full max-w-md p-6 shadow-2xl animate-scale-up border border-white/50 dark:border-slate-600/50 max-h-[90vh] overflow-y-auto custom-scrollbar"
      >
        <h3 class="text-xl font-black text-slate-800 dark:text-white mb-6">
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
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all"
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
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all font-mono"
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
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all"
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
                class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all"
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
              class="w-full px-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all"
            />
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button
            @click="showLinkModal = false"
            class="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-xs font-black transition-colors"
          >
            取消
          </button>
          <button
            @click="saveLink"
            class="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-black shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95"
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
