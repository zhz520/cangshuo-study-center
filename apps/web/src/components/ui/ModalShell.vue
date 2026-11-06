<script setup>
defineProps({
  open: Boolean,
  title: { type: String, required: true },
  size: { type: String, default: 'md' }
});
defineEmits(['close']);
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="admin-modal-backdrop" @click.self="$emit('close')">
      <div class="admin-modal" :class="{ 'is-wide': size === 'wide' }" role="dialog" aria-modal="true" :aria-label="title">
        <div class="admin-modal-head">
          <h2>{{ title }}</h2>
          <button class="admin-modal-close" type="button" aria-label="关闭" @click="$emit('close')">×</button>
        </div>
        <div class="admin-modal-body">
          <slot />
        </div>
        <div v-if="$slots.footer" class="admin-modal-foot">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
