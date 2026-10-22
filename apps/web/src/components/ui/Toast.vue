<script setup>
import { ref, defineExpose } from "vue";

const toasts = ref([]);
let idCounter = 0;

const add = (message, type = "info", duration = 3000) => {
  const id = idCounter++;
  toasts.value.push({ id, message, type });
  setTimeout(() => remove(id), duration);
};

const remove = (id) => {
  toasts.value = toasts.value.filter((t) => t.id !== id);
};

defineExpose({ add });
</script>

<template>
  <div
    class="fixed top-4 left-1/2 -translate-x-1/2 z-[100] flex flex-col gap-2 pointer-events-none"
  >
    <TransitionGroup name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="pointer-events-auto min-w-[300px] max-w-sm p-4 rounded-xl shadow-xl backdrop-blur-md border flex items-start gap-3 transform transition-all duration-300"
        :class="{
          'bg-white/90 dark:bg-slate-800/90 border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-100':
            toast.type === 'info',
          'bg-emerald-50/90 dark:bg-emerald-900/90 border-emerald-200 dark:border-emerald-700 text-emerald-800 dark:text-emerald-100':
            toast.type === 'success',
          'bg-red-50/90 dark:bg-red-900/90 border-red-200 dark:border-red-700 text-red-800 dark:text-red-100':
            toast.type === 'error',
          'bg-amber-50/90 dark:bg-amber-900/90 border-amber-200 dark:border-amber-700 text-amber-800 dark:text-amber-100':
            toast.type === 'warning',
        }"
      >
        <span v-if="toast.type === 'success'" class="text-xl">✅</span>
        <span v-else-if="toast.type === 'error'" class="text-xl">❌</span>
        <span v-else-if="toast.type === 'warning'" class="text-xl">⚠️</span>
        <span v-else class="text-xl">ℹ️</span>
        <div class="text-sm font-medium leading-relaxed break-words">
          {{ toast.message }}
        </div>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}
</style>
