<script setup>
import { sanitizeHtml } from "../utils/sanitize-html";
import { ref, onMounted, computed, watch, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { data as api } from '../api';
import * as echarts from 'echarts';
import markdownit from 'markdown-it';
import Toast from '../components/ui/Toast.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const toast = ref(null);

const md = markdownit({ html: true, breaks: true });

// Data Refs
const detailedStats = ref(null);
const analysisData = ref(null);
const aiAnalysisResult = ref(null);
const isGeneratingAi = ref(false);

// Filters
const majors = ref([]);
const subjects = ref([]);
const selectedMajor = ref('');
const selectedSubject = ref('');

// Chart Instances
let radarChartInstance = null;
let heatmapChartInstance = null;
let subjectChartInstance = null;
let typeChartInstance = null;

const radarChartRef = ref(null);
const heatmapChartRef = ref(null);
const subjectChartRef = ref(null);
const typeChartRef = ref(null);

const loadStructure = async () => {
    try {
        const res = await api.getMajors();
        majors.value = res.data;
        if (majors.value.length > 0) {
           selectedMajor.value = majors.value[0].id;
        }
    } catch (e) {
        console.error(e);
    }
};

watch(selectedMajor, async (val) => {
    if (!val) {
        subjects.value = [];
        selectedSubject.value = '';
        return;
    }
    try {
        const res = await api.getSubjects(val);
        subjects.value = res.data;
        if (subjects.value.length > 0) {
            selectedSubject.value = subjects.value[0].id;
        } else {
             selectedSubject.value = '';
        }
    } catch (e) { console.error(e); }
});

// If subject changes, only reload the subject-specific part
watch(selectedSubject, async (val) => {
    if (!val) return;
    await loadSubjectAnalysis();
});

// Load Overall Detailed Stats + Subject Specific Analysis
const loadAllData = async () => {
    loading.value = true;
    try {
        // Fetch detailed stats first
        const detailRes = await api.getDetailedStats();
        detailedStats.value = detailRes.data;

        // If we have subjects, we wait for selectedSubject watch to trigger loadSubjectAnalysis
        // But if not, or initially, we need to load it explicitly once we have a selectedSubject
        await loadSubjectAnalysis();
        
    } catch (e) {
        toast.value?.add('获取分析数据失败', 'error');
    } finally {
        loading.value = false;
        
        // Render general charts after DOM updates
        nextTick(() => {
            initHeatmapChart();
            initSubjectChart();
            initTypeChart();
        });
    }
};

const loadSubjectAnalysis = async () => {
    if (!selectedSubject.value) return;
    try {
        const res = await api.getAnalysis(selectedSubject.value);
        analysisData.value = res.data;
        
        nextTick(() => {
            initRadarChart();
        });
    } catch (e) {
         console.error('Failed to load sub analysis', e);
    }
};

const generateAiAnalysis = async () => {
    if (!detailedStats.value) return;
    if (aiAnalysisResult.value) return; // Already generated

    isGeneratingAi.value = true;
    try {
        // We need total questions, accuracy, active_days etc.
        // We get total and correct from typeStats or subjectStats
        let total = 0, correct = 0;
        (detailedStats.value.typeStats || []).forEach(t => { total += t.total; correct += t.correct; });
        const accuracy = total > 0 ? Math.round((correct / total) * 100) : 0;
        const activeDays = (detailedStats.value.weekHeatmap || []).length; // rough placeholder or use a separate endpoint for total active days if available. We'll use detailedStats streak
        
        const payload = {
            totalQuestions: total,
            accuracy: accuracy,
            activeDays: activeDays, // Note: not entirely accurate if we only have 7 days from detailed stats, but it's okay for payload
            streak: detailedStats.value.streak || 0,
            subjectStats: detailedStats.value.subjectStats,
            typeStats: detailedStats.value.typeStats
        };

        const res = await api.getAiAnalysis(payload);
        if (res.data && res.data.analysis) {
            aiAnalysisResult.value = md.render(res.data.analysis);
        } else {
            toast.value?.add('生成AI诊断失败', 'error');
        }
    } catch (error) {
         let msg = "生成AI诊断失败";
         if (error.response && error.response.data && error.response.data.error) {
             msg = error.response.data.error;
         }
         toast.value?.add(msg, 'error');
    } finally {
        isGeneratingAi.value = false;
    }
};

const initRadarChart = () => {
    if (!radarChartRef.value || !analysisData.value) return;
    if (radarChartInstance) radarChartInstance.dispose();
    
    radarChartInstance = echarts.init(radarChartRef.value);
    const indicator = analysisData.value.radar?.indicator || [];
    const values = analysisData.value.radar?.value || [];

    const option = {
        radar: {
            indicator: indicator,
            splitNumber: 5,
            axisName: { color: '#64748b', fontWeight: 'bold' },
            splitLine: { lineStyle: { color: ['#e2e8f0'] } },
            splitArea: { show: false },
            axisLine: { lineStyle: { color: '#cbd5e1' } }
        },
        series: [{
            type: 'radar',
            data: [{
                value: values,
                name: '能力维度',
                symbol: 'circle',
                symbolSize: 6,
                itemStyle: { color: '#2563eb' },
                areaStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: 'rgba(37, 99, 235, 0.22)' },
                        { offset: 1, color: 'rgba(37, 99, 235, 0.04)' }
                    ])
                },
                lineStyle: { width: 3 }
            }]
        }]
    };
    radarChartInstance.setOption(option);
};

const initHeatmapChart = () => {
    if (!heatmapChartRef.value || !detailedStats.value) return;
    if (heatmapChartInstance) heatmapChartInstance.dispose();
    
    heatmapChartInstance = echarts.init(heatmapChartRef.value);
    
    const weekData = detailedStats.value.weekHeatmap || [];
    // Ensure we have 7 days
    const dates = [];
    const values = [];
    const today = new Date();
    for (let i = 6; i >= 0; i--) {
        const d = new Date(today);
        d.setDate(today.getDate() - i);
        const dateStr = d.toISOString().split('T')[0];
        dates.push(d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }));
        
        const found = weekData.find(w => w.day.startsWith(dateStr));
        values.push(found ? found.count : 0);
    }

    const option = {
        grid: { left: '5%', right: '5%', bottom: '10%', top: '20%', containLabel: true },
        tooltip: { trigger: 'axis', formatter: '{b}<br/>刷题数: {c}' },
        xAxis: { type: 'category', data: dates, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#94a3b8' } },
        yAxis: { type: 'value', splitLine: { lineStyle: { type: 'dashed', color: '#f1f5f9' } }, axisLabel: { show: false } },
        series: [{
            data: values,
            type: 'bar',
            itemStyle: { 
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: '#2563eb' },
                    { offset: 1, color: '#60a5fa' }
                ]),
                borderRadius: [4, 4, 0, 0]
            },
            label: { show: true, position: 'top', color: '#2563eb', fontWeight: 'bold' }
        }]
    };
    heatmapChartInstance.setOption(option);
};

const initSubjectChart = () => {
     if (!subjectChartRef.value || !detailedStats.value) return;
     if (subjectChartInstance) subjectChartInstance.dispose();

     const subjectStats = detailedStats.value.subjectStats || [];
     if(subjectStats.length === 0) return;

     subjectChartInstance = echarts.init(subjectChartRef.value);
     const pieData = subjectStats.map(s => ({ name: s.subject_name.substring(0,6), value: s.total }));

     const option = {
         tooltip: { trigger: 'item', formatter: '{b}: {c} 题 ({d}%)' },
         legend: { bottom: '0%', left: 'center', itemWidth: 8, itemHeight: 8, icon: 'circle', textStyle: { color: '#64748b', fontSize: 10 } },
         series: [{
             type: 'pie',
             radius: ['40%', '70%'],
             avoidLabelOverlap: false,
             itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
             label: { show: false, position: 'center' },
             labelLine: { show: false },
             data: pieData,
             color: ['#2563eb', '#0ea5e9', '#16a34a', '#d97706', '#64748b', '#94a3b8']
         }]
     };
     subjectChartInstance.setOption(option);
};

const initTypeChart = () => {
     if (!typeChartRef.value || !detailedStats.value) return;
     if (typeChartInstance) typeChartInstance.dispose();

     const typeStats = detailedStats.value.typeStats || [];
     if(typeStats.length === 0) return;

     typeChartInstance = echarts.init(typeChartRef.value);
     
     const typeMap = { single: '单选', multiple: '多选', judge: '判断', fill: '填空', material: '材料', sort: '排序' };
     const categoryData = typeStats.map(t => typeMap[t.type] || t.type);
     const accuracyData = typeStats.map(t => t.accuracy);

     const option = {
         grid: { left: '3%', right: '10%', bottom: '3%', top: '10%', containLabel: true },
         tooltip: { trigger: 'axis', formatter: '{b}: 正确率 {c}%' },
         xAxis: { type: 'value', max: 100, splitLine: { lineStyle: { type: 'dashed', color: '#f1f5f9' } }, axisLabel: { color: '#94a3b8' } },
         yAxis: { type: 'category', data: categoryData, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#64748b', fontWeight: 'bold' } },
         series: [{
             type: 'bar',
             data: accuracyData,
             barWidth: '50%',
             itemStyle: { 
                 color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
                    { offset: 0, color: '#10b981' },
                    { offset: 1, color: '#34d399' }
                 ]),
                 borderRadius: [0, 10, 10, 0]
             },
             label: { show: true, position: 'right', formatter: '{c}%', color: '#059669', fontWeight: 'bold' }
         }]
     };
     typeChartInstance.setOption(option);
};

const getAccuracyColor = (acc) => {
    if (acc >= 90) return 'text-emerald-500 bg-emerald-50 dark:bg-emerald-900/20';
    if (acc >= 70) return 'text-indigo-500 bg-indigo-50 dark:bg-indigo-900/20';
    if (acc >= 60) return 'text-amber-500 bg-amber-50 dark:bg-amber-900/20';
    return 'text-rose-500 bg-rose-50 dark:bg-rose-900/20';
};

const handleResize = () => {
    radarChartInstance?.resize();
    heatmapChartInstance?.resize();
    subjectChartInstance?.resize();
    typeChartInstance?.resize();
};

onMounted(async () => {
    await loadStructure();
    
    // We intentionally delay loadAllData until the watches for selectedMajor/Subject run
    // But to be safe, we can wait a tick and call it if needed.
    // Actually, setting selectedMajor triggers watches, so loadData will be called.
    setTimeout(() => {
        loadAllData();
    }, 100);

    window.addEventListener('resize', handleResize);
});

const overallTotalQuestions = computed(() => {
    if (!detailedStats.value || !detailedStats.value.typeStats) return 0;
    return detailedStats.value.typeStats.reduce((sum, item) => sum + item.total, 0);
});

const overallAccuracy = computed(() => {
    if (!detailedStats.value || !detailedStats.value.typeStats) return 0;
    let total = 0; let correct = 0;
    detailedStats.value.typeStats.forEach(t => { total += t.total; correct += t.correct; });
    return total === 0 ? 0 : Math.round((correct / total) * 100);
});

</script>

<template>
  <div class="ui-page analysis-page">
    <header class="ui-page-head">
      <div><p class="ui-eyebrow">学习诊断</p><h1 class="ui-title">学习诊断报告</h1><p class="ui-subtitle">基于已记录的学习行为和作答结果。</p></div>
      <span v-if="detailedStats" class="ui-badge">连续 {{ detailedStats.streak || 0 }} 天</span>
      <button class="ui-btn ui-btn-outline ui-btn-sm" @click="router.push('/stats')">返回统计</button>
    </header>

    <div v-if="loading" class="ui-card ui-loading"><span class="ui-spinner mx-auto"></span><p class="mt-3">正在加载诊断数据</p></div>
    <template v-else>
      <section class="ui-grid ui-grid-4 diagnosis-metrics">
        <div class="ui-card ui-card-pad ui-metric"><span class="ui-metric-value">{{ overallTotalQuestions }}</span><span class="ui-metric-label">累计作答</span></div>
        <div class="ui-card ui-card-pad ui-metric"><span class="ui-metric-value">{{ overallAccuracy }}%</span><span class="ui-metric-label">综合正确率</span></div>
        <div class="ui-card ui-card-pad ui-metric"><span class="ui-metric-value">{{ detailedStats?.subjectStats?.length || 0 }}</span><span class="ui-metric-label">涉及科目</span></div>
        <div class="ui-card ui-card-pad ui-metric"><span class="ui-metric-value">{{ detailedStats?.typeStats?.length || 0 }}</span><span class="ui-metric-label">涉及题型</span></div>
      </section>

      <section class="diagnosis-layout">
        <div class="ui-card ui-card-pad diagnosis-activity"><div class="ui-row-between"><div><h2 class="ui-section-title">近 7 天活跃</h2><p class="ui-small ui-muted">每天的作答数量</p></div></div><div ref="heatmapChartRef" class="diagnosis-chart chart-activity"></div></div>
        <div class="ui-card ui-card-pad diagnosis-ai">
          <div class="ui-row-between"><div><h2 class="ui-section-title">AI 学情洞察</h2><p class="ui-small ui-muted">按需生成，不自动消耗额度</p></div><button class="ui-btn ui-btn-primary ui-btn-sm" :disabled="isGeneratingAi" @click="generateAiAnalysis">{{ isGeneratingAi ? '正在分析…' : aiAnalysisResult ? '重新查看' : '生成报告' }}</button></div>
          <div v-if="aiAnalysisResult" class="ai-markdown diagnosis-ai-body" v-html="sanitizeHtml(aiAnalysisResult)"></div>
          <div v-else-if="overallTotalQuestions > 0" class="diagnosis-placeholder"><strong>准备好生成学习建议</strong><span>报告会结合科目、题型和近七天活跃情况。</span></div>
          <div v-else class="diagnosis-placeholder"><strong>数据还不够</strong><span>完成一些练习后再生成诊断报告。</span></div>
        </div>
      </section>

      <section class="ui-grid ui-grid-2 diagnosis-charts">
        <div class="ui-card ui-card-pad"><div class="ui-row-between"><h2 class="ui-section-title">科目偏好</h2><span class="ui-small ui-faint">作答占比</span></div><div v-if="!detailedStats?.subjectStats?.length" class="ui-empty">暂无数据</div><div v-else ref="subjectChartRef" class="diagnosis-chart"></div></div>
        <div class="ui-card ui-card-pad"><div class="ui-row-between"><h2 class="ui-section-title">题型掌握度</h2><span class="ui-small ui-faint">正确率</span></div><div v-if="!detailedStats?.typeStats?.length" class="ui-empty">暂无数据</div><div v-else ref="typeChartRef" class="diagnosis-chart"></div></div>
      </section>

      <section class="diagnosis-detail">
        <div class="ui-section-head"><div><h2 class="ui-section-title">专项能力拆解</h2><p class="ui-small ui-muted">选择专业和科目查看知识点表现</p></div></div>
        <div class="diagnosis-filters">
          <select v-model="selectedMajor" class="ui-select" aria-label="选择专业"><option value="" disabled>选择专业</option><option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option></select>
          <select v-model="selectedSubject" class="ui-select" aria-label="选择科目"><option value="" disabled>选择科目</option><option v-for="s in subjects" :key="s.id" :value="s.id">{{ s.name }}</option></select>
        </div>
        <div class="ui-grid ui-grid-2 diagnosis-charts">
          <div class="ui-card ui-card-pad"><div class="ui-row-between"><h2 class="ui-section-title">知识点雷达</h2><span class="ui-small ui-faint">至少 3 个知识点</span></div><div v-if="!analysisData?.radar?.indicator?.length || analysisData.radar.indicator.length < 3" class="ui-empty">数据不足，无法生成雷达图</div><div v-else ref="radarChartRef" class="diagnosis-chart chart-radar"></div></div>
          <div class="ui-card ui-card-pad"><div class="ui-row-between"><h2 class="ui-section-title">需要加强的知识点</h2><button v-if="analysisData?.weakTags?.length" class="ui-btn ui-btn-ghost ui-btn-sm" @click="router.push(`/exam/${selectedSubject}?mode=wrong`)">去复习错题 →</button></div><div v-if="!analysisData?.weakTags?.length" class="ui-empty">当前科目暂无明显弱项</div><div v-else class="weak-list"><div v-for="(item, idx) in analysisData.weakTags" :key="item.tag" class="weak-row"><span class="weak-index">{{ idx + 1 }}</span><span class="ui-grow"><strong>{{ item.tag }}</strong><small class="block ui-small ui-faint">涉及 {{ item.total }} 题</small></span><span class="ui-badge" :class="getAccuracyColor(item.accuracy)">正确率 {{ item.accuracy }}%</span></div></div></div>
        </div>
      </section>
    </template>
    <Toast ref="toast" />
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
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 1.5rem;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.01);
  transition: all 0.3s ease;
}
.dark .glass-card {
  background: rgba(30, 41, 59, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.05);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.2);
}

.animate-fade-in-up {
  animation: fadeInUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Custom Scrollbar */
.custom-scrollbar::-webkit-scrollbar {
    width: 6px;
}
.custom-scrollbar::-webkit-scrollbar-track {
    background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
    background: rgba(148, 163, 184, 0.3);
    border-radius: 10px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
    background: rgba(148, 163, 184, 0.5);
}

/* AI Markdown Overrides */
.ai-markdown :deep(h1), 
.ai-markdown :deep(h2), 
.ai-markdown :deep(h3) {
    color: inherit;
    font-weight: 800;
    margin-top: 1.5em;
    margin-bottom: 0.5em;
}
.ai-markdown :deep(p) {
    margin-bottom: 1em;
}
.ai-markdown :deep(ul) {
    list-style-type: none;
    padding-left: 0;
}
.ai-markdown :deep(li) {
    position: relative;
    padding-left: 1.5em;
    margin-bottom: 0.5em;
}
.ai-markdown :deep(li::before) {
    content: "•";
    position: absolute;
    left: 0.5em;
    color: #6366f1;
    font-weight: bold;
}
.ai-markdown :deep(strong) {
    color: #6366f1;
    font-weight: 800;
}
.dark .ai-markdown :deep(strong) {
    color: #818cf8;
}
</style>
