<script setup>
import { ref, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import { data as api, publicApi } from "../api";
import Toast from "../components/ui/Toast.vue";

const router = useRouter();
const username = ref("同学");
const toast = ref(null);

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

// --- Logic ---
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

  try {
    const user = JSON.parse(localStorage.getItem("user") || "{}");
    if (user.username) username.value = user.username;

    await fetchHomeData();
    await fetchMajors();
    await fetchStats();
  } catch (err) {
    if (err.response && err.response.status === 401) router.push("/login");
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

</script>

<template>
  <div class="ui-page ui-page-wide home-page">
    <header class="ui-page-head home-head">
      <div>
        <p class="ui-eyebrow">{{ currentDate }} · 学习首页</p>
        <h1 class="ui-title">{{ greeting }}，{{ username }}</h1>
        <p class="ui-subtitle">今天从下一道题开始，进度会自动保存。</p>
      </div>
      <button type="button" class="home-refresh" aria-label="刷新首页" @click="refreshHome">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M20 7v5h-5"/><path d="M4 17v-5h5"/><path d="M6.1 9A7 7 0 0 1 18 7l2 5M18 15a7 7 0 0 1-11.9 2L4 12"/></svg>
        <span>刷新</span>
      </button>
    </header>

    <section class="home-overview" aria-label="学习概览">
      <div class="home-continue">
        <span class="home-continue-mark" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 4h5a3 3 0 0 1 3 3v13a3 3 0 0 0-3-3H4zM20 4h-5a3 3 0 0 0-3 3v13a3 3 0 0 1 3-3h5z"/></svg>
        </span>
        <div class="home-continue-copy">
          <span class="home-kicker">继续学习</span>
          <h2>{{ selectedMajor ? selectedMajor.name : '选择你的下一门科目' }}</h2>
          <p>{{ selectedMajor ? '已选专业，从题库目录继续完成练习。' : '按专业和科目查找，开始今天的学习。' }}</p>
        </div>
        <button type="button" class="ui-btn ui-btn-primary" @click="scrollToSubjects">{{ selectedMajor ? '选择科目' : '浏览题库' }} <span aria-hidden="true">→</span></button>
      </div>

      <div class="home-summary">
        <div class="home-summary-head"><strong>学习概览</strong><span>累计数据</span></div>
        <div class="home-summary-grid">
          <div><strong>{{ totalQuestions }}</strong><span>累计作答</span></div>
          <div><strong>{{ correctRate }}%</strong><span>整体正确率</span></div>
          <div><strong>{{ majors.length }}</strong><span>可浏览分类</span></div>
          <div><strong>{{ nearestCountdown ? nearestCountdown.days : '—' }}</strong><span>{{ nearestCountdown ? nearestCountdown.title : '考试倒计时' }}</span></div>
        </div>
      </div>
    </section>

    <nav class="home-tools" aria-label="学习工具">
      <span class="home-tools-label">学习工具</span>
      <RouterLink to="/score-query">联考成绩查询</RouterLink>
      <RouterLink to="/export">题库导出</RouterLink>
      <RouterLink to="/download">下载 App</RouterLink>
    </nav>

    <div class="home-body">
      <section id="subjects" class="subject-library" aria-labelledby="library-title">
        <header class="library-head">
          <div>
            <p class="library-eyebrow">题库目录</p>
            <div class="library-heading-row">
              <h2 id="library-title">按专业找到下一道题</h2>
              <span class="library-count">{{ majors.length }} 个分类</span>
            </div>
          </div>
          <span v-if="loadingSubjects" class="ui-spinner" aria-label="正在加载科目"></span>
        </header>

        <div class="library-toolbar">
          <div class="ui-search library-search">
            <svg class="ui-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
            <input v-model="subjectSearch" class="ui-input" :placeholder="selectedMajor ? '搜索当前专业下的科目' : '搜索专业分类'" :aria-label="selectedMajor ? '搜索科目' : '搜索专业'" />
          </div>
          <button v-if="selectedMajor" class="library-reset" type="button" @click="selectedMajor=null;subjects=[];subjectSearch=''">全部专业</button>
        </div>

        <div class="library-layout">
          <div class="library-index">
            <div class="library-index-head"><span>专业方向</span><span>{{ filteredMajors.length }}</span></div>
            <div class="library-index-list">
              <button v-for="(major, index) in filteredMajors" :key="major.id" type="button"
                class="library-major" :class="{ 'is-active': selectedMajor?.id === major.id }"
                :aria-pressed="selectedMajor?.id === major.id" @click="selectMajor(major)">
                <span class="library-major-order">{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="library-major-copy">
                  <strong>{{ major.name }}</strong>
                  <small>{{ major.description || '查看科目与练习' }}</small>
                </span>
                <svg class="library-chevron" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m9 5 7 7-7 7"/></svg>
              </button>
              <div v-if="filteredMajors.length === 0" class="library-empty">
                <strong>没有匹配的专业</strong>
                <span>换一个关键词试试</span>
              </div>
            </div>
          </div>

          <div v-if="selectedMajor" class="library-subject-pane">
            <div class="library-subject-head">
              <div>
                <p class="library-eyebrow">当前分类</p>
                <h3>{{ selectedMajor.name }}</h3>
              </div>
              <span class="library-count">{{ filteredSubjects.length }} 个科目</span>
            </div>
            <div v-if="loadingSubjects" class="library-loading" role="status">
              <span class="ui-spinner"></span><span>正在读取科目</span>
            </div>
            <div v-else-if="filteredSubjects.length === 0" class="library-empty library-empty-pane">
              <strong>{{ subjectSearch ? '当前分类没有匹配科目' : '当前分类暂无科目' }}</strong>
              <span>{{ subjectSearch ? '清除搜索或选择其他专业' : '目录更新后会显示在这里' }}</span>
            </div>
            <div v-else class="library-subject-list">
              <button v-for="subject in filteredSubjects" :key="subject.id" type="button" class="library-subject" @click="goExam(subject)">
                <span class="library-subject-copy">
                  <strong>{{ subject.name }}</strong>
                  <small>{{ subject.question_count ? `${subject.question_count} 道练习题` : '进入练习' }}</small>
                </span>
                <span class="library-start">开始 <span aria-hidden="true">→</span></span>
              </button>
            </div>
          </div>

          <div v-else class="library-guide">
            <span class="library-guide-rule" aria-hidden="true"></span>
            <p class="library-guide-title">从一个专业方向开始</p>
            <p class="library-guide-copy">选择左侧分类即可查看科目、题量与练习入口。搜索会同时筛选专业和当前科目。</p>
            <dl class="library-guide-points">
              <div><dt>01</dt><dd>先定位专业方向</dd></div>
              <div><dt>02</dt><dd>再选择具体科目</dd></div>
              <div><dt>03</dt><dd>进度会自动保存</dd></div>
            </dl>
          </div>
        </div>
      </section>

      <aside class="home-aside ui-stack">
        <section class="ui-card home-side-note">
          <div v-if="countdowns.length" class="home-side-block">
            <div class="ui-section-head"><h2 class="ui-section-title">考试倒计时</h2></div>
            <div v-if="countdowns.length" class="countdown-list">
              <div v-for="cd in countdowns" :key="cd.id" class="countdown-row"><span class="ui-grow">{{ cd.title }}</span><strong>{{ getDaysLeft(cd.target_date) }} 天</strong></div>
            </div>
            <p v-else class="ui-small ui-muted">暂未设置考试倒计时</p>
          </div>
          <div class="home-side-block home-side-daily">
            <span class="ui-eyebrow">每日一句</span>
            <p class="daily-primary">{{ currentDaily.proverb.en }}</p>
            <p class="ui-small ui-muted">{{ currentDaily.proverb.zh }}</p>
            <div class="ui-divider my-4"></div>
            <p class="ui-small" style="color:var(--text-main)">{{ currentDaily.poem.line }}</p>
            <p class="ui-small ui-faint text-right mt-1">{{ currentDaily.poem.src }}</p>
          </div>
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

/* Subject directory: compact two-pane navigation instead of a wall of cards. */
.subject-library {
  scroll-margin-top: 76px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-card);
}
.library-head { display:flex; align-items:flex-start; justify-content:space-between; gap:18px; padding:20px 20px 14px; }
.library-eyebrow { margin:0 0 6px; color:var(--text-quote); font-size:10px; font-weight:650; letter-spacing:.12em; text-transform:uppercase; }
.library-heading-row { display:flex; align-items:center; flex-wrap:wrap; gap:10px; }
.library-heading-row h2 { margin:0; color:var(--text-main); font-size:19px; line-height:1.35; font-weight:650; letter-spacing:-.025em; }
.library-count { display:inline-flex; align-items:center; min-height:22px; padding:0 7px; border-radius:5px; background:var(--bg-card-highlight); color:var(--text-sub); font-size:10px; white-space:nowrap; }
.library-toolbar { display:flex; align-items:center; gap:10px; padding:0 20px 14px; border-bottom:1px solid var(--border); }
.library-search { flex:1; }
.library-search .ui-input { min-height:36px; background:var(--bg-body); }
.library-reset { flex:0 0 auto; padding:7px 0 7px 10px; color:var(--primary); font-size:12px; font-weight:550; }
.library-layout { display:grid; grid-template-columns:minmax(250px,.86fr) minmax(0,1.14fr); min-height:320px; }

.library-index { min-width:0; }
.library-index-head { display:flex; justify-content:space-between; padding:12px 20px 8px; color:var(--text-quote); font-size:10px; font-weight:600; letter-spacing:.06em; text-transform:uppercase; }
.library-index-list { max-height:438px; overflow-y:auto; overscroll-behavior:contain; }
.library-major { width:100%; display:flex; align-items:center; gap:12px; min-height:58px; padding:9px 20px; border-bottom:1px solid var(--border); text-align:left; transition:background .15s ease,color .15s ease; }
.library-major:last-child { border-bottom:0; }
.library-major:hover { background:var(--bg-card-highlight); }
.library-major.is-active { background:var(--primary-soft); box-shadow:inset 2px 0 var(--primary); }
.library-major-order { width:22px; flex:0 0 22px; color:var(--text-quote); font-size:10px; font-variant-numeric:tabular-nums; }
.library-major.is-active .library-major-order { color:var(--primary); }
.library-major-copy { display:flex; flex-direction:column; gap:3px; min-width:0; flex:1; }
.library-major-copy strong { overflow:hidden; color:var(--text-main); font-size:13px; font-weight:550; text-overflow:ellipsis; white-space:nowrap; }
.library-major-copy small { overflow:hidden; color:var(--text-quote); font-size:10px; font-weight:400; text-overflow:ellipsis; white-space:nowrap; }
.library-chevron { flex:0 0 auto; color:var(--text-quote); }
.library-major.is-active .library-chevron { color:var(--primary); }
.library-subject-pane { min-width:0; border-left:1px solid var(--border); }
.library-subject-head { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; padding:16px 20px 12px; border-bottom:1px solid var(--border); }
.library-subject-head h3 { margin:0; color:var(--text-main); font-size:15px; font-weight:600; line-height:1.4; }
.library-subject-list { max-height:390px; overflow-y:auto; overscroll-behavior:contain; }
.library-subject { width:100%; display:flex; align-items:center; gap:12px; min-height:62px; padding:10px 20px; border-bottom:1px solid var(--border); text-align:left; }
.library-subject:last-child { border-bottom:0; }
.library-subject:hover { background:var(--bg-card-highlight); }
.library-subject-copy { flex:1; min-width:0; display:flex; flex-direction:column; gap:4px; }
.library-subject-copy strong { color:var(--text-main); font-size:13px; font-weight:550; }
.library-subject-copy small { color:var(--text-quote); font-size:10px; }
.library-start { flex:0 0 auto; color:var(--text-sub); font-size:11px; }
.library-subject:hover .library-start { color:var(--primary); }
.library-guide { display:flex; flex-direction:column; justify-content:center; padding:34px 30px; border-left:1px solid var(--border); background:var(--bg-card); }
.library-guide-rule { width:30px; height:2px; margin-bottom:16px; background:var(--primary); border-radius:2px; }
.library-guide-title { margin:0; color:var(--text-main); font-size:17px; line-height:1.45; font-weight:600; }
.library-guide-copy { max-width:320px; margin:8px 0 22px; color:var(--text-sub); font-size:12px; line-height:1.7; }
.library-guide-points { display:flex; flex-direction:column; gap:0; margin:0; }
.library-guide-points div { display:flex; align-items:baseline; gap:12px; padding:8px 0; border-top:1px solid var(--border); }
.library-guide-points dt { color:var(--text-quote); font-size:10px; font-variant-numeric:tabular-nums; }
.library-guide-points dd { margin:0; color:var(--text-sub); font-size:11px; }
.library-loading { display:flex; align-items:center; justify-content:center; gap:10px; min-height:190px; color:var(--text-sub); font-size:12px; }
.library-loading .ui-spinner { width:16px; height:16px; }
.library-empty { display:flex; flex-direction:column; align-items:center; gap:6px; padding:44px 20px; color:var(--text-sub); text-align:center; }
.library-empty strong { color:var(--text-main); font-size:13px; font-weight:550; }
.library-empty span { font-size:11px; }
.library-empty-pane { min-height:190px; justify-content:center; }
.home-refresh { min-height:34px; display:inline-flex; align-items:center; gap:6px; padding:0 9px; border-radius:7px; color:var(--text-sub); font-size:11px; }
.home-refresh:hover { background:var(--bg-card-highlight); color:var(--text-main); }
.home-overview { display:grid; grid-template-columns:minmax(0,1.35fr) minmax(300px,.65fr); gap:12px; margin-bottom:10px; }
.home-continue { display:flex; align-items:center; gap:14px; min-height:112px; padding:17px 19px; border:1px solid var(--border); border-radius:12px; background:var(--bg-card); }
.home-continue-mark { width:36px; height:36px; flex:0 0 36px; display:grid; place-items:center; border:1px solid color-mix(in srgb,var(--primary) 18%,var(--border)); border-radius:9px; background:var(--primary-soft); color:var(--primary); }
.home-continue-copy { display:flex; flex:1; min-width:0; flex-direction:column; gap:4px; }
.home-kicker { color:var(--primary); font-size:9px; font-weight:650; letter-spacing:.08em; text-transform:uppercase; }
.home-continue h2 { margin:0; color:var(--text-main); font-size:17px; line-height:1.35; font-weight:600; letter-spacing:-.02em; }
.home-continue p { margin:0; color:var(--text-sub); font-size:10px; line-height:1.5; }
.home-summary { padding:14px 16px; border:1px solid var(--border); border-radius:12px; background:var(--bg-card); }
.home-summary-head { display:flex; align-items:center; justify-content:space-between; padding-bottom:9px; border-bottom:1px solid var(--border); }
.home-summary-head strong { color:var(--text-main); font-size:12px; font-weight:600; }
.home-summary-head span { color:var(--text-quote); font-size:9px; }
.home-summary-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:7px; padding-top:11px; }
.home-summary-grid div { display:flex; flex-direction:column; gap:2px; min-width:0; }
.home-summary-grid strong { color:var(--text-main); font-size:19px; line-height:1.2; font-weight:650; letter-spacing:-.03em; font-variant-numeric:tabular-nums; }
.home-summary-grid span { overflow:hidden; color:var(--text-quote); font-size:9px; text-overflow:ellipsis; white-space:nowrap; }
.home-tools { display:flex; align-items:center; gap:16px; padding:9px 2px 12px; margin-bottom:4px; color:var(--text-sub); font-size:11px; }
.home-tools-label { color:var(--text-quote); font-size:9px; font-weight:600; letter-spacing:.08em; text-transform:uppercase; }
.home-tools a { color:var(--text-sub); }
.home-tools a:hover { color:var(--primary); }
.home-side-note { overflow:hidden; }
.home-side-block { padding:16px 18px; }
.home-side-block:first-child { border-top:0; }
.home-side-daily { border-top:1px solid var(--border); }
@media(max-width:760px) {
  .home-overview { grid-template-columns:1fr; }
  .home-continue { align-items:flex-start; flex-wrap:wrap; }
  .home-continue .ui-btn { width:100%; }
  .home-summary-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }
  .home-tools { overflow-x:auto; white-space:nowrap; }
  .library-head { padding:18px 16px 12px; }
  .library-toolbar { padding:0 16px 12px; }
  .library-layout { grid-template-columns:1fr; }
  .library-index-list { max-height:300px; }
  .library-major { padding-left:16px; padding-right:16px; }
  .library-subject-pane { border-left:0; border-top:1px solid var(--border); }
  .library-guide { display:none; }
}
</style>
