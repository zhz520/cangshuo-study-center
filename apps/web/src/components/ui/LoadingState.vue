<script setup>
defineProps({
  label: { type: String, default: '正在加载' },
  description: { type: String, default: '' }
});
</script>

<template>
  <div class="ui-loading-state" role="status" aria-live="polite">
    <span class="ui-spinner" aria-hidden="true"></span>
    <strong>{{ label }}</strong>
    <p v-if="description">{{ description }}</p>
  </div>
</template>
