<script setup>
import BrandLogo from '../../components/BrandLogo.vue';
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { auth } from '../../api';
import { getTheme, toggleTheme as toggleStoredTheme } from '../../utils/theme';

const route = useRoute();
const router = useRouter();
const user = ref({});
const isDark = ref(getTheme() === 'dark');
const menuOpen = ref(false);
const sheetOpen = ref(false);

const isAdmin = computed(() => user.value.role === 'admin');
const displayName = computed(() => user.value.nickname || user.value.username || '同学');
const initial = computed(() => displayName.value.slice(0, 1).toUpperCase());
const onHomeBank = computed(() => route.path === '/home' && route.hash === '#subjects');

const navItems = computed(() => [
  { label: '首页', to: '/home', icon: 'home' },
  { label: '题库', to: '/home#subjects', icon: 'book' },
  { label: '学情', to: '/analysis', icon: 'chart' },
  { label: '数据', to: '/data', icon: 'layers' },
]);

const mobileItems = [
  { label: '首页', to: '/home', icon: 'home' },
  { label: '题库', to: '/home#subjects', icon: 'book' },
  { label: '学情', to: '/analysis', icon: 'chart' },
  { label: '数据', to: '/data', icon: 'layers' },
];

const readUser = () => {
  try { user.value = JSON.parse(localStorage.getItem('user') || '{}') || {}; }
  catch { user.value = {}; }
};

const toggleTheme = () => {
  isDark.value = toggleStoredTheme() === 'dark';
};

const scrollHash = () => {
  if (!route.hash) return;
  nextTick(() => setTimeout(() => document.querySelector(route.hash)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 80));
};

const go = async (to) => {
  menuOpen.value = false;
  sheetOpen.value = false;
  if (to === '/home#subjects') {
    await router.push({ path: '/home', hash: '#subjects' });
    scrollHash();
  } else {
    await router.push(to);
  }
};

const logout = async () => {
  menuOpen.value = false;
  sheetOpen.value = false;
  if (!window.confirm('确认退出当前账号？')) return;
  try { await auth.logout(); } catch {}
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('user');
  router.push('/login');
};

onMounted(() => { readUser(); scrollHash(); });
watch(() => route.fullPath, () => { readUser(); menuOpen.value = false; sheetOpen.value = false; scrollHash(); });
</script>

<template>
  <div class="web-shell">
    <header class="web-topbar">
      <div class="web-topbar-inner">
        <RouterLink to="/home" class="web-brand" aria-label="烁学首页">
          <BrandLogo />
          <span>烁学</span>
        </RouterLink>

        <nav class="web-nav" aria-label="主导航">
          <button v-for="item in navItems" :key="item.to" class="web-nav-link"
            :class="{ 'is-active': item.to === '/home#subjects' ? onHomeBank : item.to === '/home' ? (route.path === '/home' && route.hash !== '#subjects') : route.path.startsWith(item.to) }"
            @click="go(item.to)">{{ item.label }}</button>
        </nav>

        <div class="web-actions">
          <button class="web-icon-btn" type="button" :aria-label="isDark ? '切换浅色模式' : '切换深色模式'" @click="toggleTheme">
            <svg v-if="isDark" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.42 1.42M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.42-1.42M17.66 6.34l1.41-1.41"/></svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20.5 14.1A8.5 8.5 0 0 1 9.9 3.5 8.5 8.5 0 1 0 20.5 14.1Z"/></svg>
          </button>
          <div class="relative">
            <button class="web-avatar" type="button" @click="menuOpen = !menuOpen" :aria-expanded="menuOpen" :aria-label="`${displayName} 的账户菜单`">{{ initial }}</button>
            <div v-if="menuOpen" class="ui-dropdown" @click.stop>
              <div class="px-3 pb-2 pt-1">
                <p class="text-[13px] font-semibold" style="color:var(--text-main)">{{ displayName }}</p>
                <p class="ui-small ui-faint">{{ isAdmin ? '管理员账户' : '学习账户' }}</p>
              </div>
              <button v-if="isAdmin" @click="go('/admin')">管理后台</button>
              <div class="ui-divider my-1"></div>
              <button @click="toggleTheme">{{ isDark ? '切换到浅色模式' : '切换到深色模式' }}</button>
              <button class="!text-rose-500" @click="logout">退出登录</button>
            </div>
          </div>
        </div>
      </div>
    </header>

    <main class="web-main"><slot /></main>

    <nav class="web-mobile-nav" aria-label="移动端主导航">
      <button v-for="item in mobileItems" :key="item.to" class="web-mobile-link"
        :class="{ 'is-active': item.to === '/home#subjects' ? onHomeBank : item.to === '/home' ? (route.path === '/home' && route.hash !== '#subjects') : route.path.startsWith(item.to) }"
        @click="go(item.to)">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
          <path v-if="item.icon === 'home'" d="m3 11 9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>
          <path v-else-if="item.icon === 'book'" d="M4 4h5a3 3 0 0 1 3 3v13a3 3 0 0 0-3-3H4zM20 4h-5a3 3 0 0 0-3 3v13a3 3 0 0 1 3-3h5z"/>
          <path v-else-if="item.icon === 'chart'" d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>
          <path v-else d="M4 19V9M10 19V5M16 19v-6M22 19H2"/>
        </svg>
        <span>{{ item.label }}</span>
      </button>
      <button class="web-mobile-link" type="button" @click="sheetOpen = true">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1 .6 1.7 1.7 0 0 0-.4 1.1V21h-4v-.09A1.7 1.7 0 0 0 8.6 19.4a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-.6-1 1.7 1.7 0 0 0-1.1-.4H3v-4h.09A1.7 1.7 0 0 0 4.6 8.6a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-.6 1.7 1.7 0 0 0 .4-1.1V3h4v.09A1.7 1.7 0 0 0 15.4 4.6a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.4 9c.1.36.32.7.6 1 .3.26.7.4 1.1.4h.09v4h-.09a1.7 1.7 0 0 0-1.7.6Z"/></svg>
        <span>更多</span>
      </button>
    </nav>

    <div v-if="sheetOpen" class="ui-sheet-backdrop" @click="sheetOpen = false"></div>
    <div v-if="sheetOpen" class="ui-sheet" role="dialog" aria-label="更多菜单">
      <div class="ui-sheet-handle"></div>
      <div class="ui-row pb-3">
        <span class="web-avatar">{{ initial }}</span>
        <div><p class="font-semibold" style="color:var(--text-main)">{{ displayName }}</p><p class="ui-small ui-faint">{{ isAdmin ? '管理员账户' : '学习账户' }}</p></div>
      </div>
      <div class="ui-list-card ui-card">
        <button class="ui-row-button" @click="go('/data')"><span class="ui-grow">学习数据</span><span class="ui-faint">›</span></button>
        <button class="ui-row-button" @click="go('/download')"><span class="ui-grow">下载 App</span><span class="ui-faint">›</span></button>
        <button v-if="isAdmin" class="ui-row-button" @click="go('/admin')"><span class="ui-grow">管理后台</span><span class="ui-faint">›</span></button>
        <button class="ui-row-button" @click="toggleTheme"><span class="ui-grow">{{ isDark ? '浅色模式' : '深色模式' }}</span><span class="ui-faint">切换</span></button>
      </div>
      <button class="ui-btn ui-btn-outline w-full mt-3" style="color:var(--danger);border-color:color-mix(in srgb,var(--danger) 35%,var(--border))" @click="logout">退出登录</button>
    </div>
  </div>
</template>

<style scoped>
.relative { position: relative; }
@media (max-width: 1023px) { .hide-mobile { display: none; } }
</style>
