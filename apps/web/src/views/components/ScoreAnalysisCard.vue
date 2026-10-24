<template>
  <section class="score-diagnosis">
    <h3 class="ui-section-title">成绩分析</h3>
    <div class="score-diagnosis-overview">
      <div>
        <span class="ui-small ui-muted">本次成绩超过约</span>
        <p class="score-diagnosis-value">{{ defeatRate }}<small>%</small></p>
        <p class="ui-small ui-muted">按本次联考排名计算，仅作参考。</p>
        <strong class="score-diagnosis-summary">{{ aiPrediction.title }}</strong>
        <p class="ui-small ui-muted">{{ aiPrediction.desc }}</p>
      </div>
      <div ref="radarChartRef" class="score-diagnosis-chart"></div>
    </div>
    <div class="score-diagnosis-subjects">
      <div><span class="ui-small ui-muted">相对优势</span><h4>{{ strengths.name }}</h4><p>{{ strengths.comment }}</p></div>
      <div><span class="ui-small ui-muted">优先复习</span><h4>{{ weaknesses.name }}</h4><p>{{ weaknesses.comment }}</p></div>
    </div>
    <div class="score-diagnosis-ai">
      <div class="ui-row-between"><div><h4 class="ui-section-title">AI 学情建议</h4><p class="ui-small ui-muted">结合成绩与各科表现生成建议。</p></div><button v-if="!aiAnalysis" class="ui-btn ui-btn-outline ui-btn-sm" :disabled="aiLoading" @click="requestAiAnalysis">{{ aiLoading ? '分析中…' : '生成报告' }}</button></div>
      <div v-if="aiLoading" class="ui-loading"><span class="ui-spinner" style="display:inline-block"></span><p>正在分析成绩…</p></div>
      <div v-else-if="aiAnalysis" class="score-diagnosis-report">{{ aiAnalysis }}</div>
      <p v-else-if="aiError" class="score-error" role="alert">{{ aiError }} <button @click="requestAiAnalysis">重试</button></p>
    </div>
  </section>
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
    color: ['#2563eb'],
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(12, 13, 24, 0.95)',
      borderColor: 'rgba(168, 85, 247, 0.4)',
      borderWidth: 1,
      textStyle: { color: '#64748b' },
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
        color: '#64748b',
        fontSize: 13,
        fontWeight: 'bold',
        padding: [3, 5]
      },
      splitLine: {
        lineStyle: {
          color: ['#e2e8f0', '#cbd5e1'].reverse(),
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
          color: '#cbd5e1'
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
