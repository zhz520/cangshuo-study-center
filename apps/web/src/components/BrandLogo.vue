<script setup>
import appIcon from '../assets/app_icon.png';
</script>

<template>
  <img :src="appIcon" class="brand-logo" alt="" aria-hidden="true" width="32" height="32" />
</template>

<style scoped>
.brand-logo {
  display: block;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  object-fit: contain;
  border-radius: 8px;
  background: #fff;
}
</style>
