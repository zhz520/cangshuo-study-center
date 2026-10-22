<script setup>
import { useRoute } from 'vue-router';
const route = useRoute();
</script>

<template>
  <div class="ui-page not-found">
    <p class="ui-eyebrow">404</p>
    <h1 class="ui-title">页面不存在</h1>
    <p class="ui-subtitle">地址 <code>{{ route.fullPath }}</code> 没有对应页面，可能已移动或链接有误。</p>
    <div class="ui-row mt-5"><RouterLink to="/home" class="ui-btn ui-btn-primary">返回首页</RouterLink><RouterLink to="/" class="ui-btn ui-btn-outline">回到产品首页</RouterLink></div>
  </div>
</template>

<style scoped>
.not-found { max-width: 620px; padding-top: 12vh; }
.not-found code { font-size: 12px; color: var(--text-sub); word-break: break-all; }
</style>
