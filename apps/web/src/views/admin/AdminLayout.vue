<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';

const router = useRouter();
const route = useRoute();
const sidebarOpen = ref(false);
const confirmDialog = ref(null);
const user = ref({});
const isDark = ref(false);

const navGroups = [
  { label: '概览', items: [{ label: '仪表盘', to: '/admin' }] },
  { label: '内容', items: [
    { label: '用户与权限', to: '/admin/users' },
    { label: '题库管理', to: '/admin/questions' },
    { label: '题库访问控制', to: '/admin/access-control' },
    { label: '导出授权', to: '/admin/exports' },
    { label: '考试系统', to: '/admin/exams' },
    { label: '题目报错', to: '/admin/reports' },
    { label: '知识结构', to: '/admin/structure' },
  ] },
  { label: '资源与运营', items: [
    { label: '资源文章', to: '/admin/articles' },
    { label: '评论管理', to: '/admin/comments' },
    { label: '订阅卡密', to: '/admin/cards' },
    { label: '课程表', to: '/admin/schedule' },
    { label: 'App 版本', to: '/admin/app-update' },
    { label: '系统设置', to: '/admin/settings' },
  ] },
];
const displayName = computed(() => user.value.nickname || user.value.username || '管理员');
const initial = computed(() => displayName.value.slice(0, 1).toUpperCase());

const syncTheme = () => {
  const saved = localStorage.getItem('theme');
  isDark.value = saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.classList.toggle('dark', isDark.value);
};

const toggleTheme = () => {
  isDark.value = !isDark.value;
  document.documentElement.classList.toggle('dark', isDark.value);
  localStorage.setItem('theme', isDark.value ? 'dark' : 'light');
};

const logout = async () => {
  const confirmed = confirmDialog.value ? await confirmDialog.value.confirm({
    title: '退出管理后台', message: '确认退出当前账号？', confirmText: '退出', cancelText: '取消', type: 'danger',
  }) : window.confirm('确认退出当前账号？');
  if (!confirmed) return;
  try { const { auth } = await import('../../api'); await auth.logout(); } catch {}
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('user');
  router.push('/login');
};

onMounted(() => {
  try { user.value = JSON.parse(localStorage.getItem('user') || '{}') || {}; } catch {}
  syncTheme();
});
</script>

<template>
  <div class="admin-shell">
    <div v-if="sidebarOpen" class="admin-backdrop" @click="sidebarOpen = false"></div>
    <aside class="admin-sidebar" :class="{ 'is-open': sidebarOpen }">
      <RouterLink to="/admin" class="admin-brand" @click="sidebarOpen = false">
        <span class="web-brand-mark">烁</span>
        <span><strong>烁学</strong><small>管理后台</small></span>
      </RouterLink>
      <nav class="admin-nav" aria-label="后台导航">
        <section v-for="group in navGroups" :key="group.label">
          <p class="admin-nav-label">{{ group.label }}</p>
          <RouterLink v-for="item in group.items" :key="item.to" :to="item.to" class="admin-nav-link"
            :class="{ 'is-active': item.to === '/admin' ? route.path === '/admin' : route.path.startsWith(item.to) }"
            @click="sidebarOpen = false"><span class="admin-nav-line"></span>{{ item.label }}</RouterLink>
        </section>
      </nav>
      <div class="admin-sidebar-foot">
        <button class="admin-foot-link" @click="router.push('/home')">返回学习首页</button>
        <button class="admin-foot-link" @click="toggleTheme">{{ isDark ? '浅色模式' : '深色模式' }}</button>
        <button class="admin-foot-link danger" @click="logout">退出登录</button>
      </div>
    </aside>

    <section class="admin-main">
      <header class="admin-topbar">
        <button class="admin-menu-btn" type="button" aria-label="打开后台导航" @click="sidebarOpen = true"><span></span><span></span><span></span></button>
        <div class="admin-topbar-title"><strong>管理后台</strong><span>烁学运营与题库管理</span></div>
        <div class="admin-user"><span class="web-avatar">{{ initial }}</span><span class="admin-user-name">{{ displayName }}</span></div>
      </header>
      <main class="admin-content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in"><component :is="Component" /></transition>
        </router-view>
      </main>
    </section>
    <ConfirmDialog ref="confirmDialog" />
  </div>
</template>

<style scoped>
.admin-shell { min-height: 100vh; display: grid; grid-template-columns: 232px minmax(0,1fr); background: var(--bg-body); color: var(--text-main); }
.admin-sidebar { position: sticky; top: 0; height: 100vh; display: flex; flex-direction: column; background: var(--bg-card); border-right: 1px solid var(--border); z-index: 50; }
.admin-brand { display: flex; align-items: center; gap: 10px; height: 66px; padding: 0 18px; border-bottom: 1px solid var(--border); }
.admin-brand > span:last-child { display: flex; flex-direction: column; gap: 1px; }
.admin-brand strong { font-size: 14px; }
.admin-brand small { font-size: 10px; color: var(--text-quote); }
.admin-nav { flex: 1; overflow-y: auto; padding: 18px 10px; }
.admin-nav section + section { margin-top: 18px; }
.admin-nav-label { padding: 0 10px 7px; font-size: 10px; font-weight: 600; color: var(--text-quote); letter-spacing: .08em; }
.admin-nav-link { display: flex; align-items: center; gap: 8px; min-height: 36px; padding: 0 10px; border-radius: 8px; color: var(--text-sub); font-size: 12px; transition: background .15s ease, color .15s ease; }
.admin-nav-link:hover { background: var(--bg-card-highlight); color: var(--text-main); }
.admin-nav-link.is-active { background: var(--primary-soft); color: var(--primary); font-weight: 600; }
.admin-nav-line { width: 3px; height: 14px; border-radius: 2px; background: transparent; }
.admin-nav-link.is-active .admin-nav-line { background: var(--primary); }
.admin-sidebar-foot { border-top: 1px solid var(--border); padding: 10px; display: flex; flex-direction: column; gap: 2px; }
.admin-foot-link { min-height: 34px; border-radius: 7px; padding: 0 10px; text-align: left; font-size: 12px; color: var(--text-sub); }
.admin-foot-link:hover { background: var(--bg-card-highlight); color: var(--text-main); }
.admin-foot-link.danger { color: var(--danger); }
.admin-main { min-width: 0; }
.admin-topbar { height: 64px; display: flex; align-items: center; gap: 12px; padding: 0 24px; border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--bg-body) 94%, transparent); backdrop-filter: blur(12px); position: sticky; top: 0; z-index: 20; }
.admin-topbar-title { display: flex; flex-direction: column; gap: 2px; }
.admin-topbar-title strong { font-size: 14px; }
.admin-topbar-title span { font-size: 10px; color: var(--text-quote); }
.admin-user { margin-left: auto; display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text-sub); }
.admin-menu-btn { display: none; width: 34px; height: 34px; flex-direction: column; justify-content: center; gap: 4px; padding: 0 8px; border-radius: 8px; }
.admin-menu-btn span { height: 1px; background: var(--text-sub); }
.admin-content { padding: 24px; width: min(1320px, 100%); margin: 0 auto; }
.admin-backdrop { display: none; position: fixed; inset: 0; background: rgba(15,23,42,.28); z-index: 45; }
@media (max-width: 900px) {
  .admin-shell { grid-template-columns: 1fr; }
  .admin-sidebar { position: fixed; left: 0; top: 0; width: 244px; transform: translateX(-100%); transition: transform .2s ease; }
  .admin-sidebar.is-open { transform: translateX(0); }
  .admin-backdrop { display: block; }
  .admin-menu-btn { display: flex; }
  .admin-content { padding: 16px; }
  .admin-user-name { display: none; }
}
</style>
