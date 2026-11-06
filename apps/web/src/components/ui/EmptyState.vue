<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' }
});
</script>

<template>
  <div class="ui-empty-state">
    <span class="ui-empty-state-mark" aria-hidden="true">—</span>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>
