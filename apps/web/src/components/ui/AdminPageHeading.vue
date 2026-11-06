<script setup>
import PageHeader from './PageHeader.vue';

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' }
});
</script>

<template>
  <PageHeader eyebrow="烁学 · 管理工作台" :title="title" :description="description" />
</template>
