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
const subjects = ref([]);
const activeMajors = computed(() => {
  const counts = new Map();
  progress.value.forEach(item => counts.set(item.subject_id, (counts.get(item.subject_id) || 0) + 1));
  return subjects.value.filter(subject => counts.has(subject.id)).sort((a, b) => (counts.get(b.id) || 0) - (counts.get(a.id) || 0));
});

onMounted(async () => {
  try {
    const [progRes, majorRes, subjectRes] = await Promise.all([
      api.getProgress(),
      api.getMajors(),
      api.getSubjects()
    ]);
    if (Array.isArray(progRes.data)) {
      progress.value = progRes.data;
    }
    if (Array.isArray(majorRes.data)) {
      majors.value = majorRes.data;
    }
    if (Array.isArray(subjectRes.data)) {
      subjects.value = subjectRes.data;
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
});

const getStats = (key) => {
  const p = progress.value.filter((item) => item.subject_id === key);
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
  <div class="ui-page stats-page">
    <header class="ui-page-head">
      <div><p class="ui-eyebrow">学习统计</p><h1 class="ui-title">看见积累，找到下一步</h1><p class="ui-subtitle">基于本账号的刷题记录，不做推测性统计。</p></div>
      <button class="ui-btn ui-btn-outline ui-btn-sm" @click="router.push('/analysis')">详细分析</button>
    </header>

    <section class="stats-overview ui-card ui-card-pad">
      <div class="ui-grid ui-grid-4">
        <div class="ui-metric"><span class="ui-metric-value">{{ totalCount }}</span><span class="ui-metric-label">累计作答</span></div>
        <div class="ui-metric"><span class="ui-metric-value">{{ totalRate }}%</span><span class="ui-metric-label">整体正确率</span></div>
        <div class="ui-metric"><span class="ui-metric-value">{{ progress.filter(i => i.is_correct).length }}</span><span class="ui-metric-label">做对题数</span></div>
        <div class="ui-metric"><span class="ui-metric-value">{{ progress.filter(i => !i.is_correct).length }}</span><span class="ui-metric-label">待巩固题数</span></div>
      </div>
      <div class="stats-accuracy"><div class="ui-row-between"><span class="ui-small ui-muted">整体正确率</span><strong>{{ totalRate }}%</strong></div><div class="ui-progress"><span :style="{ width: `${totalRate}%` }"></span></div></div>
    </section>

    <section>
      <div class="ui-section-head"><div><h2 class="ui-section-title">科目表现</h2><p class="ui-small ui-muted">按实际学习记录聚合</p></div></div>
      <div v-if="loading" class="ui-stack"><div v-for="i in 3" :key="i" class="ui-skeleton h-20"></div></div>
      <div v-else-if="activeMajors.length" class="ui-card stats-major-list">
        <div v-for="mod in activeMajors" :key="mod.id" class="stats-major-row">
          <span class="subject-mark">{{ mod.name.slice(0,1) }}</span>
          <div class="ui-grow">
            <div class="ui-row-between"><strong class="text-[13px]">{{ mod.name }}</strong><span class="ui-small" :style="{color: getStats(mod.id).rate >= 80 ? 'var(--success)' : getStats(mod.id).rate >= 60 ? 'var(--primary)' : 'var(--danger)'}">{{ getStats(mod.id).rate }}%</span></div>
            <div class="ui-progress stats-progress"><span :style="{ width: `${getStats(mod.id).rate}%` }"></span></div>
            <div class="stats-major-meta"><span>已刷 {{ getStats(mod.id).total }}</span><span>正确 {{ getStats(mod.id).correct }}</span><span>错误 {{ getStats(mod.id).total - getStats(mod.id).correct }}</span></div>
          </div>
        </div>
      </div>
      <div v-else class="ui-card ui-empty">暂无学习记录，完成一次练习后会在这里显示。</div>
    </section>
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
