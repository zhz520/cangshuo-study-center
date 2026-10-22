<script setup>
import { onMounted } from 'vue';

onMounted(() => {
  const saved = localStorage.getItem("theme");
  if (
    saved === "dark" ||
    (!saved && window.matchMedia("(prefers-color-scheme: dark)").matches)
  ) {
    document.documentElement.classList.add("dark");
  } else {
    document.documentElement.classList.remove("dark");
  }
});

import { computed } from 'vue';
import { useRoute } from 'vue-router'; // Although logic is global, user is in localStorage
// Reactive check for user role would be better with a store (Pinia), but for now we read from localStorage
// To make it reactive without store, we can use a simple ref updated on storage event or route change
// A simpler hack for now: check localStorage on every route change or using a global state.
// Let's use a simple computed that reads from localStorage, but it might not be reactive enough.
// Better: Check route meta or rely on the router view re-rendering.
// Actually, App.vue is root. Let's make it reactive via a simple event listener or just checks.

const user = computed(() => {
    const u = localStorage.getItem('user');
    return u ? JSON.parse(u) : null;
});

const isDemoUser = computed(() => user.value?.role === 'demo');
</script>

<template>
  <div class="min-h-screen bg-bg-body text-slate-800 relative">
    <!-- Test Account Banner -->
    <div v-if="isDemoUser" class="fixed top-0 left-0 right-0 z-[100] bg-orange-500 text-white text-xs font-bold py-1 px-4 text-center shadow-md animate-pulse">
        ⚠️ 当前为演示账号 (测试模式)：仅供查看，无法修改任何数据
    </div>
    
    <div :class="{ 'pt-8': isDemoUser }">
         <router-view></router-view>
    </div>
  </div>
</template>
