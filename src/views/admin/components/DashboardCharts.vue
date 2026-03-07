<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue';
import * as echarts from 'echarts';

const props = defineProps({
  stats: {
    type: Object,
    default: () => ({ majorStats: [], typeStats: [], userTrend: [] })
  },
  loading: Boolean,
  isDark: Boolean
});

const majorChartRef = ref(null);
const typeChartRef = ref(null);
const trendChartRef = ref(null);

let majorChart = null;
let typeChart = null;
let trendChart = null;

const initCharts = () => {
    if (!props.stats) return;
    
    const textColor = props.isDark ? '#cbd5e1' : '#475569';
    const gridColor = props.isDark ? '#334155' : '#e2e8f0';
    const tooltipBg = props.isDark ? 'rgba(30, 41, 59, 0.9)' : 'rgba(255, 255, 255, 0.9)';

    // 1. Major Distribution (Bar Chart)
    if (majorChartRef.value) {
        majorChart = echarts.init(majorChartRef.value);
        const source = props.stats.majorStats.slice(0, 10); // Top 10
        majorChart.setOption({
            backgroundColor: 'transparent',
            tooltip: { trigger: 'axis', backgroundColor: tooltipBg, borderRadius: 8, padding: 12, textStyle: { color: textColor } },
            grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
            xAxis: { 
                type: 'value', 
                splitLine: { lineStyle: { color: gridColor, type: 'dashed' } },
                axisLabel: { color: textColor }
            },
            yAxis: { 
                type: 'category', 
                data: source.map(i => i.name),
                axisLabel: { color: textColor, width: 90, overflow: 'break' },
                axisLine: { show: false },
                axisTick: { show: false }
            },
            series: [{
                name: '题目数量',
                type: 'bar',
                data: source.map(i => i.count),
                itemStyle: {
                    borderRadius: [0, 6, 6, 0],
                    color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                        { offset: 0, color: '#6366f1' }, // Indigo 500
                        { offset: 1, color: '#a855f7' }  // Purple 500
                    ])
                },
                barWidth: 20
            }]
        });
    }

    // 2. Type Distribution (Pie Chart)
    if (typeChartRef.value) {
        typeChart = echarts.init(typeChartRef.value);
        const mapType = { single: '单选题', multiple: '多选题', judge: '判断题', fill: '填空题' };
        // Normalize data
        const pieData = props.stats.typeStats.map(item => ({
             value: item.count,
             name: mapType[item.type] || item.type
        }));
        
        typeChart.setOption({
             backgroundColor: 'transparent',
             tooltip: { trigger: 'item', backgroundColor: tooltipBg, borderRadius: 8, padding: 12, textStyle: { color: textColor } },
             legend: { bottom: '0%', left: 'center', textStyle: { color: textColor } },
             series: [{
                 name: '题型分布',
                 type: 'pie',
                 radius: ['45%', '70%'],
                 center: ['50%', '45%'],
                 itemStyle: {
                     borderRadius: 8,
                     borderColor: props.isDark ? '#1e293b' : '#fff', // slate-800 or white
                     borderWidth: 2
                 },
                 label: { show: false },
                 data: pieData,
                 color: ['#3b82f6', '#10b981', '#f59e0b', '#ef4444'] // Blue, Emerald, Amber, Red
             }]
        });
    }
    
    // 3. User Trend (Line Chart)
    if (trendChartRef.value) {
        trendChart = echarts.init(trendChartRef.value);
        const dates = props.stats.userTrend.map(i => {
             const d = new Date(i.date);
             return `${d.getMonth()+1}/${d.getDate()}`;
        });
        const counts = props.stats.userTrend.map(i => i.count);

        trendChart.setOption({
            backgroundColor: 'transparent',
            tooltip: { trigger: 'axis', backgroundColor: tooltipBg, borderRadius: 8, textStyle: { color: textColor } },
            grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true, top: '15%' },
            xAxis: { 
                type: 'category', 
                data: dates,
                axisLine: { lineStyle: { color: gridColor } },
                axisLabel: { color: textColor }
            },
            yAxis: { 
                type: 'value', 
                splitLine: { lineStyle: { color: gridColor, type: 'dashed' } },
                axisLabel: { color: textColor }
            },
            series: [{
                data: counts,
                type: 'line',
                smooth: true,
                symbolSize: 8,
                itemStyle: { color: '#06b6d4' }, // Cyan
                areaStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: 'rgba(6,182,212,0.3)' },
                        { offset: 1, color: 'rgba(6,182,212,0)' }
                    ])
                }
            }]
        });
    }
};

const resizeCharts = () => {
    majorChart?.resize();
    typeChart?.resize();
    trendChart?.resize();
};

watch(() => props.stats, () => {
    nextTick(initCharts);
}, { deep: true });

watch(() => props.isDark, () => {
    majorChart?.dispose();
    typeChart?.dispose();
    trendChart?.dispose();
    nextTick(initCharts);
});

onMounted(() => {
    window.addEventListener('resize', resizeCharts);
    // Initial render if data is ready
    if(props.stats && props.stats.majorStats && props.stats.majorStats.length) {
        initCharts();
    }
});

onUnmounted(() => {
    window.removeEventListener('resize', resizeCharts);
    majorChart?.dispose();
    typeChart?.dispose();
    trendChart?.dispose();
});
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6 mb-10">
      
      <!-- Major Distribution -->
      <div class="bg-white dark:bg-slate-800 rounded-[32px] p-6 shadow-sm border border-slate-100 dark:border-slate-700 col-span-1 lg:col-span-2 xl:col-span-1 min-h-[350px]">
          <div class="flex items-center justify-between mb-4">
              <h3 class="font-black text-slate-800 dark:text-slate-100 flex items-center gap-2">
                  <span class="text-indigo-500">📊</span> 专业题库排行
              </h3>
          </div>
          <div ref="majorChartRef" class="w-full h-[280px]"></div>
      </div>

      <!-- Type Distribution -->
      <div class="bg-white dark:bg-slate-800 rounded-[32px] p-6 shadow-sm border border-slate-100 dark:border-slate-700 min-h-[350px]">
          <div class="flex items-center justify-between mb-4">
            <h3 class="font-black text-slate-800 dark:text-slate-100 flex items-center gap-2">
                <span class="text-pink-500">🥧</span> 题型分布概览
            </h3>
        </div>
          <div ref="typeChartRef" class="w-full h-[280px]"></div>
      </div>

      <!-- User Trend -->
      <div class="bg-white dark:bg-slate-800 rounded-[32px] p-6 shadow-sm border border-slate-100 dark:border-slate-700 min-h-[350px]">
        <div class="flex items-center justify-between mb-4">
            <h3 class="font-black text-slate-800 dark:text-slate-100 flex items-center gap-2">
                <span class="text-cyan-500">📈</span> 用户增长趋势
            </h3>
            <span class="text-xs font-bold text-slate-400 bg-slate-100 dark:bg-slate-700/50 px-2 py-1 rounded-lg">Last 7 Days</span>
        </div>
          <div ref="trendChartRef" class="w-full h-[280px]"></div>
      </div>

  </div>
</template>
