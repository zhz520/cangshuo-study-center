<script setup>
defineProps({
  eyebrow: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  size: { type: String, default: 'page' }
});
</script>

<template>
  <header class="page-header" :class="`is-${size}`">
    <div class="page-header-copy">
      <p v-if="eyebrow" class="page-header-eyebrow">{{ eyebrow }}</p>
      <h1 class="page-header-title">{{ title }}</h1>
      <p v-if="description" class="page-header-description">{{ description }}</p>
      <slot name="meta" />
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>
