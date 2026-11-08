<script setup>
import { onMounted, onUnmounted, ref } from 'vue';
import Toast from './Toast.vue';
import ConfirmDialog from './ConfirmDialog.vue';

const toast = ref(null);
const confirmDialog = ref(null);

const onToast = (event) => {
  toast.value?.add(event.detail.message, event.detail.type, event.detail.duration);
};

const onConfirm = async (event) => {
  const value = confirmDialog.value
    ? await confirmDialog.value.confirm(event.detail.options || {})
    : false;
  window.dispatchEvent(new CustomEvent('study-ui-confirm-result', {
    detail: { id: event.detail.id, value }
  }));
};

onMounted(() => {
  window.addEventListener('study-ui-toast', onToast);
  window.addEventListener('study-ui-confirm', onConfirm);
});

onUnmounted(() => {
  window.removeEventListener('study-ui-toast', onToast);
  window.removeEventListener('study-ui-confirm', onConfirm);
});
</script>

<template>
  <Toast ref="toast" />
  <ConfirmDialog ref="confirmDialog" />
</template>
