<script setup>
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
                itemStyle: { color: '#6366f1' },
                areaStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: 'rgba(99, 102, 241, 0.5)' },
                        { offset: 1, color: 'rgba(99, 102, 241, 0.1)' }
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
                    { offset: 0, color: '#ec4899' },
                    { offset: 1, color: '#f43f5e' }
                ]),
                borderRadius: [4, 4, 0, 0]
            },
            label: { show: true, position: 'top', color: '#f43f5e', fontWeight: 'bold' }
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
             color: ['#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#6366f1']
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
    <div class="min-h-screen bg-slate-50 dark:bg-slate-900 pb-24 transition-colors duration-500">
        <!-- Top Nav -->
        <header class="glass-nav px-4 md:px-8 py-4 flex items-center justify-between sticky top-0 z-30 shadow-sm">
            <div class="flex items-center gap-3">
                <button @click="router.back()" class="w-10 h-10 rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-600 dark:text-slate-300 hover:bg-slate-200 transition-all">
                    <span class="text-xl">←</span>
                </button>
                <h1 class="text-xl md:text-2xl font-black text-slate-800 dark:text-slate-100 tracking-tight flex items-center gap-2">
                    <span class="text-2xl">🧠</span> 学习诊断报告
                </h1>
            </div>
            
            <div class="flex items-center gap-2" v-if="!loading && detailedStats">
                <span class="hidden md:inline-flex items-center justify-center px-3 py-1 bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400 font-bold rounded-lg text-sm">
                    🔥 连击: {{ detailedStats.streak }} 天
                </span>
            </div>
        </header>

        <div class="p-4 md:p-8 max-w-7xl mx-auto space-y-6">
            
            <div v-if="loading" class="py-32 flex flex-col items-center justify-center space-y-4">
                 <div class="w-12 h-12 border-4 border-slate-200 border-t-indigo-500 rounded-full animate-spin"></div>
                 <p class="text-slate-400 font-bold text-sm tracking-widest animate-pulse">正在生成报告...</p>
            </div>

            <template v-else>
                
                <!-- Section 1: Top Glance & AI -->
                <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 animate-fade-in-up">
                    
                    <!-- Left: Quick Stats -->
                    <div class="lg:col-span-4 flex flex-col gap-6">
                        <div class="glass-card p-6 flex items-center justify-between relative overflow-hidden group">
                             <div class="absolute -right-6 -top-6 w-24 h-24 bg-indigo-500/10 rounded-full blur-xl group-hover:scale-150 transition-transform duration-700"></div>
                             <div>
                                 <h3 class="text-slate-500 dark:text-slate-400 font-bold text-sm uppercase tracking-wide">总刷题量</h3>
                                 <div class="text-4xl font-black text-slate-800 dark:text-white mt-1">{{ overallTotalQuestions }}</div>
                             </div>
                             <div class="w-14 h-14 bg-indigo-100 dark:bg-indigo-900/30 rounded-2xl flex items-center justify-center text-indigo-500 text-2xl shadow-inner">
                                📝
                             </div>
                        </div>
                        <div class="glass-card p-6 flex items-center justify-between relative overflow-hidden group">
                             <div class="absolute -right-6 -top-6 w-24 h-24 bg-emerald-500/10 rounded-full blur-xl group-hover:scale-150 transition-transform duration-700"></div>
                             <div>
                                 <h3 class="text-slate-500 dark:text-slate-400 font-bold text-sm uppercase tracking-wide">综合正确率</h3>
                                 <div class="text-4xl font-black text-slate-800 dark:text-white mt-1 flex items-baseline gap-1">
                                     {{ overallAccuracy }}<span class="text-xl text-slate-400">%</span>
                                 </div>
                             </div>
                             <div class="w-14 h-14 bg-emerald-100 dark:bg-emerald-900/30 rounded-2xl flex items-center justify-center text-emerald-500 text-2xl shadow-inner">
                                🎯
                             </div>
                        </div>
                        
                        <!-- 7 Days Activity Heatmap -->
                        <div class="glass-card p-5 h-full flex flex-col">
                            <h3 class="font-bold text-slate-700 dark:text-slate-200 flex items-center gap-2 mb-2">
                                <span class="w-2 h-5 bg-pink-500 rounded-full"></span> 活跃图谱 (近7天)
                            </h3>
                            <div class="flex-grow min-h-[150px] w-full" ref="heatmapChartRef"></div>
                        </div>
                    </div>

                    <!-- Right: AI Deep Analysis -->
                    <div class="lg:col-span-8 relative">
                        <!-- Animated Gradient Background -->
                        <div class="absolute inset-0 bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 rounded-3xl opacity-100 blur-[2px] shadow-2xl shadow-indigo-500/20"></div>
                        
                        <div class="relative h-full bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl rounded-[22px] p-1 m-[2px]">
                            <div class="h-full bg-slate-50 dark:bg-slate-800 rounded-[20px] p-6 lg:p-8 flex flex-col">
                                <div class="flex items-center justify-between mb-6">
                                    <h3 class="font-black text-xl text-transparent bg-clip-text bg-gradient-to-r from-indigo-500 to-purple-600 flex items-center gap-2">
                                        <span class="text-2xl animate-pulse">✨</span> AI 深度学情洞察
                                    </h3>
                                    
                                    <button 
                                        @click="generateAiAnalysis"
                                        :disabled="isGeneratingAi"
                                        class="px-4 py-2 rounded-xl bg-gradient-to-r from-indigo-500 to-purple-500 text-white font-bold text-sm shadow-lg shadow-indigo-500/30 hover:shadow-indigo-500/50 hover:-translate-y-0.5 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                                    >
                                        <div v-if="isGeneratingAi" class="flex items-center gap-2">
                                           <div class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                                           让AI思考中...
                                        </div>
                                        <span v-else>生成深度报告</span>
                                    </button>
                                </div>

                                <div class="flex-grow overflow-y-auto custom-scrollbar pr-2 relative">
                                    <div v-if="aiAnalysisResult" class="ai-markdown prose prose-slate dark:prose-invert max-w-none text-[15px] leading-relaxed" v-html="aiAnalysisResult"></div>
                                    
                                    <div v-else-if="overallTotalQuestions > 0" class="h-full flex flex-col items-center justify-center text-slate-400 py-10">
                                        <div class="text-6xl mb-4 opacity-50 grayscale">🤖</div>
                                        <p class="font-bold">点击右侧按钮，让 AI 为你生成专属学习建议</p>
                                        <p class="text-xs mt-2">基于 DeepSeek 深度思考模型分析</p>
                                    </div>

                                    <div v-else class="h-full flex flex-col items-center justify-center text-slate-400 py-10 text-center">
                                        <div class="text-6xl mb-4 opacity-50 grayscale">📂</div>
                                        <p class="font-bold text-slate-500">数据不足</p>
                                        <p class="text-sm mt-1">你的刷题量比较少，快去刷几道题再来看看吧~</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Section 2: Distribution Stats -->
                <div class="grid grid-cols-1 md:grid-cols-2 gap-6 animate-fade-in-up" style="animation-delay: 100ms;">
                    
                    <div class="glass-card p-6">
                        <h3 class="font-bold text-slate-700 dark:text-slate-200 flex items-center gap-2 mb-4">
                             <span class="w-2 h-5 bg-blue-500 rounded-full"></span> 科目偏好分析
                        </h3>
                        <div v-if="!detailedStats?.subjectStats?.length" class="h-[250px] flex items-center justify-center text-slate-400">暂无数据</div>
                        <div v-else class="w-full h-[250px]" ref="subjectChartRef"></div>
                    </div>

                    <div class="glass-card p-6">
                        <h3 class="font-bold text-slate-700 dark:text-slate-200 flex items-center gap-2 mb-4">
                             <span class="w-2 h-5 bg-emerald-500 rounded-full"></span> 题型掌握度
                        </h3>
                        <div v-if="!detailedStats?.typeStats?.length" class="h-[250px] flex items-center justify-center text-slate-400">暂无数据</div>
                        <div v-else class="w-full h-[250px]" ref="typeChartRef"></div>
                    </div>

                </div>

                <!-- Section 3: Subject Specific Details (Old Analysis View Features) -->
                <div class="pt-8 animate-fade-in-up" style="animation-delay: 200ms;">
                    <div class="flex flex-col md:flex-row items-center justify-between mb-6 gap-4">
                        <h2 class="text-2xl font-black text-slate-800 dark:text-white">专项能力拆解</h2>
                        <div class="flex gap-2">
                            <select v-model="selectedMajor" class="bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2 text-sm font-bold text-slate-600 dark:text-slate-300 shadow-sm outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/20 transition-all cursor-pointer">
                                <option value="" disabled>选择专业</option>
                                <option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option>
                            </select>
                            <select v-model="selectedSubject" class="bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2 text-sm font-bold text-slate-600 dark:text-slate-300 shadow-sm outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/20 transition-all cursor-pointer">
                                <option value="" disabled>选择科目</option>
                                <option v-for="s in subjects" :key="s.id" :value="s.id">{{ s.name }}</option>
                            </select>
                        </div>
                    </div>

                    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
                        
                        <!-- Radar -->
                        <div class="glass-card p-6 flex flex-col items-center">
                            <h3 class="font-bold text-slate-700 dark:text-slate-200 flex items-center gap-2 mb-2 w-full">
                                <span class="w-2 h-5 bg-indigo-500 rounded-full"></span> 知识点雷达图
                            </h3>
                            <div v-if="!analysisData || !analysisData.radar || analysisData.radar.indicator.length < 3" class="flex-grow w-full flex items-center justify-center text-slate-400 text-sm min-h-[300px]">
                                数据不足，无法生成雷达图 (需至少涉猎3个知识点)
                            </div>
                            <div v-else ref="radarChartRef" class="w-full h-[350px]"></div>
                        </div>

                        <!-- Top Weaknesses -->
                        <div class="glass-card p-6 flex flex-col max-h-[450px]">
                            <h3 class="font-bold text-slate-700 dark:text-slate-200 flex items-center justify-between mb-4">
                                <div class="flex items-center gap-2">
                                    <span class="w-2 h-5 bg-rose-500 rounded-full"></span> 急需攻克知识点
                                </div>
                                <button v-if="analysisData && analysisData.weakTags?.length" @click="router.push(`/exam/${selectedSubject}?mode=wrong`)" class="text-xs font-bold text-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 px-3 py-1.5 rounded-lg hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors">
                                    去复习错题 →
                                </button>
                            </h3>

                            <div v-if="!analysisData || !analysisData.weakTags?.length" class="flex-grow flex items-center justify-center py-10 text-center text-slate-400 text-sm">
                                🎉 太棒了，当前科目暂无明显弱项！
                            </div>

                            <div v-else class="flex-grow overflow-y-auto custom-scrollbar space-y-2.5 pr-2 pb-2">
                                <div v-for="(item, idx) in analysisData.weakTags" :key="idx" 
                                    class="flex items-center justify-between p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-900/50 border border-slate-100 dark:border-slate-800 transition-all hover:border-slate-200 dark:hover:border-slate-700 group">
                                    <div class="flex items-center gap-3">
                                        <span class="text-xs font-black w-6 h-6 rounded-lg flex items-center justify-center bg-white dark:bg-slate-800 text-slate-400 border border-slate-200 dark:border-slate-700 shadow-sm group-hover:scale-110 transition-transform">
                                            {{ idx + 1 }}
                                        </span>
                                        <span class="font-bold text-sm text-slate-700 dark:text-slate-200 truncate max-w-[150px] md:max-w-xs" :title="item.tag">
                                            {{ item.tag }}
                                        </span>
                                    </div>
                                    <div class="text-right flex flex-col items-end">
                                        <div class="text-xs font-bold px-2 py-0.5 rounded" :class="getAccuracyColor(item.accuracy)">
                                            正确率 {{ item.accuracy }}% 
                                        </div>
                                        <div class="text-[10px] text-slate-400 mt-1 font-medium">涉及 {{ item.total }} 题</div>
                                    </div>
                                </div>
                            </div>
                        </div>

                    </div>
                </div>

            </template>
        </div>

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
