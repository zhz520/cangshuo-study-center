<script setup>
import { ref, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import { data as api } from "../api";

const router = useRouter();
const loading = ref(true);
const progress = ref([]);

const MODULES = [
  { key: "jy", name: "万维系统", color: "bg-blue-500", text: "text-blue-500" },
  {
    key: "jyjsj",
    name: "专业练习",
    color: "bg-purple-500",
    text: "text-purple-500",
  },
  {
    key: "aa",
    name: "语文特训",
    color: "bg-emerald-500",
    text: "text-emerald-500",
  },
];

const majors = ref([]);
const activeMajors = computed(() => majors.value);

onMounted(async () => {
  try {
    const [progRes, majorRes] = await Promise.all([
      api.getProgress(),
      api.getMajors()
    ]);
    if (Array.isArray(progRes.data)) {
      progress.value = progRes.data;
    }
    if (Array.isArray(majorRes.data)) {
      majors.value = majorRes.data;
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
});

const getStats = (key) => {
  const p = progress.value.filter((item) => item.category === key);
  const total = p.length;
  const correct = p.filter((item) => item.is_correct).length;
  const rate = total === 0 ? 0 : Math.round((correct / total) * 100);
  return { total, correct, rate };
};

const totalRate = computed(() => {
  const total = progress.value.length;
  const correct = progress.value.filter((item) => item.is_correct).length;
  return total === 0 ? 0 : Math.round((correct / total) * 100);
});

const totalCount = computed(() => progress.value.length);
</script>

<template>
  <div class="min-h-screen bg-slate-50 dark:bg-slate-900 pb-24 transition-colors duration-500">
    <!-- Top Bar -->
    <header
      class="glass-nav px-6 py-4 flex items-center gap-4 sticky top-0 z-20 shadow-sm"
    >
      <button
        @click="router.back()"
        class="w-10 h-10 rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-all active:scale-95"
      >
        <span class="text-xl">←</span>
      </button>
      <h1 class="text-xl font-black text-slate-800 dark:text-slate-100 tracking-tight">
        数据透视
      </h1>
    </header>

    <main class="p-6 max-w-lg mx-auto space-y-8 animate-fade-in-up">
      <!-- Overview Card -->
      <div class="relative group">
         <!-- Glow Effect -->
         <div class="absolute -inset-1 bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 rounded-[32px] blur opacity-25 group-hover:opacity-50 transition duration-1000"></div>
         
         <div class="relative bg-white dark:bg-slate-800 rounded-[30px] p-8 shadow-2xl flex flex-col items-center justify-center overflow-hidden">
             <!-- Background Texture -->
             <div class="absolute top-0 right-0 p-8 opacity-5">
                 <div class="text-9xl">📊</div>
             </div>

            <div class="relative w-48 h-48 flex items-center justify-center mb-6">
              <!-- Animated SVG Donut -->
              <svg viewBox="0 0 36 36" class="w-full h-full transform -rotate-90 drop-shadow-lg">
                <path
                  class="text-slate-100 dark:text-slate-700/50"
                  d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.5"
                  stroke-linecap="round"
                />
                <path
                  class="text-indigo-500 transition-all duration-1500 ease-out"
                  :stroke-dasharray="`${totalRate}, 100`"
                  d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  fill="none"
                  stroke="url(#gradient)"
                  stroke-width="2.5"
                  stroke-linecap="round"
                />
                <defs>
                   <linearGradient id="gradient" x1="0%" y1="0%" x2="100%" y2="0%">
                     <stop offset="0%" stop-color="#6366f1" />
                     <stop offset="100%" stop-color="#a855f7" />
                   </linearGradient>
                </defs>
              </svg>
              <div class="absolute inset-0 flex flex-col items-center justify-center">
                <span class="text-5xl font-black bg-clip-text text-transparent bg-gradient-to-br from-indigo-500 to-purple-500">{{ totalRate }}<span class="text-2xl">%</span></span>
                <span class="text-xs font-bold text-slate-400 uppercase tracking-widest mt-1">综合正确率</span>
              </div>
            </div>

            <div class="grid grid-cols-2 gap-8 w-full">
              <div class="text-center p-4 rounded-2xl bg-slate-50 dark:bg-slate-900/50">
                <div class="text-2xl font-black text-slate-800 dark:text-slate-100 mb-1">
                  {{ totalCount }}
                </div>
                <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider">总刷题量</div>
              </div>
              <div class="text-center p-4 rounded-2xl bg-emerald-50 dark:bg-emerald-900/20">
                <div class="text-2xl font-black text-emerald-500 mb-1">
                  {{ progress.filter((i) => i.is_correct).length }}
                </div>
                <div class="text-[10px] font-bold text-emerald-600/60 dark:text-emerald-400/60 uppercase tracking-wider">做对题数</div>
              </div>
            </div>
         </div>
      </div>

      <!-- Module Stats List -->
      <div class="space-y-4">
         <h2 class="text-sm font-black text-slate-400 uppercase tracking-widest px-2">专业分析</h2>
         
         <div v-if="loading" class="space-y-4">
             <div v-for="i in 3" :key="i" class="h-24 rounded-2xl bg-slate-200 dark:bg-slate-800 animate-pulse"></div>
         </div>
         
         <div v-else class="space-y-4">
            <div
              v-for="(mod, index) in activeMajors"
              :key="mod.id"
              class="glass-card p-5 group hover:-translate-y-1 transition-all duration-300"
              :style="{ animationDelay: `${index * 100}ms` }"
            >
              <div class="flex items-center justify-between mb-3">
                <div class="flex items-center gap-3">
                   <div class="w-10 h-10 rounded-xl flex items-center justify-center text-lg font-bold text-white shadow-lg" :class="[
                      index % 3 === 0 ? 'bg-gradient-to-br from-blue-400 to-blue-600 shadow-blue-500/30' : 
                      index % 3 === 1 ? 'bg-gradient-to-br from-purple-400 to-purple-600 shadow-purple-500/30' : 
                      'bg-gradient-to-br from-pink-400 to-pink-600 shadow-pink-500/30'
                   ]">
                       {{ mod.name.charAt(0) }}
                   </div>
                   <div>
                       <h3 class="font-bold text-slate-800 dark:text-slate-100">{{ mod.name }}</h3>
                       <p class="text-[10px] text-slate-400 font-medium">Topic ID: {{ mod.id }}</p>
                   </div>
                </div>
                <div class="text-right">
                    <span class="text-xl font-black" :class="[
                      getStats(mod.id).rate >= 80 ? 'text-emerald-500' :
                      getStats(mod.id).rate >= 60 ? 'text-indigo-500' : 'text-rose-500'
                    ]">{{ getStats(mod.id).rate }}%</span>
                </div>
              </div>

              <!-- Progress Bar -->
              <div class="h-2.5 bg-slate-100 dark:bg-slate-700/50 rounded-full overflow-hidden mb-3">
                 <div class="h-full rounded-full transition-all duration-1000 relative" 
                      :class="[
                          index % 3 === 0 ? 'bg-blue-500' : 
                          index % 3 === 1 ? 'bg-purple-500' : 'bg-pink-500'
                      ]"
                      :style="{ width: getStats(mod.id).rate + '%' }">
                      <div class="absolute inset-0 bg-white/20 animate-pulse-slow"></div>
                 </div>
              </div>
              
              <div class="flex justify-between items-center text-xs font-medium text-slate-400">
                  <span class="flex items-center gap-1">
                      <span class="w-1.5 h-1.5 rounded-full bg-slate-300"></span>
                      已刷 {{ getStats(mod.id).total }}
                  </span>
                  <span class="flex items-center gap-1">
                      <span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
                      正确 {{ getStats(mod.id).correct }}
                  </span>
                  <span class="flex items-center gap-1">
                      <span class="w-1.5 h-1.5 rounded-full bg-rose-400"></span>
                      错误 {{ getStats(mod.id).total - getStats(mod.id).correct }}
                  </span>
              </div>
            </div>
         </div>
         
         <div v-if="!loading && activeMajors.length === 0" class="text-center py-10 opacity-50">
             暂无数据
         </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.glass-nav {
  background-color: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(229, 231, 235, 0.5);
}
.dark .glass-nav {
  background-color: rgba(15, 23, 42, 0.8);
  border-bottom: 1px solid rgba(30, 41, 59, 0.5);
}

.glass-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 1.5rem;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
}
.dark .glass-card {
  background: rgba(30, 41, 59, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.animate-fade-in-up {
  animation: fadeInUp 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pulse-slow {
    0%, 100% { opacity: 0.5; }
    50% { opacity: 0.8; }
}
.animate-pulse-slow {
    animation: pulse-slow 3s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
</style>
