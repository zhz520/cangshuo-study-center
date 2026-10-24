<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import AppLayout from './views/layouts/AppLayout.vue';

const route = useRoute();
const isDemo = ref(false);
const syncUser = () => {
  try { isDemo.value = JSON.parse(localStorage.getItem('user') || '{}')?.role === 'demo'; }
  catch { isDemo.value = false; }
};

onMounted(syncUser);
watch(() => route.fullPath, syncUser);
</script>

<template>
  <div class="min-h-screen relative" style="background:var(--bg-body);color:var(--text-main)">
    <div v-if="isDemo" class="fixed top-0 left-0 right-0 z-[100] bg-amber-600 text-white text-xs font-semibold py-1.5 px-4 text-center">
      演示账号：只能查看，不能修改数据
    </div>
    <div :class="{ 'pt-7': isDemo }">
      <router-view v-slot="{ Component, route: currentRoute }">
        <AppLayout v-if="currentRoute.meta.shell">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" :key="currentRoute.path" />
          </transition>
        </AppLayout>
        <transition v-else name="page-fade" mode="out-in">
          <component :is="Component" :key="currentRoute.fullPath" />
        </transition>
      </router-view>
    </div>
  </div>
</template>
