<script setup>
import { ref } from 'vue';

const toasts = ref([]);
let idCounter = 0;

const add = (message, type = 'info', duration = 3000) => {
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
  <div class="toast-stack" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="toast in toasts" :key="toast.id" class="toast-item" :class="'is-' + toast.type" role="status">
        <span class="toast-message">{{ toast.message }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-stack { position: fixed; top: 16px; left: 50%; z-index: 100; display: flex; flex-direction: column; gap: 8px; width: min(380px, calc(100vw - 32px)); transform: translateX(-50%); pointer-events: none; }
.toast-item { pointer-events: auto; display: flex; align-items: flex-start; padding: 11px 13px; border: 1px solid var(--border); border-left: 3px solid var(--border-strong); border-radius: 9px; background: var(--bg-card); color: var(--text-main); box-shadow: var(--shadow); }
.toast-message { font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.toast-item.is-success { border-left-color: var(--success); }
.toast-item.is-error { border-left-color: var(--danger); }
.toast-item.is-warning { border-left-color: var(--warning); }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(-8px); }
.toast-enter-active, .toast-leave-active { transition: opacity .16s ease, transform .16s ease; }
</style>
