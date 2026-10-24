<script setup>
import BrandLogo from '../../components/BrandLogo.vue';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';
import { getTheme, toggleTheme as toggleStoredTheme } from '../../utils/theme';

const router = useRouter();
const route = useRoute();
const sidebarOpen = ref(false);
const confirmDialog = ref(null);
const user = ref({});
const isDark = ref(getTheme() === 'dark');

const navGroups = [
  { key: 'overview', label: '工作台', items: [
    { label: '仪表盘', to: '/admin' },
  ] },
  { key: 'content', label: '内容与考试', items: [
    { label: '题库管理', to: '/admin/questions' },
    { label: '知识结构', to: '/admin/structure' },
    { label: '考试系统', to: '/admin/exams' },
    { label: '题目报错', to: '/admin/reports' },
    { label: '题库访问控制', to: '/admin/access-control' },
    { label: '导出授权', to: '/admin/exports' },
  ] },
  { key: 'operations', label: '用户与运营', items: [
    { label: '用户与权限', to: '/admin/users' },
    { label: '资源文章', to: '/admin/articles' },
    { label: '评论管理', to: '/admin/comments' },
    { label: '订阅卡密', to: '/admin/cards' },
    { label: '课程表', to: '/admin/schedule' },
  ] },
  { key: 'system', label: '平台配置', items: [
    { label: 'App 版本', to: '/admin/app-update' },
    { label: '系统设置', to: '/admin/settings' },
    { label: '第三方服务', to: '/admin/integrations' },
  ] },
];
const groupOpen = reactive({ overview: false, content: false, operations: false, system: false });
const isActive = (item) => item.to === '/admin' ? route.path === '/admin' : route.path.startsWith(item.to);
const activeGroup = computed(() => navGroups.find(group => group.items.some(isActive)));
const activeItem = computed(() => activeGroup.value?.items.find(isActive));
const toggleGroup = (key) => {
  const shouldOpen = !groupOpen[key];
  Object.keys(groupOpen).forEach(item => { groupOpen[item] = false; });
  if (shouldOpen) groupOpen[key] = true;
};
watch(() => route.path, () => {
  Object.keys(groupOpen).forEach(key => { groupOpen[key] = false; });
  if (activeGroup.value) groupOpen[activeGroup.value.key] = true;
}, { immediate: true });
const displayName = computed(() => user.value.nickname || user.value.username || '管理员');
const initial = computed(() => displayName.value.slice(0, 1).toUpperCase());

const toggleTheme = () => {
  isDark.value = toggleStoredTheme() === 'dark';
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
});
</script>

<template>
  <div class="admin-shell">
    <div v-if="sidebarOpen" class="admin-backdrop" @click="sidebarOpen = false"></div>
    <aside class="admin-sidebar" :class="{ 'is-open': sidebarOpen }">
      <RouterLink to="/admin" class="admin-brand" @click="sidebarOpen = false">
        <BrandLogo />
        <span><strong>烁学</strong><small>管理后台</small></span>
      </RouterLink>
      <nav class="admin-nav" aria-label="后台导航">
        <p class="admin-nav-caption">管理工作台</p>
        <section v-for="group in navGroups" :key="group.key" class="admin-nav-group">
          <button type="button" class="admin-nav-group-trigger" :class="{ 'has-active': activeGroup?.key === group.key }"
            :aria-expanded="groupOpen[group.key]" @click="toggleGroup(group.key)">
            <span>{{ group.label }}</span><em>{{ group.items.length }}</em>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true" :class="{ 'is-open': groupOpen[group.key] }"><path d="m9 5 7 7-7 7"/></svg>
          </button>
          <Transition name="nav-expand">
            <div v-if="groupOpen[group.key]" class="admin-nav-items">
              <RouterLink v-for="item in group.items" :key="item.to" :to="item.to" class="admin-nav-link"
                :class="{ 'is-active': isActive(item) }" @click="sidebarOpen = false"><span class="admin-nav-line"></span>{{ item.label }}</RouterLink>
            </div>
          </Transition>
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
        <div class="admin-topbar-title"><strong>{{ activeItem?.label || '管理后台' }}</strong><span>{{ activeGroup?.label || '烁学运营与题库管理' }}</span></div>
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
.admin-nav { flex: 1; overflow-y: auto; padding: 14px 8px 18px; }
.admin-nav-caption { padding: 4px 10px 10px; font-size: 9px; font-weight: 650; color: var(--text-quote); letter-spacing: .10em; text-transform: uppercase; }
.admin-nav-group + .admin-nav-group { margin-top: 3px; }
.admin-nav-group-trigger { width: 100%; min-height: 36px; display: flex; align-items: center; gap: 7px; padding: 0 9px; border-radius: 7px; color: var(--text-sub); font-size: 11px; font-weight: 550; text-align: left; }
.admin-nav-group-trigger:hover { background: var(--bg-card-highlight); color: var(--text-main); }
.admin-nav-group-trigger.has-active { color: var(--text-main); }
.admin-nav-group-trigger em { margin-left: auto; color: var(--text-quote); font-size: 9px; font-style: normal; font-variant-numeric: tabular-nums; }
.admin-nav-group-trigger svg { flex: 0 0 13px; color: var(--text-quote); transition: transform .16s ease; }
.admin-nav-group-trigger svg.is-open { transform: rotate(90deg); }
.admin-nav-items { margin: 1px 0 6px 17px; padding-left: 7px; border-left: 1px solid var(--border); }
.admin-nav-link { display: flex; align-items: center; gap: 7px; min-height: 33px; padding: 0 8px; border-radius: 6px; color: var(--text-sub); font-size: 11px; transition: background .15s ease, color .15s ease; }
.admin-nav-link:hover { background: var(--bg-card-highlight); color: var(--text-main); }
.admin-nav-link.is-active { background: var(--primary-soft); color: var(--primary); font-weight: 600; }
.admin-nav-line { width: 2px; height: 12px; border-radius: 2px; background: transparent; }
.admin-nav-link.is-active .admin-nav-line { background: var(--primary); }
.nav-expand-enter-active, .nav-expand-leave-active { transition: opacity .14s ease, transform .14s ease; }
.nav-expand-enter-from, .nav-expand-leave-to { opacity: 0; transform: translateY(-3px); }
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
.admin-content { padding: 24px; width: min(1280px, 100%); margin: 0 auto; }
.admin-backdrop { display: none; position: fixed; inset: 0; background: rgba(15,23,42,.28); z-index: 45; }
@media (max-width: 900px) {
  .admin-shell { grid-template-columns: 1fr; }
  .admin-sidebar { position: fixed; left: 0; top: 0; width: 244px; transform: translateX(-100%); transition: transform .2s ease; }
  .admin-sidebar.is-open { transform: translateX(0); }
  .admin-backdrop { display: block; left: 244px; }
  .admin-menu-btn { display: flex; }
  .admin-content { padding: 16px; }
  .admin-user-name { display: none; }
}
</style>
