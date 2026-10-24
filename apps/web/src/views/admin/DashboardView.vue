<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { admin, publicApi } from '../../api';
import DashboardCharts from './components/DashboardCharts.vue';
import { getTheme } from '../../utils/theme';

const stats = ref({
  users: 0,
  questions: 0,
  charts: null // For chart data
});
const dailyQuote = ref(null);
const loading = ref(true);
const isDark = ref(getTheme() === 'dark');
const username = ref('Admin');

// Time-based greeting in English
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 5) return '夜深了';
  if (hour < 12) return '早上好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

const syncChartTheme = (event) => { isDark.value = event.detail === 'dark'; };

onMounted(async () => {
  window.addEventListener('study-theme-change', syncChartTheme);

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
onUnmounted(() => window.removeEventListener('study-theme-change', syncChartTheme));
</script>

<template>
  <div class="dash">
    <div class="dash-head">
      <div>
        <p class="ui-eyebrow">控制台</p>
        <h1 class="dash-title">{{ greeting }}，{{ username }}</h1>
        <p class="dash-status">
          <span class="dash-dot"></span>
          {{ username === 'admintest' ? '演示模式 · 仅可浏览' : '管理员权限已激活' }}
        </p>
      </div>
    </div>

    <div class="dash-metrics">
      <div class="dash-metric">
        <span>用户总数</span>
        <strong>{{ loading ? '—' : stats.users }}</strong>
        <p>已注册账户</p>
      </div>
      <div class="dash-metric">
        <span>题目总量</span>
        <strong>{{ loading ? '—' : stats.questions }}</strong>
        <p>当前题库</p>
      </div>
      <div class="dash-metric">
        <span>运行状态</span>
        <strong class="is-ok">正常</strong>
        <p>接口与数据库可用</p>
      </div>
    </div>

    <blockquote v-if="dailyQuote" class="dash-quote">
      <p>{{ dailyQuote.content }}</p>
      <footer>{{ dailyQuote.translation }}</footer>
    </blockquote>

    <DashboardCharts
      v-if="stats.charts"
      :stats="stats.charts"
      :loading="loading"
      :is-dark="isDark"
    />

    <div class="dash-section-head">
      <h2>快捷入口</h2>
    </div>
    <div class="dash-links">
      <router-link to="/admin/users" class="dash-link">
        <div>
          <strong>用户管理</strong>
          <p>账号资料、角色与访问权限</p>
        </div>
        <span>→</span>
      </router-link>
      <router-link to="/admin/questions" class="dash-link">
        <div>
          <strong>题库管理</strong>
          <p>题目维护、导入与审核</p>
        </div>
        <span>→</span>
      </router-link>
      <router-link to="/admin/settings" class="dash-link">
        <div>
          <strong>系统配置</strong>
          <p>首页内容与模块参数</p>
        </div>
        <span>→</span>
      </router-link>
      <router-link to="/admin/exports" class="dash-link">
        <div>
          <strong>题库导出授权</strong>
          <p>生成与撤销账户导出密码</p>
        </div>
        <span>→</span>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.dash { width: 100%; }
.dash-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 26px; }
.dash-title { margin-top: 6px; font-size: 24px; line-height: 1.3; font-weight: 650; letter-spacing: -.025em; }
.dash-status { display: flex; align-items: center; gap: 6px; margin-top: 7px; font-size: 11px; color: var(--text-sub); }
.dash-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--success); }
.dash-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1px; margin-bottom: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--border); overflow: hidden; }
.dash-metric { padding: 18px 20px; background: var(--bg-card); }
.dash-metric span { font-size: 11px; color: var(--text-sub); }
.dash-metric strong { display: block; margin-top: 5px; font-size: 26px; line-height: 1.15; font-weight: 700; letter-spacing: -.03em; font-variant-numeric: tabular-nums; }
.dash-metric strong.is-ok { font-size: 20px; color: var(--success); }
.dash-metric p { margin-top: 4px; font-size: 10px; color: var(--text-quote); }
.dash-quote { margin-bottom: 18px; padding: 18px 20px; border: 1px solid var(--border); border-left: 3px solid var(--border-strong); border-radius: 10px; background: var(--bg-card); }
.dash-quote p { font-size: 15px; line-height: 1.7; color: var(--text-main); }
.dash-quote footer { margin-top: 8px; font-size: 11px; color: var(--text-quote); }
.dash-section-head { margin: 28px 0 10px; }
.dash-section-head h2 { font-size: 14px; font-weight: 650; }
.dash-links { border-top: 1px solid var(--border); }
.dash-link { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 2px; border-bottom: 1px solid var(--border); color: var(--text-main); }
.dash-link strong { font-size: 13px; font-weight: 600; }
.dash-link p { margin-top: 3px; font-size: 11px; color: var(--text-sub); }
.dash-link > span { color: var(--text-quote); font-size: 13px; }
.dash-link:hover strong, .dash-link:hover > span { color: var(--primary); }
@media (max-width: 700px) {
  .dash-metrics { grid-template-columns: 1fr; }
  .dash-head { flex-direction: column; }
}
</style>
