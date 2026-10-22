<script setup>
import { ref, onMounted, computed } from 'vue';
import { admin, publicApi } from '../../api';
import DashboardCharts from './components/DashboardCharts.vue';

const stats = ref({
  users: 0,
  questions: 0,
  charts: null // For chart data
});
const dailyQuote = ref(null);
const loading = ref(true);
const isDark = ref(false);
const username = ref('Admin');

// Time-based greeting in English
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 5) return 'Good night';
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
});

const toggleTheme = () => {
  isDark.value = !isDark.value;
  if (isDark.value) {
    document.documentElement.classList.add('dark');
    localStorage.setItem('theme', 'dark');
  } else {
    document.documentElement.classList.remove('dark');
    localStorage.setItem('theme', 'light');
  }
};

onMounted(async () => {
  // Init theme
  const savedTheme = localStorage.getItem('theme');
  if (savedTheme === 'dark' || (!savedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    isDark.value = true;
    document.documentElement.classList.add('dark');
  }

  // Get username
  const userStr = localStorage.getItem('user');
  if (userStr) {
    try {
      const user = JSON.parse(userStr);
      username.value = user.username || 'Admin';
    } catch (e) {
      console.error('Error parsing user data', e);
    }
  }

  try {
    const [usersRes, questionsRes, homeDataRes, statsRes] = await Promise.all([
      admin.getUsers(),
      admin.getQuestions({ limit: 1 }),
      publicApi.getRandomDailyContent(),
      admin.getStats()
    ]);
    stats.value.users = usersRes?.data?.length || 0;
    stats.value.questions = questionsRes?.data?.pagination?.total || 0;
    stats.value.charts = statsRes?.data || null; // Store chart data

    // Handle Daily Content Response
    // API returns { quote: string, proverb: {en, zh}, poetry: {line, src} }
    const data = homeDataRes.data;
    const types = [];

    if (data.quote) types.push({ content: data.quote, translation: 'Daily Quote' });
    if (data?.proverb?.en) types.push({ content: data.proverb.en, translation: data.proverb.zh || '' });
    if (data?.poetry?.line) types.push({ content: data.poetry.line, translation: data.poetry.src || '' });

    if (types.length > 0) {
      // Pick one randomly
      dailyQuote.value = types[Math.floor(Math.random() * types.length)];
    } else {
       // Fallback if no data
       dailyQuote.value = {
         content: "Learning never exhausts the mind.",
         translation: "学而不厌"
       };
    }

  } catch (e) {
    console.error('Failed to fetch dashboard data', e);
    // Fallback on error
    dailyQuote.value = {
         content: "Believe you can and you're halfway there.",
         translation: "相信自己，你已经成功了一半"
    };
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="relative animate-fade-in-up">

    <!-- Header Section -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-10">
      <div class="space-y-1">
        <h2 class="text-3xl sm:text-4xl font-black bg-gradient-to-r from-indigo-600 to-purple-600 bg-clip-text text-transparent dark:from-indigo-400 dark:to-purple-400 font-['Segoe_UI',_sans-serif] tracking-tight">
          {{ greeting }}, {{ username }}
        </h2>
        <div class="flex items-center gap-2 text-slate-400 dark:text-slate-500 text-xs font-bold uppercase tracking-widest">
          <span :class="['w-2.5 h-2.5 rounded-full animate-pulse', username === 'admintest' ? 'bg-orange-500 shadow-[0_0_10px_rgba(249,115,22,0.5)]' : 'bg-emerald-500 box-shadow-emerald']"></span>
          <span>{{ username === 'admintest' ? '系统运行正常 · 演示模式 (仅浏览)' : '系统运行正常 · 管理员权限已激活' }}</span>
        </div>
      </div>
      
      <button 
        @click="toggleTheme" 
        class="group flex items-center self-start md:self-auto gap-3 px-5 py-3 rounded-2xl bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-all shadow-sm border border-slate-100 dark:border-slate-700 active:scale-95 hover:shadow-md"
      >
        <span class="text-xl group-hover:rotate-12 transition-transform">{{ isDark ? '🌙' : '🌞' }}</span>
        <span class="text-sm font-black">{{ isDark ? '深夜模式' : '明亮模式' }}</span>
      </button>
    </div>

    <!-- Stats Grid -->
    <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-6 mb-10">
      <!-- Users Card -->
      <div class="relative overflow-hidden bg-gradient-to-br from-indigo-500 to-indigo-700 p-8 rounded-[32px] shadow-lg shadow-indigo-500/20 dark:shadow-none group hover:-translate-y-1 transition-transform duration-300">
        <div class="absolute -right-6 -top-6 text-9xl text-white opacity-[0.08] rotate-12 group-hover:scale-110 group-hover:rotate-6 transition-transform duration-500 select-none">👥</div>
        <!-- Shine effect -->
        <div class="absolute top-0 right-0 w-full h-full bg-gradient-to-bl from-white/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none"></div>

        <div class="relative z-10">
          <div class="text-indigo-100/80 text-xs font-black uppercase tracking-widest mb-3">活跃用户总数</div>
          <div class="text-5xl font-black text-white flex items-baseline gap-1 tracking-tight">
            <span v-if="loading" class="animate-pulse">...</span>
            <span v-else>{{ stats.users }}</span>
          </div>
          <div class="mt-6 flex items-center gap-2">
            <span class="text-[10px] text-indigo-700 font-bold bg-white px-3 py-1 rounded-full shadow-sm flex items-center gap-1">
                <span class="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse"></span>
                LIVE
            </span>
            <span class="text-[10px] text-indigo-200">数据实时同步中</span>
          </div>
        </div>
      </div>

      <!-- Questions Card -->
      <div class="relative overflow-hidden bg-gradient-to-br from-fuchsia-500 to-fuchsia-700 p-8 rounded-[32px] shadow-lg shadow-fuchsia-500/20 dark:shadow-none group hover:-translate-y-1 transition-transform duration-300">
         <div class="absolute -right-6 -top-6 text-9xl text-white opacity-[0.08] rotate-12 group-hover:scale-110 group-hover:rotate-6 transition-transform duration-500 select-none">📚</div>
         <!-- Shine effect -->
         <div class="absolute top-0 right-0 w-full h-full bg-gradient-to-bl from-white/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none"></div>

         <div class="relative z-10">
          <div class="text-fuchsia-100/80 text-xs font-black uppercase tracking-widest mb-3">系统题目总量</div>
          <div class="text-5xl font-black text-white flex items-baseline gap-1 tracking-tight">
            <span v-if="loading" class="animate-pulse">...</span>
            <span v-else>{{ stats.questions }}</span>
          </div>
          <div class="mt-6 flex items-center gap-2">
            <span class="text-[10px] text-fuchsia-100 font-bold border border-white/30 px-3 py-1 rounded-full">库容持续增长</span>
          </div>
        </div>
      </div>

      <!-- Daily Quote Card -->
      <div class="sm:col-span-2 bg-slate-900 dark:bg-slate-800 rounded-[32px] p-8 shadow-xl shadow-slate-200/50 dark:shadow-none text-white relative overflow-hidden flex flex-col justify-center min-h-[200px] group hover:shadow-2xl transition-shadow duration-300">
        <!-- Background Image -->
        <div class="absolute inset-0 bg-gradient-to-r from-slate-900 to-slate-800 z-0"></div>
        <div class="absolute top-0 right-0 p-8 opacity-10 text-8xl font-serif leading-none select-none group-hover:scale-110 transition-transform duration-700">"</div>
        
        <div v-if="dailyQuote" class="relative z-10">
          <div class="text-xl sm:text-2xl font-bold font-serif leading-relaxed mb-6 max-w-lg">
            {{ dailyQuote.content }}
          </div>
          <div class="flex items-center gap-4">
            <span class="w-8 h-[3px] bg-gradient-to-r from-indigo-500 to-purple-500 rounded-full"></span>
            <span class="text-sm font-bold text-slate-400 uppercase tracking-widest">{{ dailyQuote.translation }}</span>
          </div>
        </div>
        <div v-else class="text-center opacity-30 animate-pulse text-xs uppercase tracking-widest relative z-10">Updating Daily Inspiration...</div>
      </div>
    </div>

    <!-- Data Visualization Charts -->
    <DashboardCharts 
      v-if="stats.charts" 
      :stats="stats.charts" 
      :loading="loading" 
      :is-dark="isDark"
    />

    <!-- Quick Navigation -->
    <div class="flex items-center gap-3 mb-8">
      <div class="w-1.5 h-8 bg-gradient-to-b from-indigo-500 to-purple-500 rounded-full"></div>
      <h3 class="text-xl font-black text-slate-800 dark:text-slate-100 tracking-tight">快捷管理入口</h3>
    </div>
    
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-6">
      <router-link to="/admin/users" class="group bg-white dark:bg-slate-800 p-8 rounded-[32px] border border-slate-100 dark:border-slate-700 shadow-sm hover:shadow-xl hover:border-indigo-200 dark:hover:border-indigo-500/30 transition-all duration-300 active:scale-[0.98] relative overflow-hidden">
        <div class="absolute top-0 right-0 w-24 h-24 bg-indigo-50 dark:bg-indigo-500/5 rounded-bl-[60px] -mr-4 -mt-4 transition-transform group-hover:scale-110"></div>
        
        <div class="w-16 h-16 rounded-2xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 flex items-center justify-center text-3xl mb-6 group-hover:scale-110 group-hover:rotate-3 transition-transform duration-300 shadow-sm">👥</div>
        <div class="font-black text-xl text-slate-800 dark:text-slate-100 mb-2">用户管理</div>
        <p class="text-sm text-slate-400 leading-relaxed font-medium">配置用户资料与管理权限级别</p>
      </router-link>

      <router-link to="/admin/questions" class="group bg-white dark:bg-slate-800 p-8 rounded-[32px] border border-slate-100 dark:border-slate-700 shadow-sm hover:shadow-xl hover:border-fuchsia-200 dark:hover:border-fuchsia-500/30 transition-all duration-300 active:scale-[0.98] relative overflow-hidden">
         <div class="absolute top-0 right-0 w-24 h-24 bg-fuchsia-50 dark:bg-fuchsia-500/5 rounded-bl-[60px] -mr-4 -mt-4 transition-transform group-hover:scale-110"></div>

        <div class="w-16 h-16 rounded-2xl bg-fuchsia-50 dark:bg-fuchsia-500/10 text-fuchsia-600 dark:text-fuchsia-400 flex items-center justify-center text-3xl mb-6 group-hover:scale-110 group-hover:rotate-3 transition-transform duration-300 shadow-sm">📚</div>
        <div class="font-black text-xl text-slate-800 dark:text-slate-100 mb-2">题库管理</div>
        <p class="text-sm text-slate-400 leading-relaxed font-medium">维护海量题目数据的增删改查</p>
      </router-link>

      <router-link to="/admin/settings" class="group bg-white dark:bg-slate-800 p-8 rounded-[32px] border border-slate-100 dark:border-slate-700 shadow-sm hover:shadow-xl hover:border-emerald-200 dark:hover:border-emerald-500/30 transition-all duration-300 active:scale-[0.98] relative overflow-hidden">
         <div class="absolute top-0 right-0 w-24 h-24 bg-emerald-50 dark:bg-emerald-500/5 rounded-bl-[60px] -mr-4 -mt-4 transition-transform group-hover:scale-110"></div>

        <div class="w-16 h-16 rounded-2xl bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center text-3xl mb-6 group-hover:scale-110 group-hover:rotate-3 transition-transform duration-300 shadow-sm">⚙️</div>
        <div class="font-black text-xl text-slate-800 dark:text-slate-100 mb-2">系统配置</div>
        <p class="text-sm text-slate-400 leading-relaxed font-medium">自定义首页内容与各模块策略参数</p>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.animate-fade-in-up {
  animation: fadeInUp 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
