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
  <div class="min-h-screen pb-20 transition-colors duration-500">
    
    <!-- Hero Header -->
    <header class="relative pt-12 pb-20 px-6 overflow-hidden">
      <!-- Background Blobs -->
      <div class="absolute top-0 left-1/2 -translate-x-1/2 w-full h-full z-0 pointer-events-none">
        <div class="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-indigo-500/10 rounded-full blur-[80px] animate-pulse"></div>
        <div class="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-purple-500/10 rounded-full blur-[80px] animate-pulse" style="animation-delay: 2s"></div>
      </div>

      <div class="relative z-10 max-w-4xl mx-auto">
        <div class="flex justify-between items-start mb-8">
          <div>
            <div class="flex items-baseline gap-2 mb-1">
               <h1 class="text-3xl md:text-4xl font-black text-slate-800 dark:text-white tracking-tight">
                {{ greeting }}, <span class="text-transparent bg-clip-text bg-gradient-to-r from-indigo-500 to-purple-500">{{ username }}</span>
              </h1>
              <span class="text-2xl animate-bounce">👋</span>
            </div>
            <p class="text-sm font-medium text-slate-500 dark:text-slate-400 max-w-md leading-relaxed">
              {{ currentDaily.quote }}
            </p>
          </div>
          
          <div class="flex items-center gap-3">
             <button @click="toggleTheme" class="w-10 h-10 rounded-full glass-card flex items-center justify-center text-lg transition-transform active:scale-90 hover:bg-white/50 dark:hover:bg-slate-700/50">
                {{ isDark ? "🌙" : "🌞" }}
             </button>
             <button @click="handleLogout" class="w-10 h-10 rounded-full glass-card flex items-center justify-center text-rose-500 transition-transform active:scale-90 hover:bg-rose-50 dark:hover:bg-rose-900/30">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5.636 5.636a9 9 0 1012.728 0M12 3v9" />
                </svg>
             </button>
          </div>
        </div>

        <!-- Daily Cards Row -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
             <!-- Proverb -->
             <div class="glass-card p-5 relative overflow-hidden group hover:-translate-y-1 transition-transform duration-300">
                 <div class="absolute top-0 right-0 p-3 opacity-10 group-hover:opacity-20 transition-opacity">
                     <span class="text-6xl">💡</span>
                 </div>
                 <div class="relative z-10">
                     <span class="inline-block px-2 py-1 round-lg bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400 text-[10px] font-bold rounded-md mb-2">每日谚语</span>
                     <p class="font-bold text-slate-700 dark:text-slate-200 text-lg mb-1">{{ currentDaily.proverb.en }}</p>
                     <p class="text-sm text-slate-500 dark:text-slate-400">{{ currentDaily.proverb.zh }}</p>
                 </div>
             </div>
             
             <!-- Poem -->
             <div class="glass-card p-5 relative overflow-hidden group hover:-translate-y-1 transition-transform duration-300">
                 <div class="absolute bottom-0 right-0 p-3 opacity-10 group-hover:opacity-20 transition-opacity">
                     <span class="text-6xl">📜</span>
                 </div>
                 <div class="relative z-10">
                     <span class="inline-block px-2 py-1 round-lg bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-[10px] font-bold rounded-md mb-2">每日诗词</span>
                     <p class="font-serif font-bold text-slate-700 dark:text-slate-200 text-lg mb-1">{{ currentDaily.poem.line }}</p>
                     <p class="text-sm text-slate-500 dark:text-slate-400 text-right">—— {{ currentDaily.poem.src }}</p>
                 </div>
             </div>
        </div>
      </div>
    </header>

    <main class="max-w-4xl mx-auto px-6 space-y-10 -mt-6 relative z-20">
      
      <!-- Quick Stats -->
      <section class="grid grid-cols-2 gap-4">
          <div class="glass-card p-6 flex flex-col items-center justify-center text-center hover:bg-white/80 dark:hover:bg-slate-800/80 transition-colors">
              <span class="text-4xl font-black bg-clip-text text-transparent bg-gradient-to-br from-indigo-500 to-blue-500 mb-1">{{ totalQuestions }}</span>
              <span class="text-xs font-bold text-slate-400 uppercase tracking-wider">刷题总数</span>
          </div>
          <div class="glass-card p-6 flex flex-col items-center justify-center text-center hover:bg-white/80 dark:hover:bg-slate-800/80 transition-colors">
              <span class="text-4xl font-black bg-clip-text text-transparent bg-gradient-to-br from-emerald-500 to-teal-500 mb-1">{{ correctRate }}%</span>
              <span class="text-xs font-bold text-slate-400 uppercase tracking-wider">正确率</span>
          </div>
      </section>

      <!-- Countdowns -->
      <section v-if="countdowns.length > 0">
        <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
                <span class="text-xl">⏳</span> 倒计时
            </h2>
        </div>
        <div class="flex gap-4 overflow-x-auto pb-4 scrollbar-hide -mx-2 px-2">
            <div v-for="cd in countdowns" :key="cd.id" 
                 class="min-w-[160px] glass-card p-4 flex flex-col justify-between h-[100px] relative overflow-hidden group"
            >
                <div>
                     <span class="text-xs font-bold text-slate-400 dark:text-slate-500">{{ cd.title }}</span>
                     <div class="flex items-baseline gap-1 mt-1">
                         <span class="text-2xl font-black text-slate-800 dark:text-white">{{ getDaysLeft(cd.target_date) }}</span>
                         <span class="text-xs text-slate-400">天</span>
                     </div>
                </div>
                 <div class="absolute bottom-2 right-2 text-3xl opacity-10 group-hover:scale-125 transition-transform">
                    {{ getIconForCountdown(cd.title) }}
                 </div>
            </div>
        </div>
      </section>

      <!-- Majors & Subjects -->
      <section>
          <div class="flex items-center gap-3 mb-6">
               <h2 class="text-xl font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
                   <span class="text-2xl">📚</span> 题库分类
               </h2>
               <div v-if="loading" class="animate-spin rounded-full h-4 w-4 border-2 border-indigo-500 border-t-transparent"></div>
          </div>

          <!-- Majors Grid -->
           <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3 mb-6">
              <button
                v-for="major in majors"
                :key="major.id"
                @click="selectMajor(major)"
                class="relative h-20 glass-card flex flex-col items-center justify-center p-2 transition-all duration-300 group overflow-hidden"
                :class="selectedMajor?.id === major.id ? 'ring-2 ring-indigo-500 bg-indigo-50/50 dark:bg-indigo-900/20' : 'hover:bg-white/60 dark:hover:bg-slate-800/60'"
              >
                  <span class="text-sm font-bold text-slate-700 dark:text-slate-200 z-10 relative text-center leading-tight">{{ major.name }}</span>
                  
                  <div class="absolute inset-0 bg-gradient-to-br from-indigo-500/0 to-purple-500/0 group-hover:from-indigo-500/5 group-hover:to-purple-500/5 transition-colors"></div>
                  
                  <!-- Active Indicator -->
                  <div v-if="selectedMajor?.id === major.id" class="absolute bottom-1 w-1 h-1 bg-indigo-500 rounded-full"></div>
              </button>
          </div>

          <!-- Subjects List (Collapse/Expand) -->
          <div v-if="selectedMajor" class="animate-fade-in-up bg-white/50 dark:bg-slate-800/50 rounded-[32px] p-6 border border-white/50 dark:border-slate-700/50 backdrop-blur-sm">
               <div class="flex justify-between items-center mb-4">
                   <h3 class="font-bold text-slate-700 dark:text-slate-200">{{ selectedMajor.name }} - 科目</h3>
                   <span class="text-xs bg-slate-200 dark:bg-slate-700 text-slate-500 px-2 py-1 rounded-full">{{ subjects.length }} 个科目</span>
               </div>
               
               <div v-if="loadingSubjects" class="py-8 text-center">
                   <div class="animate-spin w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full mx-auto"></div>
               </div>
               
               <div v-else-if="subjects.length === 0" class="text-center py-8 text-slate-400 text-sm">
                   暂无数据
               </div>

               <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                   <button
                        v-for="subject in subjects"
                        :key="subject.id"
                        @click="goExam(subject)"
                        class="flex items-center gap-4 p-4 bg-white dark:bg-slate-800 rounded-2xl border border-slate-100 dark:border-slate-700 hover:border-indigo-300 dark:hover:border-indigo-700 hover:shadow-lg hover:shadow-indigo-500/10 transition-all group text-left"
                   >
                        <div class="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-900/30 text-indigo-500 flex items-center justify-center text-lg shadow-inner">
                            📝
                        </div>
                        <div class="flex-1">
                            <h4 class="font-bold text-slate-700 dark:text-slate-200 text-sm group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">{{ subject.name }}</h4>
                            <p class="text-[10px] text-slate-400 mt-0.5">点击开始刷题</p>
                        </div>
                        <span class="text-slate-300 group-hover:translate-x-1 transition-transform">➜</span>
                   </button>
               </div>
          </div>
      </section>

      <!-- External Links -->
      <section v-if="externalLinks.length > 0">
           <h2 class="text-xl font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2 mb-6">
               <span class="text-2xl">🔗</span> 常用资源
           </h2>
           <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
               <a v-for="(link, index) in externalLinks" :key="link.id"
                  :href="link.url" target="_blank"
                  class="glass-card p-4 flex items-center gap-4 hover:bg-white/80 dark:hover:bg-slate-800/80 transition-colors group"
               >
                   <div class="w-10 h-10 rounded-full flex items-center justify-center text-white text-lg shadow-lg"
                        :class="index % 2 === 0 ? 'bg-gradient-to-br from-pink-500 to-rose-500' : 'bg-gradient-to-br from-cyan-400 to-blue-500'"
                   >
                       {{ getLinkIcon(link.name) }}
                   </div>
                   <div class="flex-1 min-w-0">
                       <h3 class="font-bold text-slate-700 dark:text-slate-200 text-sm truncate">{{ link.name }}</h3>
                       <p class="text-[10px] text-slate-400 truncate">{{ link.url }}</p>
                   </div>
               </a>
           </div>
      </section>

      <!-- Footer Actions -->
      <section class="pb-10 pt-4">
          <div class="grid grid-cols-3 gap-3 md:flex md:justify-center md:gap-4">
              <!-- Admin Button -->
              <router-link
                v-if="userRole === 'admin' || userRole === 'demo'"
                to="/admin"
                class="glass-card px-3 py-3 md:px-6 flex flex-col md:flex-row items-center justify-center gap-2 md:gap-3 hover:-translate-y-1 transition-transform"
              >
                  <span class="text-xl">⚙️</span>
                  <span class="font-bold text-xs md:text-base text-slate-700 dark:text-slate-200 whitespace-nowrap">后台管理</span>
              </router-link>
              
              <!-- Data Center -->
              <router-link
                to="/data"
                class="glass-card px-3 py-3 md:px-6 flex flex-col md:flex-row items-center justify-center gap-2 md:gap-3 hover:-translate-y-1 transition-transform"
              >
                  <span class="text-xl">💾</span>
                  <span class="font-bold text-xs md:text-base text-slate-700 dark:text-slate-200 whitespace-nowrap">数据中心</span>
              </router-link>

              <!-- Analysis Button -->
              <router-link
                to="/analysis"
                class="glass-card px-3 py-3 md:px-6 flex flex-col md:flex-row items-center justify-center gap-2 md:gap-3 hover:-translate-y-1 transition-transform ring-2 ring-indigo-500/20"
              >
                  <span class="text-xl">📊</span>
                  <span class="font-bold text-xs md:text-base text-slate-700 dark:text-slate-200 whitespace-nowrap">学情分析</span>
              </router-link>
          </div>
      </section>

    </main>

    <!-- Simple Footer -->
    <footer class="text-center text-slate-400 text-xs py-6 space-y-2 pb-24 md:pb-6">
        <p>© 2026 沧烁 . All rights reserved.</p>
        <p>
          <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener" class="hover:text-slate-300 transition-colors inline-flex items-center justify-center gap-1">
            🛡️ 蜀ICP备2025126838号-1
          </a>
        </p>
        <p>
          <a href="https://www.beian.gov.cn/portal/registerSystemInfo?recordcode=51172202000160" target="_blank" rel="noopener" class="hover:text-slate-300 transition-colors inline-flex items-center justify-center gap-1">
            <img src="https://img.alicdn.com/tfs/TB1..50QpXXXXX7XpXXXXXXXXXX-40-40.png" alt="公安备案" class="w-4 h-4"> 川公网安备51172202000160号
          </a>
        </p>
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
