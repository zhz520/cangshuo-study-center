<script setup>
defineProps({
  label: { type: String, default: '' }
});
</script>

<template>
  <div class="page-toolbar">
    <div class="page-toolbar-main">
      <span v-if="label" class="page-toolbar-label">{{ label }}</span>
      <slot />
    </div>
    <div v-if="$slots.end" class="page-toolbar-end">
      <slot name="end" />
    </div>
  </div>
</template>
