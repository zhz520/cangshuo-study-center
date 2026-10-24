<script setup>
defineProps({ title: String, description: String });
</script>
<template>
  <div class="admin-page-heading">
    <p class="ui-eyebrow">烁学 · 管理工作台</p>
    <h1>{{ title }}</h1>
    <p v-if="description" class="ui-subtitle">{{ description }}</p>
  </div>
</template>
<style scoped>
.admin-page-heading h1 { margin-top:6px; font-size:24px; line-height:1.3; font-weight:650; letter-spacing:-.025em; color:var(--text-main); }
@media(max-width:600px) { .admin-page-heading h1 { font-size:22px; } }
</style>
