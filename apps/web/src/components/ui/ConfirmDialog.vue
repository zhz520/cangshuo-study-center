<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  show: { type: Boolean, default: undefined },
  title: { type: String, default: '' },
  content: { type: String, default: '' },
  type: { type: String, default: '' },
  confirmText: { type: String, default: '' },
  cancelText: { type: String, default: '' }
});
const emit = defineEmits(['confirm', 'cancel', 'update:show']);

const isVisible = ref(false);
const options = ref({
  title: '提示',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  type: 'info'
});

let resolvePromise = null;

const confirm = (opts) => {
  options.value = { ...options.value, ...opts };
  isVisible.value = true;
  return new Promise((resolve) => {
    resolvePromise = resolve;
  });
};

const visible = computed(() => props.show !== undefined ? props.show : isVisible.value);
const view = computed(() => ({
  title: props.title || options.value.title,
  content: props.content || options.value.message || options.value.content,
  type: props.type || options.value.type,
  confirmText: props.confirmText || options.value.confirmText,
  cancelText: props.cancelText || options.value.cancelText
}));

const finish = (value) => {
  if (props.show !== undefined) {
    emit('update:show', false);
    emit(value ? 'confirm' : 'cancel');
    return;
  }
  isVisible.value = false;
  if (resolvePromise) resolvePromise(value);
  resolvePromise = null;
};

defineExpose({ confirm });
</script>

<template>
  <Transition name="confirm-fade">
    <div v-if="visible" class="confirm-backdrop" @click.self="finish(false)">
      <section class="confirm-modal" role="dialog" aria-modal="true" :aria-label="view.title">
        <div class="confirm-copy">
          <h2>{{ view.title }}</h2>
          <p>{{ view.content }}</p>
        </div>
        <div class="confirm-actions">
          <button class="ui-btn ui-btn-outline" type="button" @click="finish(false)">{{ view.cancelText }}</button>
          <button class="ui-btn" :class="view.type === 'danger' ? 'confirm-danger' : 'ui-btn-primary'" type="button" @click="finish(true)">{{ view.confirmText }}</button>
        </div>
      </section>
    </div>
  </Transition>
</template>

<style scoped>
.confirm-backdrop { position: fixed; inset: 0; z-index: 100; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(15,23,42,.38); backdrop-filter: blur(3px); }
.confirm-modal { width: min(400px, 100%); padding: 20px; border: 1px solid var(--border); border-radius: 14px; background: var(--bg-card); box-shadow: 0 24px 60px rgba(15,23,42,.18); }
.confirm-copy h2 { margin: 0; color: var(--text-main); font-size: 15px; font-weight: 650; letter-spacing: -.01em; }
.confirm-copy p { margin-top: 8px; color: var(--text-sub); font-size: 12px; line-height: 1.7; white-space: pre-line; }
.confirm-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 22px; }
.confirm-actions .ui-btn { min-width: 76px; }
.confirm-danger { background: var(--danger); color: #fff; }
.confirm-danger:hover { background: color-mix(in srgb, var(--danger) 88%, #000); }
.confirm-fade-enter-active, .confirm-fade-leave-active { transition: opacity .14s ease; }
.confirm-fade-enter-from, .confirm-fade-leave-to { opacity: 0; }
</style>
