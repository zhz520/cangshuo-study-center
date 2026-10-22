<template>
  <div class="landing min-h-screen flex flex-col relative overflow-hidden">
    <!-- Particles Background -->
    <div class="particles" aria-hidden="true">
      <div v-for="i in 18" :key="i" class="particle" :style="{
        left: ((i * 5.5 + 2) % 100) + '%',
        animationDelay: i * 0.37 + 's',
        animationDuration: 12 + (i % 8) + 's',
        width: 2 + (i % 3) + 'px',
        height: 2 + (i % 3) + 'px',
        opacity: 0.08 + (i % 5) * 0.04,
      }"></div>
    </div>

    <!-- Ambient Glows -->
    <div class="ambient" aria-hidden="true">
      <div class="glow glow-1"></div>
      <div class="glow glow-2"></div>
      <div class="glow glow-3"></div>
    </div>

    <!-- Transparent Navbar -->
    <header class="w-full relative z-50 border-b border-white/5 backdrop-blur-md bg-[#080810]/60">
      <div class="container mx-auto px-6 h-16 flex items-center justify-between max-w-5xl">
        <router-link to="/" class="group flex items-center text-[#e8e8f0] hover:text-white transition-colors">
          <div class="w-8 h-8 rounded-full border border-white/10 flex items-center justify-center mr-3 group-hover:border-[#a855f7]/50 group-hover:bg-[#a855f7]/10 transition-all">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path></svg>
          </div>
          <span class="font-medium text-sm">返回主页</span>
        </router-link>
        <div class="font-bold text-white tracking-wider flex items-center">
          <div class="w-2 h-2 rounded-full bg-[#a855f7] mr-2"></div>联考成绩查询
        </div>
        <div class="w-20"></div> <!-- Balance spacer -->
      </div>
    </header>

    <main class="flex-grow container mx-auto px-4 py-12 max-w-4xl relative z-10 flex flex-col justify-center">
      
      <!-- Query Form Block -->
      <transition enter-active-class="transform transition duration-500" enter-from-class="opacity-0 translate-y-8" enter-to-class="opacity-100 translate-y-0" mode="out-in">
        <div v-if="scoreDataList.length === 0" key="form" class="w-full max-w-2xl mx-auto p-8 md:p-12 glass-panel rounded-3xl relative" :class="{ 'opacity-50 pointer-events-none scale-[0.98]': isLoading }">
          
          <div class="text-center mb-10">
            <h1 class="text-3xl md:text-5xl font-extrabold text-white mb-4 tracking-tight">四川省职教高考研究联合体<br/>模拟考试</h1>
            <p class="text-[#a1a1aa] text-sm md:text-base leading-relaxed max-w-xl mx-auto">请准确输入个人信息查询 2025—2026 学年第四次联考测验结果，快速了解自身的详细学情及全省排名情况。</p>
          </div>

          <div class="space-y-6">
            <!-- Name Field -->
            <div class="group">
              <label class="block text-sm font-semibold text-[#a1a1aa] mb-2 group-focus-within:text-[#a855f7] transition-colors">考生真实姓名 <span class="text-rose-500">*</span></label>
              <div class="relative">
                <span class="absolute left-4 top-1/2 -translate-y-1/2 text-[#71717a] group-focus-within:text-[#a855f7] transition-colors">
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clip-rule="evenodd" /></svg>
                </span>
                <input v-model="form.student_name" type="text" placeholder="例如：张三"
                  class="glass-input w-full" @keyup.enter="handleQuery" />
              </div>
            </div>
            
            <div class="relative flex items-center py-2">
              <div class="flex-grow border-t border-white/10"></div>
              <span class="flex-shrink-0 mx-4 text-xs font-medium text-[#71717a] px-2 tracking-widest uppercase">通过以下任一方式精准查询</span>
              <div class="flex-grow border-t border-white/10"></div>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <!-- Exam Number Field -->
              <div class="group">
                <label class="block text-sm font-semibold text-[#a1a1aa] mb-2 group-focus-within:text-[#a855f7] transition-colors">考号查询 🎯</label>
                <div class="relative">
                  <span class="absolute left-4 top-1/2 -translate-y-1/2 text-[#71717a] group-focus-within:text-[#a855f7] transition-colors">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" /></svg>
                  </span>
                  <input v-model="form.exam_number" type="text" placeholder="准考证号"
                    class="glass-input w-full" @keyup.enter="handleQuery" />
                </div>
              </div>

              <!-- School Field Custom Select -->
              <div class="group relative" v-click-outside="closeSchoolDropdown">
                <label class="block text-sm font-semibold text-[#a1a1aa] mb-2 group-focus-within:text-[#a855f7] transition-colors">学校查询 🏫</label>
                <div class="relative" @click="isSchoolDropdownOpen = true">
                  <span class="absolute left-4 top-1/2 -translate-y-1/2 text-[#71717a] group-focus-within:text-[#a855f7] transition-colors">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" /></svg>
                  </span>
                  <input v-model="schoolSearchText" type="text" placeholder="选择或搜索学校"
                    class="glass-input w-full pr-10" @focus="isSchoolDropdownOpen = true" />
                  <span class="absolute right-4 top-1/2 -translate-y-1/2 text-[#71717a] pointer-events-none">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 transform transition-transform" :class="{ 'rotate-180': isSchoolDropdownOpen }" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" /></svg>
                  </span>
                </div>

                <!-- Dropdown List -->
                <transition enter-active-class="transition ease-out duration-200" enter-from-class="opacity-0 translate-y-1" enter-to-class="opacity-100 translate-y-0" leave-active-class="transition ease-in duration-150" leave-from-class="opacity-100 translate-y-0" leave-to-class="opacity-0 translate-y-1">
                  <div v-show="isSchoolDropdownOpen" class="absolute z-50 w-full mt-2 bg-[#121320] border border-white/10 rounded-2xl shadow-2xl max-h-60 overflow-y-auto custom-scrollbar">
                    <div v-if="filteredSchools.length === 0" class="p-4 text-center text-sm text-[#71717a]">无可匹配学校</div>
                    <ul v-else class="py-2">
                      <li @click="selectSchool('')" class="px-4 py-2.5 text-sm hover:bg-white/5 cursor-pointer transition-colors text-[#a1a1aa]" :class="{ 'text-[#a855f7] font-bold bg-[#a855f7]/10': schoolSearchText === '' }">不限学校</li>
                      <li v-for="school in filteredSchools" :key="school" @click="selectSchool(school)" class="px-4 py-2.5 text-sm hover:bg-white/5 cursor-pointer transition-colors text-[#e8e8f0]" :class="{ 'text-[#a855f7] font-bold bg-[#a855f7]/10': schoolSearchText === school }">
                        {{ school }}
                      </li>
                    </ul>
                  </div>
                </transition>
              </div>
            </div>

            <!-- Submit Button -->
            <button @click="handleQuery" :disabled="isLoading || !isFormValid"
              class="w-full mt-4 btn-primary btn-lg flex justify-center items-center disabled:opacity-50 disabled:scale-100 active:scale-[0.98]">
              <svg v-if="isLoading" class="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              {{ isLoading ? '检索神算子数据库...' : '立即查询成绩' }}
            </button>
            
            <transition enter-active-class="transition ease-out duration-300" enter-from-class="opacity-0 translate-y-2" enter-to-class="opacity-100 translate-y-0">
              <div v-if="errorMsg" class="bg-rose-500/10 border border-rose-500/20 text-rose-400 rounded-xl p-4 text-center text-sm font-medium flex items-center justify-center">
                <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                {{ errorMsg }}
              </div>
            </transition>
          </div>
        </div>

        <!-- Result View -->
        <div v-else key="result" class="w-full relative z-10 animate-fade-in-up">
          <div class="flex justify-between items-end mb-8 border-b border-white/10 pb-6">
            <div>
              <div class="inline-flex items-center justify-center space-x-2 bg-emerald-500/10 border border-emerald-500/20 px-3 py-1 rounded-full mb-3">
                <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                <span class="text-emerald-400 text-xs font-bold tracking-widest">DATA RETRIEVED</span>
              </div>
              <h2 class="text-3xl lg:text-4xl font-extrabold text-white mb-1">成绩报告诊断书</h2>
              <p class="text-[#a1a1aa] text-sm"><span v-if="scoreDataList.length > 1">侦测到 <strong class="text-[#a855f7]">{{ scoreDataList.length }}</strong> 条重名匹配记录</span><span v-else>唯一精准匹配档案</span></p>
            </div>
            <button @click="resetQuery" class="group flex items-center space-x-1.5 text-[#a1a1aa] hover:text-[#e8e8f0] font-medium px-5 py-2.5 bg-white/5 border border-white/10 rounded-xl transition-all hover:border-white/20 hover:bg-white/10">
              <svg class="w-4 h-4 group-hover:-rotate-180 transition-transform duration-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path></svg>
              <span>重新查询</span>
            </button>
          </div>
          
          <div v-for="(scoreData, index) in scoreDataList" :key="scoreData.id" class="mb-16 relative">
            <!-- Decorative line for multiple results -->
            <div v-if="index > 0" class="absolute -top-10 left-1/2 -translate-x-1/2 w-48 h-px bg-gradient-to-r from-transparent via-[#a855f7]/50 to-transparent"></div>

            <div class="glass-panel rounded-3xl p-6 md:p-10">
              <!-- Basic Info Grid -->
              <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6 mb-8">
                <div class="bg-white/5 p-5 rounded-2xl border border-white/5 relative overflow-hidden group hover:border-white/10 transition-colors">
                  <p class="text-[#71717a] text-xs font-bold uppercase tracking-wider mb-1">CANDIDATE</p>
                  <p class="text-white font-extrabold text-xl">{{ scoreData.student_name }}</p>
                </div>
                <div class="bg-white/5 p-5 rounded-2xl border border-white/5 relative overflow-hidden group hover:border-white/10 transition-colors">
                  <p class="text-[#71717a] text-xs font-bold uppercase tracking-wider mb-1">EXAM ID</p>
                  <p class="text-white font-extrabold text-xl">{{ scoreData.exam_number || '-' }}</p>
                </div>
                <div class="bg-white/5 p-5 rounded-2xl border border-white/5 relative overflow-hidden group hover:border-white/10 transition-colors lg:col-span-2">
                  <p class="text-[#71717a] text-xs font-bold uppercase tracking-wider mb-1">INSTITUTION</p>
                  <p class="text-white font-bold text-lg truncate" :title="scoreData.school_name">
                    {{ scoreData.school_name || '未登记学校地区' }} 
                    <span class="ml-2 text-[#a855f7] font-medium text-[11px] bg-[#a855f7]/15 px-2 py-0.5 rounded-md border border-[#a855f7]/30 alignment-middle">{{ scoreData.major_category || '综合' }}</span>
                  </p>
                </div>
              </div>

              <!-- Total Score Banner -->
              <div class="relative rounded-[32px] p-[1px] bg-gradient-to-br from-[#4f46e5] via-[#7c3aed] to-[#a855f7] overflow-hidden shadow-2xl shadow-[#7c3aed]/20 mb-8">
                <div class="bg-[#121320] rounded-[31px] px-6 py-12 md:py-16 flex flex-col items-center justify-center text-center relative overflow-hidden">
                  <!-- Abstract shapes -->
                  <div class="absolute -top-10 -right-10 w-64 h-64 bg-[#7c3aed]/20 rounded-full blur-[60px]"></div>
                  <div class="absolute -bottom-10 -left-10 w-64 h-64 bg-[#ec4899]/20 rounded-full blur-[60px]"></div>
                  
                  <p class="text-[#a1a1aa] font-black tracking-[0.2em] text-sm uppercase mb-6 relative z-10 flex items-center">
                    <span class="w-8 h-px bg-white/20 mr-4"></span>OVERALL PERFORMANCE<span class="w-8 h-px bg-white/20 ml-4"></span>
                  </p>
                  <div class="flex flex-col md:flex-row items-center justify-center md:space-x-16 space-y-10 md:space-y-0 relative z-10 w-full">
                    <div class="text-center w-full md:w-1/2 flex flex-col items-center">
                      <span class="text-sm font-semibold text-[#a1a1aa] mb-3">全省联考总名次</span>
                      <div class="text-7xl md:text-8xl font-black text-white drop-shadow-lg tabular-nums transform md:translate-x-4">
                        <span class="text-3xl text-[#a855f7] mr-1 italic align-top">#</span>{{ scoreData.total_rank || '-' }}
                      </div>
                    </div>
                    <div class="hidden md:block w-px h-32 bg-gradient-to-b from-transparent via-white/20 to-transparent shadow-[0_0_15px_rgba(255,255,255,0.1)]"></div>
                    <div class="text-center w-full md:w-1/2 flex flex-col items-center">
                       <span class="text-sm font-semibold text-[#a1a1aa] mb-3">测验总成绩</span>
                      <div class="text-7xl md:text-8xl font-black gradient-text drop-shadow-2xl tabular-nums transform md:-translate-x-4 flex items-baseline justify-center">
                        {{ formatScore(scoreData.total_score) }}<span class="text-2xl text-[#a1a1aa] font-bold ml-2">分</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Subject Details Grid -->
              <div class="flex items-center justify-between mb-6 border-b border-white/5 pb-4 mt-8">
                <h3 class="text-xl md:text-2xl font-black text-white flex items-center">
                  <span class="text-xl mr-3 font-normal opacity-70">📖</span>基础学科与专业数据
                </h3>
              </div>
              
              <div class="grid grid-cols-1 md:grid-cols-2 gap-5 md:gap-6">
                <!-- Inline subject rendering for premium dark look -->
                <div v-for="(sub, i) in getScoreSubjects(scoreData)" :key="i" class="bg-white/5 rounded-2xl p-5 border border-white/5 hover:border-[#a855f7]/40 transition-all duration-300 group shadow-lg">
                  <div class="flex items-center justify-between mb-4">
                     <h4 class="text-white font-bold text-lg flex items-center">
                       <span class="mr-2 opacity-80">{{ sub.icon }}</span>{{ sub.name }}
                     </h4>
                     <div class="text-3xl font-black text-white group-hover:text-[#a855f7] transition-colors tabular-nums">
                       {{ sub.data?.total || '-' }}<span class="text-sm text-[#71717a] font-medium ml-1">分</span>
                     </div>
                  </div>
                  <div class="flex items-center justify-between text-sm">
                    <div class="bg-[#080810]/50 rounded-lg px-3 py-1.5 border border-white/5 text-[#a1a1aa]">
                      客观题: <span class="text-[#e8e8f0] font-semibold">{{ sub.data?.objective || '-' }}</span>
                    </div>
                    <div class="bg-[#080810]/50 rounded-lg px-3 py-1.5 border border-white/5 text-[#a1a1aa]">
                      主观题: <span class="text-[#e8e8f0] font-semibold">{{ sub.data?.subjective || '-' }}</span>
                    </div>
                  </div>
                  <div class="mt-3 pt-3 border-t border-white/5 flex items-center justify-between text-xs">
                    <span class="text-[#71717a]">校内单科排名</span>
                    <span class="font-bold text-[#e8e8f0] bg-white/10 px-2 rounded-md">No.{{ sub.data?.rank_school || '-' }}</span>
                  </div>
                </div>
              </div>

              <!-- Ai Analysis & Data Visualization -->
              <ScoreAnalysisCard :record="scoreData" />
            </div>
          </div>
        </div>
      </transition>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { publicApi } from '@/api';
import ScoreAnalysisCard from './components/ScoreAnalysisCard.vue';

const form = ref({
  student_name: '',
  exam_number: '',
  school_name: ''
});

// UI State
const isLoading = ref(false);
const errorMsg = ref('');
const scoreDataList = ref([]);
const schoolsList = ref([]);
const isSchoolDropdownOpen = ref(false);
const schoolSearchText = ref('');

// Custom Directives for clicking outside dropdown
const vClickOutside = {
  mounted(el, binding) {
    el.clickOutsideEvent = function(event) {
      if (!(el === event.target || el.contains(event.target))) {
        binding.value(event, el);
      }
    };
    document.body.addEventListener('click', el.clickOutsideEvent);
  },
  unmounted(el) {
    document.body.removeEventListener('click', el.clickOutsideEvent);
  }
};

onMounted(async () => {
  try {
    const res = await publicApi.getSchools();
    if (res.data && res.data.success) {
      schoolsList.value = res.data.data;
    }
  } catch (e) {
    console.error("Failed to fetch schools list:", e);
  }
});

const filteredSchools = computed(() => {
  if (!schoolSearchText.value) return schoolsList.value;
  const searchLower = schoolSearchText.value.toLowerCase();
  return schoolsList.value.filter(s => s && s.toLowerCase().includes(searchLower));
});

const selectSchool = (schoolName) => {
  schoolSearchText.value = schoolName;
  form.value.school_name = schoolName;
  isSchoolDropdownOpen.value = false;
};

// Valid if name is filled AND either exam number OR school selection exists
const isFormValid = computed(() => {
  const actualSchool = schoolSearchText.value;
  return form.value.student_name.trim() !== '' && 
    (form.value.exam_number.trim() !== '' || actualSchool.trim() !== '');
});

const getScoreSubjects = (record) => {
  let details = {};
  if (record?.score_details) {
    try {
      details = typeof record.score_details === 'string' ? JSON.parse(record.score_details) : record.score_details;
    } catch (e) {}
  }
  return [
    { name: '语文', icon: '📝', data: details.chinese },
    { name: '数学', icon: '📐', data: details.math },
    { name: '英语', icon: '🔤', data: details.english },
    { name: '专业综合', icon: '💼', data: details.professional },
  ];
};

const formatScore = (val) => {
  return val != null ? Number(val).toString() : '-';
};

const handleQuery = async () => {
  if (!isFormValid.value || isLoading.value) return;
  // sync selected textual school logic
  form.value.school_name = schoolSearchText.value;
  
  isLoading.value = true;
  errorMsg.value = '';
  
  try {
    const res = await publicApi.queryScore({
      student_name: form.value.student_name.trim(),
      exam_number: form.value.exam_number.trim(),
      school_name: form.value.school_name.trim()
    });
    
    if (res.data.success && res.data.data && res.data.data.length > 0) {
      scoreDataList.value = res.data.data;
    } else {
      errorMsg.value = '档案库未检索到您的信息，核对姓名与考号、学校是否完全一致。';
    }
  } catch (err) {
    errorMsg.value = err.response?.data?.error || '网络查询线路拥挤导致失败，请稍后刷新重试。';
  } finally {
    isLoading.value = false;
  }
};

const closeSchoolDropdown = () => {
  isSchoolDropdownOpen.value = false;
};

const resetQuery = () => {
  scoreDataList.value = [];
  form.value.student_name = '';
  form.value.exam_number = '';
  schoolSearchText.value = '';
  form.value.school_name = '';
  errorMsg.value = '';
};
</script>

<style scoped>

/* Copying Landing UI Styles directly for perfect match */
.landing {
  background: #080810;
}

.particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
.particle {
  position: absolute;
  background: #6366f1;
  border-radius: 50%;
  animation: float linear infinite;
}
@keyframes float {
  0% { transform: translateY(110vh) scale(0); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translateY(-10vh) scale(1); opacity: 0; }
}

.ambient {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  animation: drift 16s ease-in-out infinite alternate;
}
.glow-1 {
  width: 70vw; height: 70vw; background: radial-gradient(circle, #4f46e520, #7c3aed15);
  top: -20%; left: -20%;
}
.glow-2 {
  width: 50vw; height: 50vw; background: radial-gradient(circle, #a855f715, #ec489910);
  bottom: 0; right: -10%; animation-delay: -6s; animation-direction: alternate-reverse;
}
.glow-3 {
  width: 30vw; height: 30vw; background: radial-gradient(circle, #06b6d415, #3b82f610);
  top: 50%; left: 40%; animation-delay: -3s;
}
@keyframes drift {
  from { transform: translate(0, 0) scale(1); }
  to { transform: translate(5%, 8%) scale(1.1); }
}

.glass-panel {
  background: rgba(14, 15, 23, 0.75);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.glass-input {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #e8e8f0;
  padding-left: 3rem;
  padding-right: 1.5rem;
  padding-top: 1rem;
  padding-bottom: 1rem;
  border-radius: 1rem;
  outline: none;
  font-size: 1rem;
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.glass-input:focus {
  background: rgba(255, 255, 255, 0.06);
  border-color: #a855f7;
  box-shadow: 0 0 0 2px rgba(168, 85, 247, 0.2);
}

::placeholder {
  color: #52525b;
}

.btn-primary {
  display: inline-flex;
  background: linear-gradient(135deg, #4f46e5, #7c3aed, #a855f7);
  color: #fff;
  border: none;
  border-radius: 1rem;
  padding: 1rem 2rem;
  font-size: 1.1rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(124, 58, 237, 0.4);
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  text-decoration: none;
}
.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 12px 32px rgba(124, 58, 237, 0.6);
}

.gradient-text {
  background: linear-gradient(120deg, #a78bfa, #60a5fa, #34d399);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(40px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.animate-fade-in-up {
  animation: fadeInUp 0.7s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

.custom-scrollbar::-webkit-scrollbar {
  width: 5px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background-color: rgba(255, 255, 255, 0.4);
}
</style>
