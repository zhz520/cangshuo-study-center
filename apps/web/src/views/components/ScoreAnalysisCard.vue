<template>
  <div class="mt-12 mb-4">
    <div class="flex items-center justify-between mb-8 border-b border-white/5 pb-4">
      <h3 class="text-xl md:text-2xl font-black text-white flex items-center">
        <span class="text-xl mr-3 font-normal opacity-70">🔬</span>多维学情诊断预测
      </h3>
    </div>

    <!-- Prediction & Status Banner -->
    <div class="bg-gradient-to-br from-[#4f46e5]/10 via-[#7c3aed]/10 to-[#ec4899]/10 border border-[#7c3aed]/20 rounded-3xl p-6 md:p-8 mb-8 relative overflow-hidden group">
      <div class="absolute -top-10 -right-10 text-9xl opacity-10 pointer-events-none group-hover:scale-110 transition-transform duration-700 blur-[2px]">{{ aiPrediction.icon }}</div>
      
      <div class="grid grid-cols-1 md:grid-cols-2 gap-8 items-center relative z-10">
        <!-- Defeat Rate & Prediction -->
        <div class="space-y-6">
          <div class="flex items-start space-x-4">
            <div class="w-12 h-12 rounded-2xl bg-gradient-to-br from-[#4f46e5] to-[#7c3aed] flex items-center justify-center shadow-lg shadow-[#7c3aed]/30 shrink-0">
              <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path></svg>
            </div>
            <div>
              <p class="text-xs md:text-sm font-semibold text-[#a1a1aa] mb-1">本次联考综合击败了</p>
              <div class="flex items-baseline space-x-2">
                <span class="text-3xl font-black text-white">{{ defeatRate }}<span class="text-xl text-[#71717a] ml-1">%</span></span>
                <span class="text-sm font-medium text-[#c084fc]">竞争者</span>
              </div>
            </div>
          </div>

          <div class="flex items-start space-x-4">
            <div class="w-12 h-12 rounded-2xl bg-gradient-to-br from-[#ec4899] to-[#f43f5e] flex items-center justify-center shadow-lg shadow-[#ec4899]/30 shrink-0">
              <span class="text-2xl pt-1">🎓</span>
            </div>
            <div>
              <p class="text-xs md:text-sm font-semibold text-[#a1a1aa] mb-1">AI 前瞻录取轨迹揭秘</p>
              <h4 class="text-lg md:text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-[#f472b6] to-[#ec4899]">{{ aiPrediction.title }}</h4>
              <p class="text-[0.85rem] text-[#d4d4d8] mt-2 leading-relaxed opacity-90">{{ aiPrediction.desc }}</p>
            </div>
          </div>
        </div>

        <!-- Radar Chart Container -->
        <div class="relative w-full h-[280px] bg-[#0c0d18]/60 backdrop-blur-xl rounded-2xl border border-white/5 shadow-inner flex items-center justify-center p-2 isolate">
           <!-- Chart decorative rings -->
           <div class="absolute inset-0 border border-white/5 rounded-2xl m-3 pointer-events-none"></div>
           <div class="absolute inset-0 border border-white/5 rounded-2xl m-6 pointer-events-none opacity-50"></div>
           <div ref="radarChartRef" class="w-full h-full relative z-10"></div>
        </div>
      </div>
    </div>

    <!-- Strengths and Weaknesses -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div class="bg-[#0c0d18]/80 backdrop-blur-md rounded-3xl p-6 md:p-8 border border-[#10b981]/20 shadow-xl relative overflow-hidden group hover:border-[#10b981]/40 transition-colors">
        <div class="absolute top-0 left-0 w-full h-[3px] bg-gradient-to-r from-[#10b981] to-[#14b8a6]"></div>
        <div class="flex items-center space-x-3 mb-5">
          <div class="w-10 h-10 rounded-xl bg-[#10b981]/15 border border-[#10b981]/30 flex items-center justify-center text-[#34d399]">
             <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 10l7-7m0 0l7 7m-7-7v18"></path></svg>
          </div>
          <h4 class="text-lg font-bold text-white">王牌优势项</h4>
        </div>
        <p class="text-white font-extrabold text-2xl mb-3 flex items-center">
          {{ strengths.name }} 
          <span class="text-xs font-bold uppercase tracking-wider text-[#10b981] bg-[#10b981]/10 border border-[#10b981]/20 px-2 py-1 rounded-md ml-3">提分核武</span>
        </p>
        <p class="text-[#a1a1aa] text-sm leading-relaxed">{{ strengths.comment }}</p>
      </div>

      <div class="bg-[#0c0d18]/80 backdrop-blur-md rounded-3xl p-6 md:p-8 border border-[#f59e0b]/20 shadow-xl relative overflow-hidden group hover:border-[#f59e0b]/40 transition-colors">
        <div class="absolute top-0 left-0 w-full h-[3px] bg-gradient-to-r from-[#f59e0b] to-[#f43f5e]"></div>
        <div class="flex items-center space-x-3 mb-5">
          <div class="w-10 h-10 rounded-xl bg-[#f59e0b]/15 border border-[#f59e0b]/30 flex items-center justify-center text-[#fbbf24]">
             <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3"></path></svg>
          </div>
          <h4 class="text-lg font-bold text-white">提分突破口</h4>
        </div>
        <p class="text-white font-extrabold text-2xl mb-3 flex items-center">
          {{ weaknesses.name }} 
          <span class="text-xs font-bold uppercase tracking-wider text-[#f59e0b] bg-[#f59e0b]/10 border border-[#f59e0b]/20 px-2 py-1 rounded-md ml-3">高爆发矿区</span>
        </p>
        <p class="text-[#a1a1aa] text-sm leading-relaxed">{{ weaknesses.comment }}</p>
      </div>
    </div>

    <!-- AI DeepSeek Diagnosis Section -->
    <div class="mt-8 bg-[#0c0d18]/80 backdrop-blur-md rounded-3xl p-6 md:p-8 border border-[#7c3aed]/20 shadow-xl relative overflow-hidden">
      <div class="absolute top-0 left-0 w-full h-[3px] bg-gradient-to-r from-[#7c3aed] via-[#a855f7] to-[#ec4899]"></div>
      <div class="flex items-center justify-between mb-6">
        <div class="flex items-center space-x-3">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-[#7c3aed] to-[#a855f7] flex items-center justify-center shadow-lg shadow-[#7c3aed]/30">
            <span class="text-white text-lg">🤖</span>
          </div>
          <div>
            <h4 class="text-lg font-bold text-white">DeepSeek AI 深度诊断</h4>
            <p class="text-xs text-[#71717a]">由 AI 升学规划导师基于真实数据生成个性化报告</p>
          </div>
        </div>
        <button v-if="!aiAnalysis && !aiLoading" @click="requestAiAnalysis"
          class="flex items-center space-x-2 px-5 py-2.5 rounded-xl text-sm font-bold transition-all
                 bg-gradient-to-r from-[#7c3aed] to-[#a855f7] text-white shadow-lg shadow-[#7c3aed]/30
                 hover:shadow-xl hover:shadow-[#7c3aed]/50 hover:scale-105 active:scale-95">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path></svg>
          <span>生成 AI 诊断报告</span>
        </button>
      </div>

      <!-- Loading State -->
      <div v-if="aiLoading" class="flex flex-col items-center justify-center py-12 space-y-4">
        <div class="relative">
          <div class="w-16 h-16 rounded-full border-4 border-[#7c3aed]/20 border-t-[#a855f7] animate-spin"></div>
          <div class="absolute inset-0 flex items-center justify-center">
            <span class="text-xl">🧠</span>
          </div>
        </div>
        <p class="text-[#a1a1aa] text-sm font-medium animate-pulse">DeepSeek 正在深度分析成绩数据...</p>
        <p class="text-[#52525b] text-xs">通常需要 5-15 秒，请耐心等待</p>
      </div>

      <!-- AI Result Display -->
      <div v-else-if="aiAnalysis" class="ai-result-content text-[#d4d4d8] text-sm md:text-[0.9rem] leading-[1.9] whitespace-pre-line">
        {{ aiAnalysis }}
      </div>

      <!-- AI Error -->
      <div v-else-if="aiError" class="text-center py-8">
        <p class="text-rose-400 text-sm mb-3">{{ aiError }}</p>
        <button @click="requestAiAnalysis" class="text-[#a855f7] text-sm font-semibold hover:underline">点击重试</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, computed, nextTick } from 'vue';
import * as echarts from 'echarts';
import { publicApi } from '@/api';

const props = defineProps({
  record: {
    type: Object,
    required: true
  }
});

const radarChartRef = ref(null);
let chartInstance = null;

// AI Analysis state
const aiAnalysis = ref(null);
const aiLoading = ref(false);
const aiError = ref(null);

const requestAiAnalysis = async () => {
  if (!localStorage.getItem('token')) {
    aiError.value = '请先登录后使用 AI 成绩分析。';
    return;
  }
  aiLoading.value = true;
  aiError.value = null;
  aiAnalysis.value = null;
  try {
    const res = await publicApi.analyzeScore({
      student_name: props.record.student_name,
      school_name: props.record.school_name,
      major_category: props.record.major_category,
      total_score: props.record.total_score,
      total_rank: props.record.total_rank,
      score_details: props.record.score_details
    });
    if (res.data.success) {
      aiAnalysis.value = res.data.data;
    } else {
      aiError.value = res.data.error || 'AI 分析失败';
    }
  } catch (e) {
    aiError.value = e.response?.data?.error || 'AI 分析服务暂时不可用，请稍后再试';
  } finally {
    aiLoading.value = false;
  }
};

// Estimate total students based on currently known db row count or mock
const TOTAL_ESTIMATE = 4497;

// Handle dynamic Max Scores for ALL Computer majors 
const MAX_SCORES = computed(() => {
  let profMax = 350;
  // USER REQUEST: 计算机专业满分都是200，都没有考操作题 (All computer majors have max score 200)
  if (props.record.major_category && props.record.major_category.includes('计算机')) {
    profMax = 200;
  }
  return {
    chinese: 150,
    math: 150,
    english: 100,
    professional: profMax
  };
});

// Extracted Data computation
const computedData = computed(() => {
  const details = typeof props.record.score_details === 'string' 
    ? JSON.parse(props.record.score_details || '{}') 
    : (props.record.score_details || {});

  return {
    chinese: parseFloat(details.chinese?.total) || 0,
    math: parseFloat(details.math?.total) || 0,
    english: parseFloat(details.english?.total) || 0,
    professional: parseFloat(details.professional?.total) || 0,
    total: parseFloat(props.record.total_score) || 0,
    rank: parseInt(props.record.total_rank) || TOTAL_ESTIMATE
  };
});

// Defeat Rate Calculation
const defeatRate = computed(() => {
  const rank = computedData.value.rank;
  if (!rank || rank > TOTAL_ESTIMATE) return "0.0";
  const rate = 100 - (rank / TOTAL_ESTIMATE * 100);
  return Math.max(0.1, rate).toFixed(1);
});

// AI Admission Prediction
const aiPrediction = computed(() => {
  const maxTotal = 150 + 150 + 100 + MAX_SCORES.value.professional;
  const pct = computedData.value.total / maxTotal;
  
  if (pct >= 0.8) {
    return { title: '🔥 第一梯队 - 稳上优质本科', desc: '你的成绩处于绝对领跑位置！保持现在的势头，冲刺省内顶级公办重本可以说是十拿九稳。', icon: '💎' };
  } else if (pct >= 0.66) {
    return { title: '✨ 核心阵营 - 本科冲刺无忧', desc: '基础扎实，分数线极具竞争力，稳定发挥即可锁定优质二本及顶级民办本科院校。', icon: '�' };
  } else if (pct >= 0.53) {
    return { title: '📈 潜力黑马 - 稳保重点高职', desc: '已经超越大量竞争者，国家级重点双高高职/大专院校大门已经敞开，还可进一步向专升本通道冲锋。', icon: '🐎' };
  } else if (pct >= 0.37) {
    return { title: '💪 蓄力节点 - 稳定升学有兜底', desc: '成绩处于中等偏强阶段，目前能够稳上大部分公办与民办大专，抓紧攻克薄弱学科能爆发极大潜力。', icon: '�' };
  } else {
    return { title: '🌱 筑基时段 - 乾坤未定皆黑马', desc: '一次模考不能定终身，当前急需查漏补缺，回归课本基础知识，只要掌握正确方法，提分空间是最大的！', icon: '💡' };
  }
});

// Calculate percentages for Strengths and Weaknesses
const subjectStats = computed(() => {
  const data = computedData.value;
  const max = MAX_SCORES.value;
  return [
    { name: '语文', id: 'chinese', score: data.chinese, max: max.chinese, pct: data.chinese / max.chinese },
    { name: '数学', id: 'math', score: data.math, max: max.math, pct: data.math / max.math },
    { name: '英语', id: 'english', score: data.english, max: max.english, pct: data.english / max.english },
    { name: '专业综合', id: 'professional', score: data.professional, max: max.professional, pct: data.professional / max.professional }
  ];
});

const strengths = computed(() => {
  const sorted = [...subjectStats.value].sort((a, b) => b.pct - a.pct);
  const best = sorted[0];
  const comments = {
    'chinese': '你的语文素养非常扎实，客观题得分率亮眼，文学素养与阅读能力拔群，请在冲刺期继续维持顶尖手感！',
    'math': '精妙的数辑思维！数学是职教高考最能拉开巨大差距的分水岭，而这正是支撑你霸榜的绝对杀手锏核心武器。',
    'english': '外语语感的双重优势展现得极为完美，在职教生中，英语的高分能让你的总分轻松跨越一整个大阶层！',
    'professional': '专业能力惊恐地扎实，理论核心理解通透。专业课一直霸占着至高的权重，它就是你总分极具统治力的基石！'
  };
  return { name: best.name, comment: comments[best.id] };
});

const weaknesses = computed(() => {
  const sorted = [...subjectStats.value].sort((a, b) => a.pct - b.pct);
  const worst = sorted[0];
  const comments = {
    'chinese': '语基底子相对单薄，建议后续极氪重心拉回古诗文名句默写和现代文套路总结，这里的提分见效是极快的！',
    'math': '面对数学切勿抗拒。务必建立错题本档案，疯狂吃透高频考点基础卷与中等题，战略性放弃压轴，你的拿分率能翻倍。',
    'english': '外语当前不幸成为吊车尾。每天清晨强行注入 30 分钟高频核心词汇与短语句型，语言的反馈绝对会让你震撼。',
    'professional': '作为权重最高的分仓，当前失血严重实在痛心。立刻断舍离，回归课本猛攻核心术语概念理论，这里全都是送分！'
  };
  return { name: worst.name, comment: comments[worst.id] };
});

const initChart = () => {
  if (chartInstance) {
    chartInstance.dispose();
  }
  if (!radarChartRef.value) return;

  chartInstance = echarts.init(radarChartRef.value);
  const option = {
    color: ['#a855f7'],
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(12, 13, 24, 0.95)',
      borderColor: 'rgba(168, 85, 247, 0.4)',
      borderWidth: 1,
      textStyle: { color: '#e8e8f0' },
      padding: [12, 16],
      className: 'backdrop-blur-md shadow-2xl rounded-xl'
    },
    radar: {
      indicator: [
        { name: '语文', max: MAX_SCORES.value.chinese },
        { name: '数学', max: MAX_SCORES.value.math },
        { name: '英语', max: MAX_SCORES.value.english },
        { name: '专业综合', max: MAX_SCORES.value.professional }
      ],
      shape: 'polygon', // Use polygon to make it look spiky and techy
      radius: '65%',
      splitNumber: 4,
      axisName: {
        color: '#e8e8f0',
        fontSize: 13,
        fontWeight: 'bold',
        padding: [3, 5]
      },
      splitLine: {
        lineStyle: {
          color: ['rgba(255, 255, 255, 0.05)', 'rgba(255, 255, 255, 0.1)'].reverse(),
          width: 1,
          type: 'solid'
        }
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(0,0,0,0)', 'rgba(255,255,255,0.01)', 'rgba(255,255,255,0.02)', 'rgba(255,255,255,0.03)']
        }
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(255, 255, 255, 0.08)'
        }
      }
    },
    series: [
      {
        name: '综合战力指标',
        type: 'radar',
        data: [
          {
            value: [
               computedData.value.chinese, 
               computedData.value.math, 
               computedData.value.english, 
               computedData.value.professional
            ],
            name: '考生成绩测算',
            symbol: 'circle',
            symbolSize: 8,
            itemStyle: {
              color: '#a855f7',
              borderColor: '#fff',
              borderWidth: 2,
            },
            lineStyle: {
              width: 3,
              color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
                { offset: 0, color: '#f472b6' },
                { offset: 1, color: '#7c3aed' }
              ])
            },
            areaStyle: {
              color: new echarts.graphic.RadialGradient(0.5, 0.5, 1, [
                { color: 'rgba(168, 85, 247, 0.5)', offset: 0 },
                { color: 'rgba(236, 72, 153, 0.05)', offset: 1 }
              ])
            }
          }
        ]
      }
    ]
  };
  chartInstance.setOption(option);
};

// Handle resize bounds
const resizeHandler = () => {
  if (chartInstance) chartInstance.resize();
};

watch(() => props.record, () => {
  nextTick(() => {
    initChart();
  });
}, { deep: true });

onMounted(() => {
  nextTick(() => { // ensure dom ready
    initChart();
    window.addEventListener('resize', resizeHandler);
  });
});

onUnmounted(() => {
  window.removeEventListener('resize', resizeHandler);
  if (chartInstance) {
    chartInstance.dispose();
  }
});
</script>
