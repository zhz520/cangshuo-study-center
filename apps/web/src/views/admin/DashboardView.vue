<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { admin, publicApi } from '../../api';
import DashboardCharts from './components/DashboardCharts.vue';
import { getTheme } from '../../utils/theme';
import PageHeader from '../../components/ui/PageHeader.vue';

const stats = ref({
  users: 0,
  questions: 0,
  charts: null // For chart data
});
const dailyQuote = ref(null);
const loading = ref(true);
const isDark = ref(getTheme() === 'dark');
const username = ref('Admin');
const pendingReports = ref(0);
const lastUpdated = ref('');

// Time-based greeting in English
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 5) return '夜深了';
  if (hour < 12) return '早上好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

const syncChartTheme = (event) => { isDark.value = event.detail === 'dark'; };

const loadDashboard = async () => {
  loading.value = true;
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
    const [usersRes, questionsRes, homeDataRes, statsRes, reportsRes] = await Promise.all([
      admin.getUsers(),
      admin.getQuestions({ limit: 1 }),
      publicApi.getRandomDailyContent(),
      admin.getStats(),
      admin.getReports({ status: 'pending', limit: 1 }).catch(() => null)
    ]);
    stats.value.users = usersRes?.data?.length || 0;
    stats.value.questions = questionsRes?.data?.pagination?.total || 0;
    stats.value.charts = statsRes?.data || null;
    pendingReports.value = reportsRes?.data?.total || 0;
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });

    const data = homeDataRes.data;
    const types = [];

    if (data.quote) types.push({ content: data.quote, translation: 'Daily Quote' });
    if (data?.proverb?.en) types.push({ content: data.proverb.en, translation: data.proverb.zh || '' });
    if (data?.poetry?.line) types.push({ content: data.poetry.line, translation: data.poetry.src || '' });

    if (types.length > 0) {
      dailyQuote.value = types[Math.floor(Math.random() * types.length)];
    } else {
       dailyQuote.value = {
         content: "Learning never exhausts the mind.",
         translation: "学而不厌"
       };
    }

  } catch (e) {
    console.error('Failed to fetch dashboard data', e);
    dailyQuote.value = {
         content: "Believe you can and you're halfway there.",
         translation: "相信自己，你已经成功了一半"
    };
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  window.addEventListener('study-theme-change', syncChartTheme);
  loadDashboard();
});
onUnmounted(() => window.removeEventListener('study-theme-change', syncChartTheme));
</script>

<template>
  <div class="dash">
    <PageHeader eyebrow="工作台" :title="greeting + '，' + username" description="查看题库、用户和平台运行状态。">
      <template #meta>
        <p class="dash-status">
          <span class="dash-dot"></span>
          {{ username === 'admintest' ? '演示模式 · 仅可浏览' : '管理员权限已激活' }}
        </p>
      </template>
      <template #actions>
        <button class="ui-btn ui-btn-outline ui-btn-sm admin-control" type="button" :disabled="loading" @click="loadDashboard">
          {{ loading ? '刷新中…' : '刷新数据' }}
        </button>
      </template>
    </PageHeader>

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
        <span>待处理报错</span>
        <strong>{{ loading ? '—' : pendingReports }}</strong>
        <p>需要审核的题目反馈</p>
      </div>
      <div class="dash-metric">
        <span>运行状态</span>
        <strong class="is-ok">正常</strong>
        <p>接口与数据库可用</p>
      </div>
    </div>

    <DashboardCharts
      v-if="stats.charts"
      :stats="stats.charts"
      :loading="loading"
      :is-dark="isDark"
    />

    <div class="dash-lower">
      <section class="admin-table-card dash-links-card">
        <div class="admin-table-toolbar">
          <div><strong>快捷入口</strong><span>常用管理操作</span></div>
          <RouterLink to="/admin/questions" class="dash-toolbar-link">进入题库</RouterLink>
        </div>
        <div class="dash-links">
          <RouterLink to="/admin/users" class="dash-link">
            <div><strong>用户管理</strong><p>账号资料、角色与访问权限</p></div>
            <span>→</span>
          </RouterLink>
          <RouterLink to="/admin/questions" class="dash-link">
            <div><strong>题库管理</strong><p>题目维护、导入与审核</p></div>
            <span>→</span>
          </RouterLink>
          <RouterLink to="/admin/settings" class="dash-link">
            <div><strong>系统设置</strong><p>首页内容与模块参数</p></div>
            <span>→</span>
          </RouterLink>
          <RouterLink to="/admin/exports" class="dash-link">
            <div><strong>导出授权</strong><p>生成与撤销账户导出密码</p></div>
            <span>→</span>
          </RouterLink>
        </div>
      </section>

      <aside class="dash-side">
        <section class="admin-table-card dash-status-card">
          <div class="admin-table-toolbar">
            <strong>系统状态</strong>
            <span>{{ lastUpdated ? '更新于 ' + lastUpdated : '读取中' }}</span>
          </div>
          <div class="dash-status-row"><span>后端接口</span><strong class="is-ok">正常</strong></div>
          <div class="dash-status-row"><span>数据库</span><strong class="is-ok">可用</strong></div>
          <div class="dash-status-row"><span>待处理报错</span><strong>{{ pendingReports }}</strong></div>
          <RouterLink to="/admin/reports" class="dash-text-link">查看题目报错 →</RouterLink>
        </section>

        <blockquote v-if="dailyQuote" class="dash-quote">
          <p>{{ dailyQuote.content }}</p>
          <footer>{{ dailyQuote.translation }}</footer>
        </blockquote>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.dash { width:100%; }
.dash-status { display:flex; align-items:center; gap:6px; margin-top:8px; color:var(--text-sub); font-size:11px; }
.dash-dot { width:6px; height:6px; border-radius:50%; background:var(--success); }
.dash-metrics { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:1px; margin:18px 0 16px; overflow:hidden; border:1px solid var(--border); border-radius:12px; background:var(--border); }
.dash-metric { padding:16px 18px; background:var(--bg-card); }
.dash-metric span { color:var(--text-sub); font-size:10px; }
.dash-metric strong { display:block; margin-top:5px; color:var(--text-main); font-size:24px; line-height:1.15; font-weight:650; letter-spacing:-.03em; font-variant-numeric:tabular-nums; }
.dash-metric strong.is-ok { font-size:18px; color:var(--success); }
.dash-metric p { margin-top:4px; color:var(--text-quote); font-size:10px; }
.dash-lower { display:grid; grid-template-columns:minmax(0,1.35fr) minmax(280px,.65fr); gap:12px; align-items:start; }
.dash-links-card { overflow:hidden; }
.dash-toolbar-link { color:var(--text-sub); font-size:10px; }
.dash-toolbar-link:hover { color:var(--primary); }
.dash-links { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); }
.dash-link { display:flex; align-items:center; justify-content:space-between; gap:14px; min-height:72px; padding:14px 16px; border-right:1px solid var(--border); border-bottom:1px solid var(--border); color:var(--text-main); }
.dash-link:nth-child(2n) { border-right:0; }
.dash-link:nth-last-child(-n+2) { border-bottom:0; }
.dash-link:hover { background:var(--bg-card-highlight); }
.dash-link strong { font-size:12px; font-weight:550; }
.dash-link p { margin-top:3px; color:var(--text-sub); font-size:10px; line-height:1.5; }
.dash-link > span { color:var(--text-quote); font-size:12px; }
.dash-link:hover > span { color:var(--primary); }
.dash-side { display:flex; flex-direction:column; gap:12px; }
.dash-status-card { overflow:hidden; }
.dash-status-row { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:11px 16px; border-bottom:1px solid var(--border); }
.dash-status-row span { color:var(--text-sub); font-size:11px; }
.dash-status-row strong { color:var(--text-main); font-size:11px; font-weight:550; }
.dash-status-row strong.is-ok { color:var(--success); }
.dash-text-link { display:block; padding:11px 16px; color:var(--primary); font-size:10px; }
.dash-text-link:hover { background:var(--bg-card-highlight); }
.dash-quote { padding:15px 16px; border:1px solid var(--border); border-radius:12px; background:var(--bg-card); }
.dash-quote p { color:var(--text-sub); font-size:12px; line-height:1.7; }
.dash-quote footer { margin-top:7px; color:var(--text-quote); font-size:10px; }
@media (max-width:1000px) {
  .dash-metrics { grid-template-columns:repeat(2,minmax(0,1fr)); }
  .dash-lower { grid-template-columns:1fr; }
}
@media (max-width:620px) {
  .dash-metrics { grid-template-columns:1fr; }
  .dash-links { grid-template-columns:1fr; }
  .dash-link { border-right:0; }
  .dash-link:nth-last-child(-n+2) { border-bottom:1px solid var(--border); }
  .dash-link:last-child { border-bottom:0; }
}
</style>