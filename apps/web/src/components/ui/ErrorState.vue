<script setup>
defineProps({
  title: { type: String, default: '加载失败' },
  description: { type: String, default: '' }
});
</script>

<template>
  <div class="ui-error-state" role="alert">
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>
