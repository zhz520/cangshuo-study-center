<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch, computed } from 'vue';
import * as echarts from 'echarts';

const props = defineProps({
  stats: { type: Object, default: () => ({ majorStats: [], typeStats: [], userTrend: [] }) },
  loading: Boolean,
  isDark: Boolean
});

const majorChartRef = ref(null);
const typeChartRef = ref(null);
const trendChartRef = ref(null);
let majorChart = null;
let typeChart = null;
let trendChart = null;

const hasMajor = computed(() => (props.stats?.majorStats || []).length > 0);
const hasType = computed(() => (props.stats?.typeStats || []).length > 0);
const hasTrend = computed(() => (props.stats?.userTrend || []).length > 0);

const disposeCharts = () => {
  majorChart?.dispose(); majorChart = null;
  typeChart?.dispose(); typeChart = null;
  trendChart?.dispose(); trendChart = null;
};

const initCharts = () => {
  const ink = props.isDark ? '#e2e8f0' : '#111827';
  const sub = props.isDark ? '#94a3b8' : '#64748b';
  const grid = props.isDark ? '#303c4e' : '#e5e7eb';
  const card = props.isDark ? '#171f2c' : '#ffffff';

  if (majorChartRef.value && hasMajor.value) {
    majorChart = echarts.init(majorChartRef.value);
    const source = (props.stats.majorStats || []).slice(0, 8);
    majorChart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', backgroundColor: card, borderColor: grid, textStyle: { color: ink, fontSize: 11 } },
      grid: { left: 4, right: 12, top: 8, bottom: 4, containLabel: true },
      xAxis: { type: 'value', splitLine: { lineStyle: { color: grid, type: 'dashed' } }, axisLabel: { color: sub, fontSize: 10 } },
      yAxis: { type: 'category', data: source.map(i => i.name), axisLabel: { color: sub, fontSize: 10, width: 88, overflow: 'truncate' }, axisLine: { show: false }, axisTick: { show: false } },
      series: [{ name: '题目数量', type: 'bar', data: source.map(i => i.count), barWidth: 12, itemStyle: { borderRadius: [0, 3, 3, 0], color: ink } }]
    });
  }

  if (typeChartRef.value && hasType.value) {
    typeChart = echarts.init(typeChartRef.value);
    const mapType = { single: '单选题', multiple: '多选题', judge: '判断题', fill: '填空题', sort: '排序题', material: '材料题' };
    const pieData = (props.stats.typeStats || []).map(item => ({ value: item.count, name: mapType[item.type] || item.type }));
    typeChart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'item', backgroundColor: card, borderColor: grid, textStyle: { color: ink, fontSize: 11 } },
      legend: { bottom: 0, left: 'center', textStyle: { color: sub, fontSize: 10 }, itemWidth: 8, itemHeight: 8 },
      series: [{ name: '题型分布', type: 'pie', radius: ['48%', '68%'], center: ['50%', '43%'], itemStyle: { borderColor: card, borderWidth: 2, borderRadius: 3 }, label: { show: false }, data: pieData, color: ['#2563eb', '#64748b', '#94a3b8', '#cbd5e1', '#d97706', '#16a34a'] }]
    });
  }

  if (trendChartRef.value && hasTrend.value) {
    trendChart = echarts.init(trendChartRef.value);
    const trendData = props.stats.userTrend || [];
    const dates = trendData.map(i => { const d = new Date(i.date); return d.getMonth() + 1 + '/' + d.getDate(); });
    const counts = trendData.map(i => i.count);
    trendChart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', backgroundColor: card, borderColor: grid, textStyle: { color: ink, fontSize: 11 } },
      grid: { left: 4, right: 12, top: 8, bottom: 4, containLabel: true },
      xAxis: { type: 'category', data: dates, boundaryGap: false, axisLine: { lineStyle: { color: grid } }, axisLabel: { color: sub, fontSize: 10 } },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: grid, type: 'dashed' } }, axisLabel: { color: sub, fontSize: 10 } },
      series: [{ data: counts, type: 'line', smooth: true, symbolSize: 4, lineStyle: { width: 2, color: '#2563eb' }, itemStyle: { color: '#2563eb' } }]
    });
  }
};

const resizeCharts = () => { majorChart?.resize(); typeChart?.resize(); trendChart?.resize(); };

watch(() => props.stats, () => { nextTick(() => { disposeCharts(); initCharts(); }); }, { deep: true });
watch(() => props.isDark, () => { nextTick(() => { disposeCharts(); initCharts(); }); });
onMounted(() => { window.addEventListener('resize', resizeCharts); nextTick(initCharts); });
onUnmounted(() => { window.removeEventListener('resize', resizeCharts); disposeCharts(); });
</script>

<template>
  <div class="dash-charts">
    <section class="admin-table-card dash-chart-card">
      <header class="dash-chart-head"><h3>专业题库排行</h3><span>按题目数量</span></header>
      <div v-if="hasMajor" ref="majorChartRef" class="dash-chart-body"></div>
      <div v-else class="admin-empty dash-chart-empty">
        <span class="admin-empty-title">暂无排行数据</span>
        <span class="admin-empty-copy">题目入库后会显示专业分布。</span>
      </div>
    </section>

    <section class="admin-table-card dash-chart-card">
      <header class="dash-chart-head"><h3>题型分布</h3><span>全部题目</span></header>
      <div v-if="hasType" ref="typeChartRef" class="dash-chart-body"></div>
      <div v-else class="admin-empty dash-chart-empty">
        <span class="admin-empty-title">暂无题型数据</span>
        <span class="admin-empty-copy">新增题目后会显示题型占比。</span>
      </div>
    </section>

    <section class="admin-table-card dash-chart-card">
      <header class="dash-chart-head"><h3>用户增长</h3><span>近 7 天</span></header>
      <div v-if="hasTrend" ref="trendChartRef" class="dash-chart-body"></div>
      <div v-else class="admin-empty dash-chart-empty">
        <span class="admin-empty-title">暂无增长记录</span>
        <span class="admin-empty-copy">新用户注册后会显示趋势。</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dash-charts { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; margin-bottom:24px; }
.dash-chart-card { min-width:0; padding:16px; }
.dash-chart-head { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:10px; }
.dash-chart-head h3 { margin:0; color:var(--text-main); font-size:12px; font-weight:600; letter-spacing:-.01em; }
.dash-chart-head span { color:var(--text-quote); font-size:10px; white-space:nowrap; }
.dash-chart-body { height:230px; }
.dash-chart-empty { height:230px; min-height:230px; }
@media(max-width:1100px) { .dash-charts { grid-template-columns:repeat(2,minmax(0,1fr)); } .dash-charts > section:last-child { grid-column:span 2; } }
@media(max-width:700px) { .dash-charts { grid-template-columns:1fr; } .dash-charts > section:last-child { grid-column:auto; } }
</style>
