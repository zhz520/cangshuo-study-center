<script setup>
import { ref } from 'vue';

const isVisible = ref(false);
const options = ref({
  title: '提示',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  type: 'info' // info, danger
});

let resolvePromise = null;
let rejectPromise = null;

const confirm = (opts) => {
  options.value = { ...options.value, ...opts };
  isVisible.value = true;
  return new Promise((resolve, reject) => {
    resolvePromise = resolve;
    rejectPromise = reject;
  });
};

const handleConfirm = () => {
  isVisible.value = false;
  if (resolvePromise) resolvePromise(true);
};

const handleCancel = () => {
  isVisible.value = false;
  if (resolvePromise) resolvePromise(false);
};

defineExpose({ confirm });
</script>

<template>
  <Transition name="fade">
    <div
      v-if="isVisible"
      class="fixed inset-0 z-[100] flex items-center justify-center p-4"
    >
      <!-- Backdrop -->
      <div
        class="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
        @click="handleCancel"
      ></div>

      <!-- Modal Content -->
      <div
        class="bg-white dark:bg-slate-800 rounded-3xl w-full max-w-sm p-6 relative z-10 shadow-2xl transform transition-all"
      >
        <div class="mb-4">
          <h3 class="font-bold text-lg text-slate-800 dark:text-slate-100 mb-2">
            {{ options.title }}
          </h3>
          <p
            class="text-sm text-slate-500 dark:text-slate-400 whitespace-pre-line leading-relaxed"
          >
            {{ options.message || options.content }}
          </p>
        </div>

        <div class="flex gap-3">
          <button
            @click="handleCancel"
            class="flex-1 py-2.5 rounded-xl bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 font-bold text-sm hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
          >
            {{ options.cancelText }}
          </button>
          <button
            @click="handleConfirm"
            class="flex-1 py-2.5 rounded-xl font-bold text-sm text-white shadow-lg transition-all transform active:scale-95"
            :class="{
              'bg-indigo-500 shadow-indigo-200 hover:bg-indigo-600':
                options.type === 'info',
              'bg-red-500 shadow-red-200 hover:bg-red-600': options.type === 'danger',
            }"
          >
            {{ options.confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.fade-enter-from .transform,
.fade-leave-to .transform {
  transform: scale(0.95);
  opacity: 0;
}
</style>
