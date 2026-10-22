<script setup>
import { ref, onMounted, computed, watch } from "vue";
import { useRouter } from "vue-router";
import { data as api, publicApi } from "../api";
import Toast from "../components/ui/Toast.vue";
import ConfirmDialog from "../components/ui/ConfirmDialog.vue";

const router = useRouter();
const username = ref("同学");
const userRole = ref("");
const loading = ref(true);
const toast = ref(null);
const confirmDialog = ref(null);

// --- State for New Structure ---
const countdowns = ref([]);
const externalLinks = ref([]);
const majors = ref([]);
const subjects = ref([]);
const selectedMajor = ref(null);
const loadingSubjects = ref(false);
const subjectSearch = ref("");
const filteredMajors = computed(() => {
  const q = subjectSearch.value.trim().toLowerCase();
  if (!q) return majors.value;
  return majors.value.filter(m => m.name.toLowerCase().includes(q));
});
const filteredSubjects = computed(() => {
  const q = subjectSearch.value.trim().toLowerCase();
  if (!q) return subjects.value;
  return subjects.value.filter(s => s.name.toLowerCase().includes(q));
});
const nearestCountdown = computed(() => {
  const rows = countdowns.value.map(c => ({ ...c, days: Number(getDaysLeft(c.target_date)) })).filter(c => Number.isFinite(c.days));
  return rows.sort((a,b) => a.days - b.days)[0] || null;
});
const scrollToSubjects = () => document.getElementById("subjects")?.scrollIntoView({ behavior: "smooth", block: "start" });
const refreshHome = async () => { await Promise.all([fetchHomeData(), fetchMajors(), fetchStats(), initDailyContent()]); };

// ... (Daily Content and Stats logic remains same)

const handleLogout = async () => {
  const confirmed = await confirmDialog.value.confirm({
    title: "确认退出",
    message: "您确定要退出登录吗？",
    confirmText: "退出",
    cancelText: "取消",
    type: "danger"
  });
  
  if (confirmed) {
    try {
      const { auth } = await import("../api");
      await auth.logout();
    } catch (_) {}
    localStorage.removeItem("token");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("user");
    router.push("/login");
  }
};

// --- Daily Content ---
const currentDaily = ref({
  quote: "学而时习之，不亦说乎",
  proverb: { en: "Practice makes perfect.", zh: "熟能生巧" },
  poem: { line: "欲穷千里目，更上一层楼", src: "王之涣" }
});
const greeting = ref("Hello");
const currentDate = ref("");
const currentYear = ref(new Date().getFullYear());

// --- Stats ---
const totalQuestions = ref(0);
const correctRate = ref(0);

const fetchStats = async () => {
    try {
        const res = await api.getProgress();
        const progress = res.data || [];
        totalQuestions.value = progress.length;
        if (progress.length > 0) {
            const correctCount = progress.filter(p => p.is_correct).length;
            correctRate.value = Math.round((correctCount / progress.length) * 100);
        } else {
            correctRate.value = 0;
        }
    } catch (err) {
        console.error("Failed to fetch progress stats:", err);
    }
};

// --- Theme ---
const isDark = ref(false);

// --- Logic ---
const initTheme = () => {
  const saved = localStorage.getItem("theme");
  if (
    saved === "dark" ||
    (!saved && window.matchMedia("(prefers-color-scheme: dark)").matches)
  ) {
    isDark.value = true;
    document.documentElement.classList.add("dark");
  }
};

const toggleTheme = () => {
  isDark.value = !isDark.value;
  if (isDark.value) {
    document.documentElement.classList.add("dark");
    localStorage.setItem("theme", "dark");
  } else {
    document.documentElement.classList.remove("dark");
    localStorage.setItem("theme", "light");
  }
};

const initDailyContent = async () => {
    try {
        const res = await publicApi.getRandomDailyContent();
        if (res.data) {
             if (res.data.quote) currentDaily.value.quote = res.data.quote;
             if (res.data.proverb) currentDaily.value.proverb = res.data.proverb; 
             if (res.data.poetry) currentDaily.value.poem = res.data.poetry;
        }
    } catch (e) {
        console.error("Failed to fetch daily content:", e);
    }

  // Date & Time
  const now = new Date();
  currentDate.value = now.toLocaleDateString("zh-CN", {
    month: "short",
    day: "numeric",
  });
  const hour = now.getHours();
  if (hour < 6) greeting.value = "夜深了";
  else if (hour < 12) greeting.value = "早上好";
  else if (hour < 18) greeting.value = "下午好";
  else greeting.value = "晚上好";
};

// --- Fetch Data ---
const fetchHomeData = async () => {
    try {
        const res = await publicApi.getHomeData();
        if (res.data) {
            countdowns.value = res.data.countdowns || [];
            externalLinks.value = res.data.externalLinks || [];
        }
    } catch (err) {
        console.error("Failed to fetch home data:", err);
    }
};

const fetchMajors = async () => {
    try {
        const res = await api.getMajors();
        majors.value = res.data;
    } catch (err) {
        console.error("Failed to fetch majors:", err);
        toast.value?.add("加载专业列表失败", "error");
    }
};

const selectMajor = async (major) => {
    if (selectedMajor.value?.id === major.id) {
        // Deselect if clicking same
        selectedMajor.value = null;
        subjects.value = [];
        return;
    }
    
    selectedMajor.value = major;
    loadingSubjects.value = true;
    subjects.value = [];
    
    try {
        const res = await api.getSubjects(major.id);
        subjects.value = res.data;
        // Scroll to subjects
        setTimeout(() => {
            document.getElementById('subjects-section')?.scrollIntoView({ behavior: 'smooth' });
        }, 100);
    } catch (err) {
        console.error("Failed to fetch subjects:", err);
        toast.value?.add("加载科目失败", "error");
    } finally {
        loadingSubjects.value = false;
    }
};

const goExam = (subject) => {
    router.push(`/exam/${subject.id}`);
};



onMounted(async () => {
  initDailyContent();
  initTheme();

  try {
    const user = JSON.parse(localStorage.getItem("user") || "{}");
    if (user.username) username.value = user.username;
    if (user.role) userRole.value = user.role;
    
    await fetchHomeData();
    await fetchMajors();
    await fetchStats();
  } catch (err) {
    if (err.response && err.response.status === 401) router.push("/login");
  } finally {
    loading.value = false;
  }
});



// --- Original UI Helpers ---
const getDaysLeft = (dateStr) => {
  const now = new Date();
  now.setHours(0, 0, 0, 0);
  const currentYear = now.getFullYear();
  let target = new Date(`${currentYear}-${dateStr}`);
  if (isNaN(target.getTime())) return '??';
  if (target < now) {
    target = new Date(`${currentYear + 1}-${dateStr}`);
  }
  return Math.ceil((target - now) / (1000 * 60 * 60 * 24));
};

const getIconForCountdown = (title) => {
  if (title.includes('计算机')) return '💻';
  if (title.includes('高考')) return '🎓';
  return '⏰';
};

const getIconBg = (index) => {
  const bgs = [
    'from-indigo-500 to-indigo-600 text-white',
    'from-orange-400 to-orange-600 text-white shadow-orange-500/20',
    'from-cyan-400 to-cyan-600 text-white shadow-cyan-500/20',
    'from-rose-400 to-rose-600 text-white shadow-rose-500/20'
  ];
  return bgs[index % bgs.length];
};

const getLinkIcon = (name) => {
  if (name.includes('指导')) return '📚';
  if (name.includes('智教')) return '🧠';
  if (name.includes('技能')) return '⌨️';
  if (name.includes('天天')) return '📊';
  return '🔗';
};
</script>

<template>
  <div class="ui-page ui-page-wide home-page">
    <header class="ui-page-head home-head">
      <div>
        <p class="ui-eyebrow">{{ currentDate }} · 学习首页</p>
        <h1 class="ui-title">{{ greeting }}，{{ username }}</h1>
        <p class="ui-subtitle">{{ currentDaily.quote }}</p>
      </div>
      <button class="ui-btn ui-btn-outline ui-btn-sm" @click="refreshHome">刷新</button>
    </header>

    <section class="home-continue ui-card ui-card-pad">
      <div class="ui-row-between">
        <div class="ui-grow">
          <span class="ui-badge ui-badge-blue">继续学习</span>
          <h2 class="home-continue-title">{{ selectedMajor ? selectedMajor.name : "今天从哪一科开始？" }}</h2>
          <p class="ui-small ui-muted">{{ selectedMajor ? "已选择专业，从下方科目列表继续。" : "按专业和科目查找，立即进入练习。" }}</p>
        </div>
        <button class="ui-btn ui-btn-dark" @click="scrollToSubjects">选择科目 <span aria-hidden="true">→</span></button>
      </div>
    </section>

    <section class="ui-grid ui-grid-4 home-metrics" aria-label="学习概览">
      <div class="ui-card ui-card-pad ui-metric"><span class="ui-metric-value">{{ totalQuestions }}</span><span class="ui-metric-label">累计作答</span></div>
      <div class="ui-card ui-card-pad ui-metric"><span class="ui-metric-value">{{ correctRate }}%</span><span class="ui-metric-label">整体正确率</span></div>
      <div class="ui-card ui-card-pad ui-metric"><span class="ui-metric-value">{{ majors.length }}</span><span class="ui-metric-label">可浏览专业</span></div>
      <div class="ui-card ui-card-pad ui-metric"><span class="ui-metric-value">{{ nearestCountdown ? nearestCountdown.days : "—" }}</span><span class="ui-metric-label">{{ nearestCountdown ? nearestCountdown.title : "考试倒计时" }}</span></div>
    </section>

    <nav class="home-quick-links" aria-label="常用入口">
      <RouterLink to="/analysis" class="ui-card home-quick-item"><span>学情分析</span><span class="ui-faint">查看趋势 ›</span></RouterLink>
      <RouterLink to="/data" class="ui-card home-quick-item"><span>学习数据</span><span class="ui-faint">导入与导出 ›</span></RouterLink>
      <RouterLink to="/score-query" class="ui-card home-quick-item"><span>成绩查询</span><span class="ui-faint">联考成绩 ›</span></RouterLink>
      <RouterLink to="/export" class="ui-card home-quick-item"><span>题库导出</span><span class="ui-faint">授权导出 ›</span></RouterLink>
    </nav>

    <div class="home-body">
      <section id="subjects" class="ui-card ui-card-pad subject-section">
        <div class="ui-row-between">
          <div><h2 class="ui-section-title">题库</h2><p class="ui-small ui-muted">选择专业后进入科目</p></div>
          <span v-if="loadingSubjects" class="ui-spinner" aria-label="正在加载科目"></span>
        </div>
        <div class="ui-search home-search">
          <svg class="ui-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
          <input v-model="subjectSearch" class="ui-input" :placeholder="selectedMajor ? '搜索科目' : '搜索专业'" :aria-label="selectedMajor ? '搜索科目' : '搜索专业'" />
        </div>
        <div class="ui-row home-major-tabs">
          <button class="ui-chip ui-chip-soft" :class="{active:selectedMajor===null}" @click="selectedMajor=null;subjects=[]">全部专业</button>
          <button v-for="major in filteredMajors.slice(0, 8)" :key="major.id" class="ui-chip ui-chip-soft" :class="{active:selectedMajor?.id===major.id}" @click="selectMajor(major)">{{ major.name.replace(/类$/, "") }}</button>
        </div>

        <div v-if="selectedMajor" class="subject-list">
          <div class="ui-row-between subject-list-head"><button class="ui-btn ui-btn-ghost ui-btn-sm" @click="selectedMajor=null;subjects=[]">← 全部专业</button><span class="ui-small ui-muted">{{ selectedMajor.name }}</span></div>
          <div v-if="loadingSubjects" class="ui-loading"><span class="ui-spinner mx-auto"></span><p class="mt-3">正在加载科目</p></div>
          <div v-else-if="filteredSubjects.length === 0" class="ui-empty">当前专业暂无匹配科目</div>
          <div v-else class="ui-list-card">
            <button v-for="subject in filteredSubjects" :key="subject.id" class="ui-row-button subject-row" @click="goExam(subject)">
              <span class="subject-mark">{{ subject.name.slice(0,1) }}</span>
              <span class="ui-grow"><strong>{{ subject.name }}</strong><small class="block ui-small ui-muted">{{ subject.question_count ? `${subject.question_count} 题` : "查看题目" }}</small></span>
              <span class="ui-faint">开始 ›</span>
            </button>
          </div>
        </div>
        <div v-else class="major-directory">
          <button v-for="major in filteredMajors" :key="major.id" class="ui-row-button major-row" @click="selectMajor(major)">
            <span class="subject-mark">{{ major.name.slice(0,1) }}</span>
            <span class="ui-grow"><strong>{{ major.name }}</strong><small class="block ui-small ui-muted">查看科目与练习</small></span>
            <span class="ui-faint">›</span>
          </button>
        </div>
      </section>

      <aside class="home-aside ui-stack">
        <section class="ui-card ui-card-pad">
          <div class="ui-section-head"><h2 class="ui-section-title">考试倒计时</h2></div>
          <div v-if="countdowns.length" class="countdown-list">
            <div v-for="cd in countdowns" :key="cd.id" class="countdown-row"><span class="ui-grow">{{ cd.title }}</span><strong>{{ getDaysLeft(cd.target_date) }} 天</strong></div>
          </div>
          <p v-else class="ui-small ui-muted">暂未设置考试倒计时</p>
        </section>
        <section class="ui-card ui-card-pad daily-note">
          <span class="ui-eyebrow">每日一句</span>
          <p class="daily-primary">{{ currentDaily.proverb.en }}</p>
          <p class="ui-small ui-muted">{{ currentDaily.proverb.zh }}</p>
          <div class="ui-divider my-4"></div>
          <p class="ui-small" style="color:var(--text-main)">{{ currentDaily.poem.line }}</p>
          <p class="ui-small ui-faint text-right mt-1">{{ currentDaily.poem.src }}</p>
        </section>
        <section v-if="externalLinks.length" class="ui-card ui-card-pad">
          <div class="ui-section-head"><h2 class="ui-section-title">常用资源</h2></div>
          <a v-for="link in externalLinks" :key="link.id" :href="link.url" target="_blank" rel="noopener" class="resource-link">{{ link.name }}<span>↗</span></a>
        </section>
      </aside>
    </div>

    <footer class="home-footer">
      <span>© 2026 沧烁</span><a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener">蜀ICP备2025126838号-1</a><a href="https://www.beian.gov.cn/portal/registerSystemInfo?recordcode=51172202000160" target="_blank" rel="noopener">川公网安备51172202000160号</a>
    </footer>
    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialog" />
  </div>
</template>

<style scoped>
/* Glass Card styling is global, but we can add specific tweaks here */
.glass-card {
    transition: transform 0.2s, box-shadow 0.2s, background-color 0.3s;
}

.scrollbar-hide::-webkit-scrollbar {
    display: none;
}
.scrollbar-hide {
    -ms-overflow-style: none;
    scrollbar-width: none;
}
</style>
